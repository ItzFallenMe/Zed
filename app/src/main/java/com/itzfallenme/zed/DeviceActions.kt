package com.itzfallenme.zed

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Platform actions are restricted to a fixed allowlist and safe handoffs. */
class DeviceActions(private val context: Context) {
    private val packages = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music"
    )

    fun openApp(name: String): Result<Unit> {
        val packageName = packages[name.trim().lowercase()]
            ?: return Result.failure(IllegalArgumentException("App is not on Zed's allowlist."))
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return Result.failure(IllegalStateException("$name is not installed or launchable."))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
    }

    fun openUrl(raw: String): Result<Unit> {
        val uri = Uri.parse(raw.trim())
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Only valid HTTP/HTTPS URLs are allowed."))
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
    }

    fun openDialer(rawNumber: String): Result<Unit> {
        val number = rawNumber.filter { it.isDigit() || it == '+' }
        if (number.length !in 7..16 || number.drop(1).contains('+')) {
            return Result.failure(IllegalArgumentException("Invalid phone number."))
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
    }
}
