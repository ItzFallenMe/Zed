package com.zed.assistant.ai

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes only the allowlisted device actions that Gemini may request.
 * Every method returns a truthful JSON result; nothing is reported as
 * successful unless the corresponding Android action was actually started.
 */
class DeviceActions(private val context: Context, private val contacts: ContactsRepository) {

  companion object {
    private const val TAG = "ZedActions"

    private val APP_PACKAGES = linkedMapOf(
      "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
      "youtube" to listOf("com.google.android.youtube"),
      "instagram" to listOf("com.instagram.android"),
      "facebook" to listOf("com.facebook.katana"),
      "chrome" to listOf("com.android.chrome"),
      "gmail" to listOf("com.google.android.gm"),
      "maps" to listOf("com.google.android.apps.maps"),
      "spotify" to listOf("com.spotify.music"),
      "netflix" to listOf("com.netflix.mediaclient"),
      "telegram" to listOf("org.telegram.messenger")
    )

    private val APP_ALIASES = mapOf(
      "google maps" to "maps",
      "google chrome" to "chrome",
      "google mail" to "gmail",
      "you tube" to "youtube",
      "whats app" to "whatsapp",
      "insta" to "instagram",
      "telegram messenger" to "telegram"
    )

    /** Canonical names used in the model's openApp enum. */
    val supportedAppNames: List<String>
      get() = APP_PACKAGES.keys.toList()
  }

  suspend fun openWhatsApp(): JSONObject {
    val resolved = APP_PACKAGES.getValue("whatsapp")
      .firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
      ?: return failure("openWhatsApp", "whatsapp_not_installed")

    val launchIntent = context.packageManager.getLaunchIntentForPackage(resolved)
      ?: return failure("openWhatsApp", "whatsapp_not_installed")
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return if (launch(launchIntent)) {
      Log.d(TAG, "WhatsApp launch intent sent ($resolved)")
      success("openWhatsApp") { put("app", "whatsapp") }
    } else {
      failure("openWhatsApp", "whatsapp_launch_failed")
    }
  }

  suspend fun openApp(rawAppName: String): JSONObject {
    val requested = rawAppName.trim()
    if (requested.isBlank()) return failure("openApp", "missing_app_name")

    val key = normalizeApp(requested)
    val packages = APP_PACKAGES[key]
      ?: return failure("openApp", "unsupported_app") {
        put("app", requested)
        put("supportedApps", JSONArray(APP_PACKAGES.keys.toList()))
      }

    val resolved = packages.firstOrNull {
      context.packageManager.getLaunchIntentForPackage(it) != null
    } ?: return failure("openApp", "app_not_installed") { put("app", key) }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(resolved)
      ?: return failure("openApp", "app_not_installed") { put("app", key) }
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return if (launch(launchIntent)) {
      Log.d(TAG, "Launched $key ($resolved)")
      success("openApp") { put("app", key) }
    } else {
      failure("openApp", "app_launch_failed") { put("app", key) }
    }
  }

  suspend fun openUrl(rawUrl: String): JSONObject {
    val input = rawUrl.trim()
    if (input.isBlank()) return failure("openUrl", "invalid_url")

    val candidate = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(input)) {
      input
    } else {
      "https://$input"
    }

