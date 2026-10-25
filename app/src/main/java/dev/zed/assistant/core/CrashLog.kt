package dev.zed.assistant.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last fatal exception on disk.
 *
 * A crash on a phone is invisible from here: the user sees "Zed keeps stopping" and the
 * stack trace dies with the process. This handler writes it to a private file before the
 * default handler takes over, and [ZedCoreService][dev.zed.assistant.service.ZedCoreService]
 * prints it into Diagnostics on the next start so it can be read without ADB.
 *
 * The exception is always re-thrown to the platform's handler — a crash is never swallowed.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_CHARS = 6000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Recorder) return
        Thread.setDefaultUncaughtExceptionHandler(Recorder(appContext, previous))
    }

    /** One-line-ish summary of the last crash, or null when the last run was clean. */
    fun last(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = buildString {
            appendLine("$stamp · thread ${thread.name}")
            appendLine("${error.javaClass.name}: ${error.message}")
            appendLine(error.stackTraceToString().take(MAX_CHARS))
        }
        runCatching { File(context.filesDir, FILE_NAME).writeText(body) }
    }

    private class Recorder(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching { write(context, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                killProcess(thread, error)
            }
        }

        private fun killProcess(thread: Thread, error: Throwable) {
            // No handler above us: match the platform's own behaviour.
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
            throw error
        }
    }
}
