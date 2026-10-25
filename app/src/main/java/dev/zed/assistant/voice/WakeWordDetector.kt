package dev.zed.assistant.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.zed.assistant.core.ZedState
import kotlin.math.min

/**
 * Keeps a recognition window open so "zed" is heard at any time.
 *
 * The platform recogniser is a one-shot affair: it stops capturing after every utterance,
 * every silence and every error, so an always-on wake word has to be restarted endlessly.
 * Naively restarting it from several callbacks at once is what made the microphone flicker
 * on and off: overlapping timers started the recogniser while it was still busy, every
 * start produced a new error, and each error scheduled another start.
 *
 * This class is built so that exactly one "arm" can ever be in flight:
 *  - one recogniser instance, created lazily and reused;
 *  - one pending restart runnable, always cancelled before it is re-scheduled;
 *  - an explicit [listening] flag plus a watchdog, so a silent recogniser cannot wedge;
 *  - backoff on repeated failures, with a clean stop instead of an endless storm;
 *  - never captures while Zed is talking or while another session owns the microphone.
 */
class WakeWordDetector(
    private val context: Context,
    private val languageProvider: () -> String,
    private val phraseProvider: () -> String,
    /** True while another part of Zed owns the microphone or the speaker. */
    private val isBusy: () -> Boolean = { false },
    private val onWake: () -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /** Armed: the user wants the wake word. */
    @Volatile
    private var running = false

    /** Temporarily muted (Zed speaking, a live session, enrolment...). */
    @Volatile
    private var paused = false

    /** A recognition window is open right now. */
    @Volatile
    private var listening = false

    /** A wake has fired and is being handed to the service. */
    @Volatile
    private var firing = false

    private var consecutiveErrors = 0
    private var cooldownUntil = 0L
    private var startedAt = 0L

    val isRunning: Boolean get() = running

    private val restartRunnable = Runnable { arm() }

    private val watchdog = Runnable {
        if (!listening) return@Runnable
        ZedState.log("WAKE", "Recognition window expired; restarting")
        listening = false
        runCatching { recognizer?.cancel() }
        schedule(MIN_RESTART_MS)
    }

    fun start() {
        if (running) {
            resume()
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ZedState.log("WAKE", "Microphone permission missing")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            ZedState.log("WAKE", "Speech recognizer unavailable; wake word disabled")
            return
        }
        running = true
        paused = false
        firing = false
        consecutiveErrors = 0
        cooldownUntil = 0L
        ZedState.wakeActive.value = true
        ZedState.setStatus("Listening for wake word")
        ZedState.log("WAKE", "Wake word armed: \"${phraseProvider()}\"")
        schedule(WARM_UP_MS)
    }

    /** Stops capturing but stays armed. */
    fun pause() {
        paused = true
        firing = false
        ZedState.wakeActive.value = false
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(watchdog)
        stopWindow()
    }

    /** Re-arms after [pause]. Safe to call repeatedly. */
    fun resume() {
        if (!running) return
        paused = false
        firing = false
        consecutiveErrors = 0
        ZedState.wakeActive.value = true
        // A previous session may still be releasing the microphone; give it a beat.
        schedule(if (isBusy()) RESUME_DELAY_MS else RESUME_DELAY_MS / 2)
    }

    fun stop() {
        running = false
        paused = true
        firing = false
        ZedState.wakeActive.value = false
        handler.removeCallbacksAndMessages(null)
        stopWindow()
        handler.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
        ZedState.log("WAKE", "Wake word disarmed")
    }

    // ------------------------------------------------------------- internals

    private fun schedule(delayMs: Long) {
        // One pending arm, ever. Without this the restart timers stack up.
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(watchdog)
        if (!running || paused) return
        handler.postDelayed(restartRunnable, delayMs.coerceAtLeast(0L))
    }

    private fun arm() {
        if (!running || paused) return
        if (isBusy()) {
            // Zed is speaking or another session has the mic: wait, don't fight it.
            schedule(BUSY_POLL_MS)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < cooldownUntil) {
            schedule(cooldownUntil - now)
            return
        }
        if (listening) return

        val engine = ensureRecognizer()
        if (engine == null) {
            ZedState.log("WAKE", "No speech recognizer available")
            return
        }
        try {
            startedAt = now
            listening = true
            engine.startListening(recognitionIntent())
            handler.removeCallbacks(watchdog)
            handler.postDelayed(watchdog, LISTEN_WINDOW_MS)
        } catch (t: Throwable) {
            listening = false
            consecutiveErrors = min(consecutiveErrors + 1, 5)
            ZedState.log("WAKE", "Could not start listening: ${t.message}")
            schedule(backoff())
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        return try {
            SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
        } catch (t: Throwable) {
            ZedState.log("WAKE", "Recognizer creation failed: ${t.message}")
            null
        }
    }

    private fun stopWindow() {
        listening = false
        recognizer?.let { runCatching { it.cancel() } }
    }

    private fun restartAfter(delayMs: Long) {
        listening = false
        handler.removeCallbacks(watchdog)
        schedule(delayMs)
    }

    private fun backoff(): Long = min(MIN_RESTART_MS * (1L shl consecutiveErrors), MAX_RESTART_MS)

    private fun recognitionIntent(): Intent {
        val language = languageProvider()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Short windows: silence ends the utterance quickly so the loop stays cheap.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
    }

    private fun check(bundle: Bundle?) {
        if (!running || paused || firing) return
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?: return
        val phrase = phraseProvider()
        for (candidate in results) {
            if (!WakePhraseMatcher.matches(candidate, phrase)) continue
            // Ignore the tail of a sentence Zed itself just spoke.
            if (SystemClock.elapsedRealtime() - startedAt < ECHO_GUARD_MS) continue
            firing = true
            consecutiveErrors = 0
            cooldownUntil = SystemClock.elapsedRealtime() + WAKE_COOLDOWN_MS
            listening = false
            handler.removeCallbacks(watchdog)
            ZedState.log("WAKE", "Detected: \"$candidate\"")
            ZedState.wakeActive.value = false
            handler.post { runCatching { recognizer?.cancel() } }
            handler.post { onWake() }
            return
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (!running || paused) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    ZedState.log("WAKE", "Microphone permission denied")
                    running = false
                    ZedState.wakeActive.value = false
                    stopWindow()
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    consecutiveErrors = 0
                    restartAfter(QUIET_RESTART_MS)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Another client owns the recogniser; back off instead of piling on.
                    consecutiveErrors = min(consecutiveErrors + 1, 3)
                    restartAfter(BUSY_RESTART_MS)
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    consecutiveErrors = min(consecutiveErrors + 1, 5)
                    recycleRecognizer()
                    restartAfter(backoff())
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_AUDIO -> {
                    consecutiveErrors = min(consecutiveErrors + 1, 5)
                    restartAfter(backoff())
                }
                else -> {
                    consecutiveErrors = min(consecutiveErrors + 1, 5)
                    restartAfter(backoff())
                }
            }
            if (consecutiveErrors >= GIVE_UP_AFTER) {
                ZedState.log("WAKE", "Speech service keeps failing; pausing the wake word")
                paused = true
                ZedState.wakeActive.value = false
                handler.removeCallbacks(restartRunnable)
                ZedState.setStatus("Wake word paused — tap the mic to talk")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            check(partialResults)
        }

        override fun onResults(results: Bundle?) {
            if (!running || paused) return
            check(results)
            if (firing) return
            consecutiveErrors = 0
            restartAfter(QUIET_RESTART_MS)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun recycleRecognizer() {
        val old = recognizer
        recognizer = null
        handler.post {
            runCatching { old?.cancel() }
            runCatching { old?.destroy() }
        }
    }

    companion object {
        private const val WARM_UP_MS = 400L
        private const val RESUME_DELAY_MS = 600L
        private const val BUSY_POLL_MS = 500L
        /** Never restart faster than this after a normal, silent window. */
        private const val QUIET_RESTART_MS = 900L
        private const val MIN_RESTART_MS = 1200L
        private const val MAX_RESTART_MS = 20_000L
        private const val BUSY_RESTART_MS = 2000L
        /** A stuck recogniser is cancelled and retried after this long. */
        private const val LISTEN_WINDOW_MS = 15_000L
        private const val WAKE_COOLDOWN_MS = 2500L
        private const val ECHO_GUARD_MS = 550L
        private const val GIVE_UP_AFTER = 6
    }
}
