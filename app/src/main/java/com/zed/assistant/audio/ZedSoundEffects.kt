package com.zed.assistant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Two procedural earcons, synthesised into 16-bit PCM so the APK carries no
 * audio files. Kept short and quiet so they sit under the voice assistant's
 * `USAGE_ASSISTANT` audio path.
 */
object ZedSoundEffects {

  private const val TAG = "ZedSounds"
  private const val SAMPLE_RATE = 44_100

  private val wakePcm by lazy { synthesise { progress, sample -> chime(progress, sample) } }
  private val standbyPcm by lazy { synthesise { progress, _ -> descent(progress) } }

  fun wake() = play(wakePcm)

  fun standby() = play(standbyPcm)

  private fun play(pcm: ByteArray) {
    Thread {
      var track: AudioTrack? = null
      try {
        track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setSampleRate(SAMPLE_RATE)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .build()
          )
          .setBufferSizeInBytes(pcm.size)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((pcm.size / 2L * 1000L / SAMPLE_RATE) + 80L)
      } catch (error: Exception) {
        Log.w(TAG, "Earcon could not play", error)
      } finally {
        runCatching { track?.stop() }
        runCatching { track?.release() }
      }
    }.start()
  }

  private inline fun synthesise(crossinline wave: (Double, Double) -> Double): ByteArray {
    val totalMs = 260
    val frames = SAMPLE_RATE * totalMs / 1000
    val out = ByteArray(frames * 2)
    for (index in 0 until frames) {
      val progress = index.toDouble() / frames
      val seconds = index.toDouble() / SAMPLE_RATE
      val envelope = envelope(progress)
      val value = (wave(progress, seconds) * envelope * 18_000.0)
        .toInt().coerceIn(-32767, 32767)
      out[index * 2] = (value and 0xFF).toByte()
      out[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }
    return out
  }

  private fun envelope(progress: Double): Double {
    if (progress > 0.92) return (1.0 - progress) / 0.08
    return 1.0
  }

  private fun chime(progress: Double, seconds: Double): Double {
    return if (progress < 0.4) {
      sin(2 * PI * 587.33 * seconds) + 0.25 * sin(2 * PI * 1174.66 * seconds)
    } else if (progress < 0.48) {
      0.0
    } else {
      sin(2 * PI * 880.0 * seconds) + 0.30 * sin(2 * PI * 1760.0 * seconds)
    }
  }

  private fun descent(progress: Double): Double {
    return if (progress < 0.38) {
      sin(2 * PI * 880.0 * progress)
    } else {
      sin(2 * PI * 587.33 * progress)
    }
  }
}
