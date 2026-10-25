package dev.zed.assistant.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.zed.assistant.core.ZedState
import kotlin.math.min

/**
 * Keeps a low-cost, offline speech recognition loop running so "Bolo Zed" is heard
 * at any time. Recognition is on-device (`EXTRA_PREFER_OFFLINE`) so no audio leaves
 * the phone for the wake word.
 */
class WakeWordDetector(
    private val context: Context,
    private val languageProvider: () -> String,
    private val phraseProvider: () -> String,
    private val onWake: () -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var firing = false

    private var consecutiveErrors = 0

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
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
        ZedState.wakeActive.value = true
        ZedState.setStatus("Listening for wake word")
        handler.post { ensureRecognizer() }
        ZedState.log("WAKE", "Wake word armed: \"${phraseProvider()}\"")
    }

    fun pause() {
        paused = true
        handler.post { stopListening() }
    }

    fun resume() {
        paused = false
        firing = false
        if (!running) return
        ZedState.wakeActive.value = true
        handler.postDelayed({ startListening() }, 350)
    }

    fun stop() {
        running = false
        paused = true
        firing = false
        ZedState.wakeActive.value = false
        handler.removeCallbacksAndMessages(null)
        handler.post {
            stopListening()
            recognizer?.let { runCatching { it.destroy() } }
            recognizer = null
        }
        ZedState.log("WAKE", "Wake word disarmed")
    }

    private fun ensureRecognizer(): SpeechRecognizer {
        recognizer?.let { return it }
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        created.setRecognitionListener(listener)
        recognizer = created
        startListening()
        return created
    }

    private fun startListening() {
        if (!running || paused) return
        val engine = ensureRecognizerIfNeeded() ?: return
        try {
            engine.startListening(recognitionIntent())
        } catch (t: Throwable) {
            ZedState.log("WAKE", "Restart failed: ${t.message}")
            scheduleRestart(1500)
        }
    }

    private fun ensureRecognizerIfNeeded(): SpeechRecognizer? {
        recognizer?.let { return it }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        created.setRecognitionListener(listener)
        recognizer = created
        return created
    }

    private fun stopListening() {
        recognizer?.let { runCatching { it.cancel() } }
    }

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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
    }

    private fun check(bundle: Bundle?) {
        if (!running || paused || firing) return
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?: return
        val phrase = phraseProvider()
        for (candidate in results) {
            if (WakePhraseMatcher.matches(candidate, phrase)) {
                firing = true
                consecutiveErrors = 0
                ZedState.log("WAKE", "Detected: \"$candidate\"")
                ZedState.wakeActive.value = false
                handler.post { stopListening() }
                handler.post { onWake() }
                return
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!running || paused) return
        handler.postDelayed({ startListening() }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    ZedState.log("WAKE", "Microphone permission denied")
                    running = false
                    ZedState.wakeActive.value = false
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    consecutiveErrors = 0
                    scheduleRestart(250)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(1200)
                SpeechRecognizer.ERROR_CLIENT -> scheduleRestart(400)
                else -> {
                    consecutiveErrors = min(consecutiveErrors + 1, 5)
                    scheduleRestart(600L * consecutiveErrors)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            check(partialResults)
        }

        override fun onResults(results: Bundle?) {
            check(results)
            if (!firing) {
                consecutiveErrors = 0
                scheduleRestart(150)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
