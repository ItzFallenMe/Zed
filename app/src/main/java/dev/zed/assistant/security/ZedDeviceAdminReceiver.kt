package dev.zed.assistant.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import dev.zed.assistant.core.ZedState

class ZedDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        ZedState.log("ADMIN", "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        ZedState.log("ADMIN", "Device admin disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Zed uses device admin to lock the screen during an intruder alert. Disable anyway?"
}
