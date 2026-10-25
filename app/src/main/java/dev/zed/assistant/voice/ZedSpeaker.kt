package dev.zed.assistant.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.zed.assistant.core.ZedState
import java.util.Locale

/**
 * Best-available voice output.
 *
 * Zed prefers Google's speech services engine, then any installed quality engine, and
 * picks the highest-quality voice for the language — including the neural/network
 * voices when the phone is online. If no premium voice exists it falls back to the
 * best on-device voice, so speech still works completely offline and free.
 */
class ZedSpeaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false

    private var pendingText: String? = null
    private var targetLanguage = "hi-IN"
    private var preferBestVoice = true
    private var progressAttached = false

    var engineName: String = "system default"
        private set

    var voiceName: String = "default"
        private set

    var onFinished: (() -> Unit)? = null

    fun init(language: String, preferBest: Boolean = true, onReady: (() -> Unit)? = null) {
        targetLanguage = language
        preferBestVoice = preferBest
        val existing = tts
        if (existing != null && initialized) {
            applyVoice()
            onReady?.invoke()
            return
        }
        createEngine(preferredEnginePackage(), onReady, allowFallback = true)
    }

    private fun createEngine(
        enginePackage: String?,
        onReady: (() -> Unit)?,
        allowFallback: Boolean
    ) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                engineName = runCatching { tts?.defaultEngine }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: (enginePackage ?: "system default")
                applyVoice()
                attachProgress()
                ZedState.log("TTS", "Engine $engineName, voice $voiceName")
                pendingText?.let { text ->
                    pendingText = null
                    speak(text)
                }
                onReady?.invoke()
            } else if (allowFallback && enginePackage != null) {
                runCatching { tts?.shutdown() }
                tts = null
                createEngine(null, onReady, allowFallback = false)
            } else {
                ZedState.log("TTS", "Text to speech unavailable")
            }
        }
        tts = try {
            if (enginePackage != null) {
                TextToSpeech(context.applicationContext, listener, enginePackage)
            } else {
                TextToSpeech(context.applicationContext, listener)
            }
        } catch (t: Throwable) {
            ZedState.log("TTS", "Engine start failed: ${t.message}")
            null
        }
    }

    /** Google speech services first, then Samsung, then whatever the system offers. */
    private fun preferredEnginePackage(): String? {
        return try {
            val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            val packages = context.packageManager
                .queryIntentServices(intent, 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
            packages.firstOrNull { it == "com.google.android.tts" }
                ?: packages.firstOrNull { it.contains("google.android.tts") }
                ?: packages.firstOrNull { it.contains("samsung") }
                ?: packages.firstOrNull { it != context.packageName }
        } catch (t: Throwable) {
            null
        }
    }

    private fun applyVoice() {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(
            if (targetLanguage.startsWith("hi")) "hi-IN" else "en-IN"
        )
        val allVoices = runCatching { engine.voices?.toList() }.getOrNull().orEmpty()
        val sameLanguage = allVoices.filter {
            it.locale.language.equals(locale.language, ignoreCase = true)
        }
        val online = isOnline()
        val candidates = if (preferBestVoice && online) {
            sameLanguage
        } else {
            // Offline: only voices that can synthesise without a network round trip.
            sameLanguage.filterNot { it.isNetworkConnectionRequired }
        }.ifEmpty { sameLanguage }

        val best = candidates.maxByOrNull { voice ->
            voice.quality * 10 + if (voice.isNetworkConnectionRequired) 1 else 0
        }

        if (best != null) {
            runCatching { engine.voice = best }
            voiceName = best.name
        } else {
            val result = engine.setLanguage(locale)
            voiceName = if (result >= TextToSpeech.LANG_AVAILABLE) {
                locale.toLanguageTag()
            } else {
                engine.setLanguage(Locale.US)
                "en-US"
            }
        }
        engine.setSpeechRate(1.02f)
        engine.setPitch(0.96f)
    }

    private fun isOnline(): Boolean {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (t: Throwable) {
            false
        }
    }

    private fun attachProgress() {
        if (progressAttached) return
        progressAttached = true
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
    }

    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !initialized) {
            pendingText = text
            init(targetLanguage, preferBestVoice)
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
        progressAttached = false
    }

    fun describe(): String = "$engineName · $voiceName"
}
