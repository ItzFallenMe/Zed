package dev.zed.assistant.security

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dev.zed.assistant.core.ZedState
import kotlin.math.PI
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * Generates a two-tone security siren straight into the alarm stream at full volume.
 * Nothing is stored, so there is no audio file to leak or tamper with.
 */
class SirenPlayer(private val context: Context) {

    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    fun start() {
        if (isPlaying) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            ZedState.log("SIREN", "AudioTrack failed: ${t.message}")
            return
        }
        track = audioTrack
        isPlaying = true
        audioTrack.play()
        worker = thread(name = "zed-siren", isDaemon = true) {
            val chunk = ShortArray(4096)
            var phase = 0.0
            var step = 0
            while (isPlaying) {
                for (i in chunk.indices) {
                    val frequency = if ((step / 6) % 2 == 0) 1180.0 else 760.0
                    phase += 2.0 * PI * frequency / sampleRate
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                    chunk[i] = (sin(phase) * Short.MAX_VALUE * 0.92).toInt().toShort()
                }
                step++
                try {
                    audioTrack.write(chunk, 0, chunk.size)
                } catch (t: Throwable) {
                    break
                }
            }
        }
        ZedState.sirenActive.value = true
        ZedState.log("SIREN", "Siren sounding")
    }

    fun stop() {
        isPlaying = false
        worker?.interrupt()
        worker = null
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
        ZedState.sirenActive.value = false
        ZedState.log("SIREN", "Siren stopped")
    }
}
