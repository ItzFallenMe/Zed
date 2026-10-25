package dev.zed.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import dev.zed.assistant.core.ZedState

data class TtsVoice(
    val name: String,
    val locale: String,
    val quality: Int,
    val network: Boolean
) {
    fun qualityLabel(): String = when {
        quality >= Voice.QUALITY_VERY_HIGH -> "Very high"
        quality >= Voice.QUALITY_HIGH -> "High"
        quality >= Voice.QUALITY_NORMAL -> "Normal"
        quality >= Voice.QUALITY_LOW -> "Low"
        else -> "Very low"
    }
}

/**
 * Reads the voices the installed speech engine offers. Used only to populate the
 * chooser; no audio leaves the device.
 */
object VoiceCatalog {

    private val supportedLanguages = setOf("en", "hi")

    fun load(context: Context, onReady: (List<TtsVoice>) -> Unit) {
        val holder = arrayOfNulls<TextToSpeech>(1)
        val engine = TextToSpeech(context.applicationContext) { status ->
            val voices = if (status == TextToSpeech.SUCCESS) {
                runCatching { holder[0]?.voices }.getOrNull().orEmpty()
                    .mapNotNull { voice ->
                        val locale = voice.locale
                        if (locale.language !in supportedLanguages) return@mapNotNull null
                        TtsVoice(
                            name = voice.name,
                            locale = locale.toLanguageTag(),
                            quality = voice.quality,
                            network = voice.isNetworkConnectionRequired
                        )
                    }
                    .distinctBy { it.name }
                    .sortedWith(
                        compareByDescending<TtsVoice> { it.quality }
                            .thenByDescending { !it.network }
                            .thenBy { it.locale }
                    )
            } else {
                emptyList()
            }
            runCatching { holder[0]?.shutdown() }
            holder[0] = null
            ZedState.log("TTS", "Found ${voices.size} voices")
            onReady(voices)
        }
        holder[0] = engine
    }
}
