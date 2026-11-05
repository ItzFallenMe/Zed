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
  private val history = ByteArray(HISTORY_BYTES)
  private var historyWrite = 0
  private var historyCount = 0

  fun accept(pcm: ByteArray, length: Int, now: Long = System.currentTimeMillis()): Boolean {
    if (length <= 0) return false
    remember(pcm, length)
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

  /**
   * A short pre-roll of the most recent microphone audio. The wake service
   * verifies this against the enrolled owner profile before opening a session.
   */
  fun recentAudio(maxBytes: Int = HISTORY_BYTES): ByteArray {
    val size = minOf(maxBytes, historyCount)
    if (size <= 0) return ByteArray(0)
    val output = ByteArray(size)
    val start = (historyWrite - size + history.size) % history.size
    val first = minOf(size, history.size - start)
    System.arraycopy(history, start, output, 0, first)
    if (size > first) System.arraycopy(history, 0, output, first, size - first)
    return output
  }

  private fun remember(pcm: ByteArray, length: Int) {
    val take = minOf(length, history.size)
    val sourceStart = length - take
    val first = minOf(take, history.size - historyWrite)
    System.arraycopy(pcm, sourceStart, history, historyWrite, first)
    if (take > first) System.arraycopy(pcm, sourceStart + first, history, 0, take - first)
    historyWrite = (historyWrite + take) % history.size
    historyCount = minOf(history.size, historyCount + take)
  }

  fun close() = runCatching { recognizer.close() }.let { }

  private fun matches(raw: String): Boolean {
    val words = raw.lowercase(Locale.ROOT)
      .replace(Regex("[^a-z ]"), " ")
      .split(Regex("\\s+"))
      .filter { it.isNotBlank() }
    if (words.isEmpty()) return false

    // The requested wake phrase is the single word "Zed" — no "Hey",
    // "Okay", or other prefix is required.
    return words.any { it == "zed" }
  }

  private companion object {
    const val TAG = "ZedWake"
    const val SAMPLE_RATE = 16_000f
    const val DEBOUNCE_MS = 3_000L
    const val HISTORY_BYTES = 16_000 * 2 * 6 // six seconds of 16-bit mono

  }
}
