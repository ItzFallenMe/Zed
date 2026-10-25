package dev.zed.assistant.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import dev.zed.assistant.core.ZedState
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Streams 16 kHz mono PCM16 from the microphone in 50 ms chunks.
 */
class MicRecorder(
    private val context: Context? = null,
    private val sampleRate: Int = 16000,
    private val onChunk: (ByteArray) -> Unit
) {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private var echoCanceller: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile
    private var running = false

    val isRecording: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        // Checked up front: constructing AudioRecord without the grant throws, and an
        // exception on a worker thread takes the whole process down with it.
        if (context != null &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ZedState.log("MIC", "Microphone permission missing")
            return false
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            ZedState.log("MIC", "Invalid audio buffer size")
            return false
        }
        val bufferSize = minBuffer * 2
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (t: Throwable) {
            ZedState.log("MIC", "AudioRecord failed: ${t.message}")
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            ZedState.log("MIC", "Microphone unavailable")
            runCatching { recorder.release() }
            return false
        }
        record = recorder
        running = true
        // Full-duplex conversation: without echo cancellation Zed hears its own voice through
        // the speaker and interrupts itself. Attach the platform effects where available.
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceller = AcousticEchoCanceler.create(recorder.audioSessionId)
                    ?.apply { enabled = true }
            }
        }
        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)
                    ?.apply { enabled = true }
            }
        }
        recorder.startRecording()
        worker = thread(name = "zed-mic", isDaemon = true) {
            val chunk = ByteArray(CHUNK_BYTES)
            while (running) {
                val read = try {
                    recorder.read(chunk, 0, chunk.size)
                } catch (t: Throwable) {
                    -1
                }
                if (read > 0) {
                    onChunk(chunk.copyOf(read))
                } else if (read < 0) {
                    break
                }
            }
        }
        return true
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        runCatching { echoCanceller?.release() }
        runCatching { noiseSuppressor?.release() }
        echoCanceller = null
        noiseSuppressor = null
        record?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        record = null
    }

    companion object {
        private const val CHUNK_BYTES = 1600 // 50 ms at 16 kHz, 16-bit mono
    }
}

/**
 * Streams 24 kHz PCM16 chunks to the speaker without blocking the network thread.
 */
class PcmPlayer(
    private val sampleRate: Int = 24000
) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    /** Called once the queued speech has finished, so the UI can leave SPEAKING. */
    @Volatile
    var onDrained: (() -> Unit)? = null

    fun start(): Boolean {
        if (running) return true
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else 8192
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
            ZedState.log("PLAYER", "AudioTrack failed: ${t.message}")
            return false
        }
        track = audioTrack
        running = true
        audioTrack.play()
        worker = thread(name = "zed-player", isDaemon = true) {
            var idleSince = 0L
            while (running) {
                val chunk = try {
                    queue.poll(120, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (t: InterruptedException) {
                    break
                }
                if (chunk == null) {
                    // A short gap is normal while chunks stream in; a long one means done.
                    val now = System.currentTimeMillis()
                    if (idleSince == 0L) idleSince = now
                    if (now - idleSince > 320L) {
                        idleSince = 0L
                        if (ZedState.speaking.value) onDrained?.invoke()
                    }
                    continue
                }
                idleSince = 0L
                try {
                    audioTrack.write(chunk, 0, chunk.size)
                } catch (t: Throwable) {
                    break
                }
                ZedState.speaking.value = queue.isNotEmpty()
            }
        }
        return true
    }

    fun enqueue(pcm: ByteArray) {
        if (!running) start()
        ZedState.speaking.value = true
        queue.offer(pcm)
    }

    /** Barge-in: drop everything still queued so the new answer is not buried. */
    fun clear() {
        queue.clear()
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.play() }
        ZedState.speaking.value = false
    }

    fun stop() {
        running = false
        queue.clear()
        worker?.interrupt()
        worker = null
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
        ZedState.speaking.value = false
    }

    fun isPlaying(): Boolean = running && queue.isNotEmpty()
}
