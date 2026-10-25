package dev.zed.assistant.core

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.zed.assistant.access.ZedAccessibilityService
import dev.zed.assistant.security.ZedDeviceAdminReceiver

/**
 * Central place that knows which powerful Android permission gates each capability,
 * plus how to send the user to the right settings screen.
 */
object ZedPermissions {

    enum class Capability(val title: String, val why: String) {
        MICROPHONE(
            "Microphone",
            "Wake word and voice commands. Audio is processed on device."
        ),
        NOTIFICATIONS(
            "Notifications",
            "Keeps the assistant alive and lets Zed read unread messages aloud."
        ),
        CALLS(
            "Phone calls",
            "Start calls you ask for, including WhatsApp call hand-off."
        ),
        CONTACTS(
            "Contacts",
            "\"Call mom\" needs to know which contact is mom. Looked up locally."
        ),
        CAMERA(
            "Camera",
            "Intruder snapshots for the anti-theft siren guard."
        ),
        WRITE_SETTINGS(
            "Change system settings",
            "Brightness control without leaving the conversation."
        ),
        ACCESSIBILITY(
            "Device control (accessibility)",
            "Taps, swipes, scrolling, typing and reading the screen for you."
        ),
        NOTIFICATION_ACCESS(
            "Notification access",
            "Read unread WhatsApp and chat notifications aloud."
        ),
        DEVICE_ADMIN(
            "Device admin",
            "Lock the screen and survive a thief swiping Zed away."
        )
    }

    fun hasMic(context: Context): Boolean = granted(context, Manifest.permission.RECORD_AUDIO)

    fun hasCamera(context: Context): Boolean = granted(context, Manifest.permission.CAMERA)

    fun hasCall(context: Context): Boolean = granted(context, Manifest.permission.CALL_PHONE)

    fun hasContacts(context: Context): Boolean = granted(context, Manifest.permission.READ_CONTACTS)

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun canWriteSettings(context: Context): Boolean =
        Settings.System.canWrite(context)

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(context, ZedDeviceAdminReceiver::class.java))
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (ZedAccessibilityService.instance != null) return true
        val expected = ComponentName(context, ZedAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { entry ->
            val normalized = entry.lowercase()
            normalized.contains(expected.flattenToShortString().lowercase()) ||
                (normalized.contains(context.packageName.lowercase()) &&
                    normalized.contains(ZedAccessibilityService::class.java.simpleName.lowercase()))
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isGranted(context: Context, capability: Capability): Boolean = when (capability) {
        Capability.MICROPHONE -> hasMic(context)
        Capability.NOTIFICATIONS -> hasNotificationPermission(context)
        Capability.CALLS -> hasCall(context)
        Capability.CONTACTS -> hasContacts(context)
        Capability.CAMERA -> hasCamera(context)
        Capability.WRITE_SETTINGS -> canWriteSettings(context)
        Capability.ACCESSIBILITY -> isAccessibilityEnabled(context)
        Capability.NOTIFICATION_ACCESS -> isNotificationListenerEnabled(context)
        Capability.DEVICE_ADMIN -> isDeviceAdminActive(context)
    }

    /** Capabilities that must be granted from a settings screen instead of a runtime dialog. */
    fun isSettingsGated(capability: Capability): Boolean = capability in setOf(
        Capability.WRITE_SETTINGS,
        Capability.ACCESSIBILITY,
        Capability.NOTIFICATION_ACCESS,
        Capability.DEVICE_ADMIN
    )

    fun openSettingsScreen(context: Context, capability: Capability) {
        val intent = when (capability) {
            Capability.WRITE_SETTINGS -> Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            Capability.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Capability.NOTIFICATION_ACCESS ->
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            Capability.DEVICE_ADMIN -> null
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
        if (intent != null) {
            launchSafely(context, intent)
        }
    }

    fun launchSafely(context: Context, intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            ZedState.log("INTENT", "Could not open ${intent.action}: ${t.message}")
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
