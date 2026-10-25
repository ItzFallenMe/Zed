package dev.zed.assistant.vision

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Translation runs entirely on device once the small language model is downloaded.
 */
class OnDeviceTranslator {

    private var cachedPair: Pair<String, String>? = null
    private var cachedTranslator: Translator? = null

    suspend fun translate(text: String, source: String = "en", target: String = "hi"): String {
        if (text.isBlank()) return ""
        val sourceCode = languageCode(source)
        val targetCode = languageCode(target)
        if (sourceCode == targetCode) return text

        val translator = translatorFor(sourceCode, targetCode)
        return try {
            translator.downloadModelIfNeeded(
                DownloadConditions.Builder().build()
            ).await()
            val result = translator.translate(text).await()
            ZedState.log("TRANSLATE", "${sourceCode.uppercase()} -> ${targetCode.uppercase()} (${text.length} chars)")
            result
        } catch (t: Throwable) {
            ZedState.log("TRANSLATE", "Translation failed: ${t.message}")
            text
        }
    }

    private fun translatorFor(sourceCode: String, targetCode: String): Translator {
        val pair = sourceCode to targetCode
        cachedTranslator?.let { if (cachedPair == pair) return it }
        runCatching { cachedTranslator?.close() }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(targetCode)
            .build()
        return Translation.getClient(options).also {
            cachedTranslator = it
            cachedPair = pair
        }
    }

    private fun languageCode(language: String): String = when (language.lowercase().take(2)) {
        "hi" -> TranslateLanguage.HINDI
        "en" -> TranslateLanguage.ENGLISH
        "mr" -> TranslateLanguage.MARATHI
        "bn" -> TranslateLanguage.BENGALI
        "ta" -> TranslateLanguage.TAMIL
        "te" -> TranslateLanguage.TELUGU
        "ur" -> TranslateLanguage.URDU
        else -> TranslateLanguage.ENGLISH
    }

    fun close() {
        runCatching { cachedTranslator?.close() }
        cachedTranslator = null
        cachedPair = null
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
            addOnFailureListener { error -> if (continuation.isActive) continuation.cancel(error) }
            addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
        }
}
