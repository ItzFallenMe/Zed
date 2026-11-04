package com.zed.assistant.audio

import android.util.Log
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Streaming wake-word detection on 16 kHz mono PCM. Recognition is deliberately
 * unconstrained (no grammar): the bundled small English model does not list
 * "zed" as a word, and a grammar would therefore reject the wake phrase.
 */
class WakeWordDetector(private val model: Model) {

  private var recognizer = Recognizer(model, SAMPLE_RATE)
  private var lastTriggerAt = 0L

  fun accept(pcm: ByteArray, length: Int, now: Long = System.currentTimeMillis()): Boolean {
    if (length <= 0) return false
    val finishedUtterance = try {
      recognizer.acceptWaveForm(pcm, length)
    } catch (error: Throwable) {
      Log.e(TAG, "Wake recognizer failed", error)
      reset()
      return false
    }

    val text = if (finishedUtterance) {
      runCatching { JSONObject(recognizer.finalResult).optString("text") }.getOrDefault("")
    } else {
      runCatching { JSONObject(recognizer.partialResult).optString("partial") }.getOrDefault("")
    }

    if (!matches(text)) return false
    if (now - lastTriggerAt < DEBOUNCE_MS) return false
    lastTriggerAt = now
    Log.i(TAG, "Wake phrase detected: '$text'")
    reset()
    return true
  }

  fun reset() {
    runCatching { recognizer.close() }
    runCatching { recognizer = Recognizer(model, SAMPLE_RATE) }
  }

  fun close() = runCatching { recognizer.close() }.let { }

  private fun matches(raw: String): Boolean {
    val words = raw.lowercase(Locale.ROOT)
      .replace(Regex("[^a-z ]"), " ")
      .split(Regex("\\s+"))
      .filter { it.isNotBlank() }
    if (words.isEmpty()) return false

    for (index in words.indices) {
      val word = words[index]
      val isName = word in NAME_VARIANTS
      if (!isName) continue
      if (index == 0) return true
      if (words[index - 1] in WAKE_PREFIXES) return true
    }
    return false
  }

  private companion object {
    const val TAG = "ZedWake"
    const val SAMPLE_RATE = 16_000f
    const val DEBOUNCE_MS = 3_000L

    val NAME_VARIANTS = setOf("zed", "zad", "sed", "said", "set")
    val WAKE_PREFIXES = setOf("hey", "ok", "okay", "yo", "hi", "hello")
  }
}
