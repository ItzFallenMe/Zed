package dev.zed.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState

/**
 * Brings Zed back after a restart. The wake word only works while the core service lives,
 * so a reboot should not silently disable the assistant.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val prefs = ZedPrefs(context)
        if (!prefs.onboardingDone || !prefs.wakeEnabled) return
        runCatching { ZedCoreService.start(context) }
            .onFailure { ZedState.log("SVC", "Boot start refused: ${it.message}") }
    }
}
