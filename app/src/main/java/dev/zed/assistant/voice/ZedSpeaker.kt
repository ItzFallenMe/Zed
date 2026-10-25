package dev.zed.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.zed.assistant.core.ZedState
import java.util.Locale

/**
 * On-device text to speech. Used for local (offline) replies and for announcing
 * actions; Gemini Live audio is played through [PcmPlayer] instead.
 */
class ZedSpeaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false

    private var pendingText: String? = null
    private var hindi: Boolean = false

    var onFinished: (() -> Unit)? = null

    fun init(language: String = "hi-IN", onReady: (() -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke()
            return
        }
        hindi = language.startsWith("hi")
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                applyLocale(hindi)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        ZedState.speaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        ZedState.speaking.value = false
                        handler.post { onFinished?.invoke() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        ZedState.speaking.value = false
                        handler.post { onFinished?.invoke() }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        ZedState.speaking.value = false
                        handler.post { onFinished?.invoke() }
                    }
                })
                pendingText?.let { text ->
                    pendingText = null
                    speak(text)
                }
                onReady?.invoke()
            } else {
                ZedState.log("TTS", "Text to speech unavailable")
            }
        }
    }

    private fun applyLocale(hindiVoice: Boolean) {
        val engine = tts ?: return
        val preferred = if (hindiVoice) {
            Locale("hi", "IN")
        } else {
            Locale.US
        }
        val available = engine.isLanguageAvailable(preferred)
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = preferred
        } else {
            engine.language = Locale.US
        }
        engine.setSpeechRate(1.02f)
        engine.setPitch(0.96f)
    }

    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !initialized) {
            pendingText = text
            init(if (hindi) "hi-IN" else "en-US")
            return
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        ZedState.speaking.value = true
        engine.speak(text, mode, null, "zed-${System.currentTimeMillis()}")
    }

    fun stop() {
        runCatching { tts?.stop() }
        ZedState.speaking.value = false
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        initialized = false
    }
}
