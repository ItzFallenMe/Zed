package com.zed.assistant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Short, cancellable 16 kHz mono recordings used for owner-voice enrolment. */
object VoiceCapture {

  private const val TAG = "ZedVoiceCapture"
  private const val SAMPLE_RATE = 16_000

  @SuppressLint("MissingPermission")
  suspend fun record(seconds: Int = 4): ByteArray? = withContext(Dispatchers.IO) {
    val minimum = AudioRecord.getMinBufferSize(
      SAMPLE_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    )
    if (minimum <= 0) return@withContext null

    val bufferSize = maxOf(minimum * 2, 8_192)
    val record = try {
      AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
      )
    } catch (error: Exception) {
      Log.e(TAG, "Could not create AudioRecord", error)
      return@withContext null
    }

    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      return@withContext null
    }

    val output = ByteArrayOutputStream()
    val buffer = ByteArray(bufferSize)
    val targetBytes = SAMPLE_RATE * 2 * seconds
    val startedAt = System.nanoTime()
    try {
      record.startRecording()
      while (coroutineContext.isActive && output.size() < targetBytes) {
        val read = record.read(buffer, 0, buffer.size)
        if (read > 0) output.write(buffer, 0, read)
        else if (read < 0) break
        if (System.nanoTime() - startedAt > (seconds + 2L) * 1_000_000_000L) break
      }
    } catch (error: Exception) {
      Log.e(TAG, "Recording failed", error)
    } finally {
      runCatching { record.stop() }
      runCatching { record.release() }
    }

    val pcm = output.toByteArray()
    if (pcm.size < SAMPLE_RATE * 2 / 2) null else pcm
  }
}
