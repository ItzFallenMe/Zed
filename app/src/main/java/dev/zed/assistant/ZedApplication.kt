package dev.zed.assistant

import android.app.Application
import dev.zed.assistant.core.CrashLog

/**
 * Installs the crash recorder before anything else runs, so a failure in the service or in
 * the first composition is written down instead of vanishing with the process.
 */
class ZedApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
