package dev.zed.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.zed.assistant.core.ZedState

/**
 * One command, one listening window. Falls back to on-device recognition when the
 * Google app is set up for offline speech.
 */
class OneShotRecognizer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var active = false

    @Volatile
    private var delivered = false

    fun listen(
        language: String,
        onPartial: (String) -> Unit,
        onFinal: (String?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device.")
            return
        }
        cancel()
        delivered = false
        active = true
        val engine = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                ZedState.listening.value = true
                ZedState.setStatus("Listening")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                ZedState.setStatus("Thinking")
            }

            override fun onError(error: Int) {
                if (delivered) return
                delivered = true
                active = false
                ZedState.listening.value = false
                onFinal(null)
                onError(describeError(error))
                cleanup()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                if (delivered) return
                delivered = true
                active = false
                ZedState.listening.value = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                onFinal(text)
                cleanup()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            engine.startListening(intent(language))
            handler.postDelayed({
                if (active && !delivered) {
                    engine.stopListening()
                }
            }, MAX_LISTEN_MS)
        } catch (t: Throwable) {
            onError(t.message ?: "Could not start listening")
        }
    }

    private fun intent(language: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    fun cancel() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
        ZedState.listening.value = false
    }

    private fun cleanup() {
        handler.postDelayed({
            recognizer?.let { runCatching { it.destroy() } }
            recognizer = null
        }, 200)
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I heard nothing."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network right now."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        else -> "Speech recognition failed ($error)."
    }

    companion object {
        private const val MAX_LISTEN_MS = 12_000L
    }
}
