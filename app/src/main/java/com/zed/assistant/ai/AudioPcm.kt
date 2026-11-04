package com.zed.assistant.ai

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helpers for the raw PCM format used by Gemini Live.
 *
 * Gemini Live input is signed 16-bit little-endian mono PCM at 16 kHz.
 * Gemini Live output is signed 16-bit little-endian mono PCM at 24 kHz
 * (the rate is also advertised in each response's MIME type).
 */
object AudioPcm {

  const val DEFAULT_OUTPUT_RATE = 24_000
  const val INPUT_RATE = 16_000

  private val RATE_PATTERN = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE)

  /** Extracts the sample rate from e.g. "audio/pcm;rate=24000". */
  fun sampleRateFromMime(mime: String?): Int {
    if (mime.isNullOrBlank()) return DEFAULT_OUTPUT_RATE
    val parsed = RATE_PATTERN.find(mime)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return parsed?.takeIf { it in 8_000..48_000 } ?: DEFAULT_OUTPUT_RATE
  }

  /** Root-mean-square level (0..1) of 16-bit LE PCM, for the waveform. */
  fun rms(pcm: ByteArray): Float {
    if (pcm.size < 2) return 0f
    var sum = 0.0
    var count = 0
    var index = 0
    while (index + 1 < pcm.size) {
      val lo = pcm[index].toInt() and 0xFF
      val hi = pcm[index + 1].toInt()
      val sample = ((hi shl 8) or lo).toShort().toInt()
      val normalized = sample / 32768.0
      sum += normalized * normalized
      count++
      index += 2
    }
    return if (count == 0) 0f else sqrt(sum / count).toFloat()
  }

  /**
   * Generates a short faded sine tone as 16-bit LE PCM. Used by the speaker
   * diagnostic so the exact same AudioTrack/audio graph as Gemini audio is tested.
   */
  fun tone(
    frequency: Double = 440.0,
    seconds: Double = 0.45,
    sampleRate: Int = DEFAULT_OUTPUT_RATE,
    amplitude: Double = 0.3
  ): ByteArray {
    val frames = (sampleRate * seconds).toInt().coerceAtLeast(1)
    val fadeFrames = (sampleRate * 0.02).toInt().coerceAtLeast(1)
    val out = ByteArray(frames * 2)
    for (i in 0 until frames) {
      val fadeIn = (i.toDouble() / fadeFrames).coerceIn(0.0, 1.0)
      val fadeOut = ((frames - i).toDouble() / fadeFrames).coerceIn(0.0, 1.0)
      val envelope = minOf(fadeIn, fadeOut)
      val value = (sin(2 * PI * frequency * i / sampleRate) * amplitude * envelope * 32767.0)
        .toInt()
        .toShort()
      out[i * 2] = (value.toInt() and 0xFF).toByte()
      out[i * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
    return out
  }
}