    val uri = try {
      candidate.toUri()
    } catch (error: Exception) {
      Log.e(TAG, "Invalid URL: $input", error)
      return failure("openUrl", "invalid_url")
    }

    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") {
      return failure("openUrl", "unsupported_url_scheme")
    }
    if (uri.host.isNullOrBlank() || input.length > 2_048) {
      return failure("openUrl", "invalid_url")
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (launch(intent)) {
      Log.d(TAG, "Opened URL: $uri")
      success("openUrl") { put("url", uri.toString()) }
    } else {
      failure("openUrl", "no_app_can_open_url") { put("url", uri.toString()) }
    }
  }

  suspend fun makeCall(rawNumber: String): JSONObject {
    val trimmed = rawNumber.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isLetter() }) {
      return failure("makeCall", "invalid_phone_number")
    }
    val digits = trimmed.filter { it.isDigit() }
    if (digits.length !in 3..15) {
      return failure("makeCall", "invalid_phone_number")
    }

    val tel = "tel:" + (if (trimmed.startsWith("+")) "+" else "") + digits
    val canCallDirectly = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED ||
      PermissionGateway.ensure(context, Manifest.permission.CALL_PHONE)

    if (canCallDirectly) {
      val callIntent = Intent(Intent.ACTION_CALL, tel.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
        withContext(Dispatchers.Main) { context.startActivity(callIntent) }
        Log.d(TAG, "Started phone call to $tel")
        return success("makeCall") {
          put("phoneNumber", tel.removePrefix("tel:"))
          put("mode", "call")
        }
      } catch (error: SecurityException) {
        Log.w(TAG, "ACTION_CALL denied, falling back to dialer", error)
      } catch (error: Exception) {
        Log.e(TAG, "ACTION_CALL failed, falling back to dialer", error)
      }
    }

    val dialIntent = Intent(Intent.ACTION_DIAL, tel.toUri())
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (launch(dialIntent)) {
      Log.d(TAG, "Opened dialer with $tel")
      success("makeCall") {
        put("phoneNumber", tel.removePrefix("tel:"))
        put("mode", "dialer")
      }
    } else {
      failure("makeCall", "no_dialer_available")
    }
  }

  suspend fun callContact(rawName: String): JSONObject {
    val name = rawName.trim()
    if (name.length < 2) return failure("callContact", "invalid_contact_name")

    val granted = PermissionGateway.ensure(context, Manifest.permission.READ_CONTACTS)
    if (!granted) {
      Log.w(TAG, "Contacts permission was not granted")
      return failure("callContact", "contacts_permission_denied")
    }

    val matches = try {
      withContext(Dispatchers.IO) { contacts.searchByName(name) }
    } catch (error: SecurityException) {
      return failure("callContact", "contacts_permission_denied")
    } catch (error: Exception) {
      Log.e(TAG, "Contact lookup failed", error)
      return failure("callContact", "contact_lookup_failed")
    }

    if (matches.isEmpty()) {
      return failure("callContact", "contact_not_found") { put("contactName", name) }
    }

    val distinctContacts = matches.map { it.contactId }.distinct()
    if (distinctContacts.size > 1 || matches.size > 1) {
      val candidates = JSONArray()
      matches.take(6).forEach { match ->
        candidates.put(
          JSONObject()
            .put("name", match.name)
            .put("number", match.number)
            .put("label", match.label.orEmpty())
        )
      }
      Log.d(TAG, "Ambiguous contact '$name': ${matches.size} matches")
      return failure("callContact", "multiple_matches") {
        put("contactName", name)
        put("candidates", candidates)
        put("instruction", "Ask the user which contact to call. Do not guess.")
      }
    }

    val match = matches.first()
    val result = makeCall(match.number)
    if (!result.optBoolean("success")) {
      return failure("callContact", result.optString("error", "call_failed")) {
        put("contactName", match.name)
      }
    }
    Log.d(TAG, "Calling contact ${match.name}")
    return success("callContact") {
      put("contactName", match.name)
      put("phoneNumber", match.number)
      put("mode", result.optString("mode", "dialer"))
    }
  }

  private fun normalizeApp(raw: String): String {
    val cleaned = raw.lowercase(Locale.ROOT)
      .replace(Regex("[^a-z0-9 ]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
    val withoutSuffix = cleaned.removeSuffix(" app").trim()
    return APP_ALIASES[withoutSuffix] ?: withoutSuffix
  }

  private suspend fun launch(intent: Intent): Boolean = withContext(Dispatchers.Main) {
    try {
      context.startActivity(intent)
      true
    } catch (error: ActivityNotFoundException) {
      Log.e(TAG, "No activity can handle ${intent.action}", error)
      false
    } catch (error: Exception) {
      Log.e(TAG, "Could not launch ${intent.action}", error)
      false
    }
  }

  private fun success(action: String, extra: JSONObject.() -> Unit = {}): JSONObject =
    JSONObject()
      .put("success", true)
      .put("action", action)
      .apply(extra)

  private fun failure(
    action: String,
    error: String,
    extra: JSONObject.() -> Unit = {}
  ): JSONObject =
    JSONObject()
      .put("success", false)
      .put("action", action)
      .put("error", error)
      .apply(extra)
}
