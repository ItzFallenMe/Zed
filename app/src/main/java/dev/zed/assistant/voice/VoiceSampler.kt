package dev.zed.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import dev.zed.assistant.core.ZedState
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Short blocking recordings for voice enrollment and identification. Runs off the main
 * thread; the caller owns the result.
 */
object VoiceSampler {

    /** Records [durationMs] of 16 kHz mono audio, trimming leading silence. */
    @SuppressLint("MissingPermission")
    fun record(durationMs: Int): ShortArray? {
        val minBuffer = AudioRecord.getMinBufferSize(
            Voiceprint.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                Voiceprint.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
        } catch (t: Throwable) {
            ZedState.log("VOICE", "Recorder unavailable: ${t.message}")
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder.release() }
            return null
        }
        val bytes = ByteArrayOutputStream()
        val chunk = ByteArray(3200)
        val deadline = SystemClock.elapsedRealtime() + durationMs
        try {
            recorder.startRecording()
            while (SystemClock.elapsedRealtime() < deadline) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) bytes.write(chunk, 0, read) else if (read < 0) break
            }
        } catch (t: Throwable) {
            ZedState.log("VOICE", "Recording failed: ${t.message}")
            return null
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
        return toShorts(bytes.toByteArray())?.let { trim(it) }
    }

    fun toShorts(pcm: ByteArray): ShortArray {
        if (pcm.size < 2) return ShortArray(0)
        val out = ShortArray(pcm.size / 2)
        for (i in out.indices) {
            val low = pcm[i * 2].toInt() and 0xFF
            val high = pcm[i * 2 + 1].toInt()
            out[i] = ((high shl 8) or low).toShort()
        }
        return out
    }

    /** Drops silence at both ends so the fingerprint is speech, not room tone. */
    fun trim(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples
        var energy = 0.0
        for (sample in samples) energy += sample.toDouble() * sample
        val rms = sqrt(energy / samples.size)
        val gate = (rms * 0.55).coerceAtLeast(120.0)
        var start = 0
        while (start < samples.size && kotlin.math.abs(samples[start].toInt()) < gate) start++
        var end = samples.size - 1
        while (end > start && kotlin.math.abs(samples[end].toInt()) < gate) end--
        if (end - start < samples.size / 4) return samples
        return samples.copyOfRange(start, end)
    }
}
