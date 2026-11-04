package com.zed.assistant.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.Manifest
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the real audio hardware for a Zed conversation.
 *
 * Capture: 16 kHz, mono, 16-bit PCM sent straight to Gemini Live.
 * Playback: one persistent AudioTrack at 24 kHz (recreated only if Gemini
 * advertises a different rate), fed by a sequential queue of PCM chunks.
 * Interruptions flush both the queue and the hardware buffer.
 */
class AudioEngine(context: Context) {

  companion object {
    private const val TAG = "ZedAudio"
    private const val INPUT_CHUNK_BYTES = 3_200 // 100 ms of 16 kHz 16-bit mono
    private const val IDLE_GRACE_MS = 700L
  }

  private data class PcmChunk(val data: ByteArray, val sampleRate: Int, val epoch: Long)

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var recordJob: Job? = null
  private var playbackJob: Job? = null

  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null
  private var trackRate = 0

  private val playQueue = ConcurrentLinkedQueue<PcmChunk>()
  private val writeLock = Any()

  @Volatile
  private var playbackActive = false

  /** Incremented on every flush so an in-flight stale chunk cannot be written. */
  @Volatile
  private var playbackEpoch = 0L

  @Volatile
  private var idleNotified = true

  private var idleSince = 0L
  private var chunksReceived = 0

  private var echoCanceler: AcousticEchoCanceler? = null
  private var noiseSuppressor: NoiseSuppressor? = null

  private var audioManager: AudioManager? = null
  private var focusRequest: AudioFocusRequest? = null
  private var previousMode = AudioManager.MODE_NORMAL
  private var previousSpeakerphone = false
  private var sessionConfigured = false

  private val _level = MutableStateFlow(0f)
  val level: StateFlow<Float> = _level

  /** Called with raw 16 kHz mono PCM16 mic chunks. */
  var onMicChunk: ((ByteArray) -> Unit)? = null

  /** Called when the first chunk of a response starts playing. */
  var onPlaybackStarted: (() -> Unit)? = null

  /** Called when the output queue and hardware buffer have been quiet for a moment. */
  var onPlaybackIdle: (() -> Unit)? = null

  /** Called for non-silent hardware failures. */
  var onError: ((String) -> Unit)? = null

  // ---------------------------------------------------------------- session

  fun beginAudioSession() {
    val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager = manager
    if (!sessionConfigured) {
      previousMode = manager.mode
      previousSpeakerphone = manager.isSpeakerphoneOn
      sessionConfigured = true
    }
    try {
      // Communication mode plus speakerphone keeps output audible and gives the
      // platform the best chance of cancelling Zed's own voice from the mic.
      manager.mode = AudioManager.MODE_IN_COMMUNICATION
      manager.isSpeakerphoneOn = true
    } catch (error: Exception) {
      Log.e(TAG, "Could not configure audio routing", error)
    }

    if (focusRequest == null) {
      val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
      val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .build()
      focusRequest = request
      val result = manager.requestAudioFocus(request)
      Log.d(TAG, "Audio focus requested, result=$result")
    }

    startPlayback()
    Log.d(TAG, "Audio session ready (mode=${manager.mode}, speaker=${manager.isSpeakerphoneOn})")
  }

  fun endAudioSession() {
    stopPlayback()
    val manager = audioManager
    if (manager != null) {
      focusRequest?.let { manager.abandonAudioFocusRequest(it) }
      focusRequest = null
      if (sessionConfigured) {
        try {
          manager.isSpeakerphoneOn = previousSpeakerphone
          manager.mode = previousMode
        } catch (error: Exception) {
          Log.w(TAG, "Could not restore audio routing", error)
        }
      }
    }
    sessionConfigured = false
    Log.d(TAG, "Audio session ended")
  }

  // -------------------------------------------------------------- recording

  @SuppressLint("MissingPermission")
  fun startRecording(): Boolean {
    if (recordJob?.isActive == true) return true

    if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      Log.e(TAG, "Microphone permission is not granted")
      onError?.invoke("Microphone permission is required")
      return false
    }

    val minBuffer = AudioRecord.getMinBufferSize(
      AudioPcm.INPUT_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    )
    if (minBuffer <= 0) {
      Log.e(TAG, "AudioRecord.getMinBufferSize failed: $minBuffer")
      onError?.invoke("Microphone is unavailable on this device")
      return false
    }
    val bufferSize = max(minBuffer * 2, INPUT_CHUNK_BYTES * 4)

    val record = createAudioRecord(bufferSize)
    if (record == null) {
      onError?.invoke("Could not initialise the microphone")
      return false
    }

    audioRecord = record
    attachAudioEffects(record)

    try {
      record.startRecording()
    } catch (error: Exception) {
      Log.e(TAG, "AudioRecord.startRecording failed", error)
      record.release()
      audioRecord = null
      onError?.invoke("Could not start the microphone")
      return false
    }

    Log.d(TAG, "Microphone started (16 kHz mono PCM16, buffer=$bufferSize bytes)")
    recordJob = scope.launch {
      val buffer = ByteArray(INPUT_CHUNK_BYTES)
      while (isActive) {
        val read = try {
          record.read(buffer, 0, buffer.size)
        } catch (error: Exception) {
          Log.e(TAG, "Microphone read failed", error)
          -1
        }
        if (read > 0) {
          val chunk = buffer.copyOf(read)
          _level.value = (AudioPcm.rms(chunk) * 4f).coerceIn(0f, 1f)
          onMicChunk?.invoke(chunk)
        } else if (read < 0) {
          Log.e(TAG, "Microphone read error: $read")
          onError?.invoke("Microphone read error")
          break
        }
      }
      Log.d(TAG, "Microphone loop stopped")
    }
    return true
  }

  private fun createAudioRecord(bufferSize: Int): AudioRecord? {
    if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      Log.e(TAG, "Cannot create AudioRecord without RECORD_AUDIO permission")
      return null
    }

    val sources = intArrayOf(
      MediaRecorder.AudioSource.VOICE_COMMUNICATION,
      MediaRecorder.AudioSource.MIC
    )
    for (source in sources) {
      var record: AudioRecord? = null
      try {
        record = AudioRecord(
          source,
          AudioPcm.INPUT_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize
        )
      } catch (error: Exception) {
        Log.e(TAG, "AudioRecord creation failed for source $source", error)
      }
      if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
        Log.d(TAG, "Using microphone source=$source")
        return record
      }
      record?.release()
    }
    return null
  }

  private fun attachAudioEffects(record: AudioRecord) {
    try {
      if (AcousticEchoCanceler.isAvailable()) {
        echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
        Log.d(TAG, "AcousticEchoCanceler enabled=${echoCanceler?.enabled}")
      }
      if (NoiseSuppressor.isAvailable()) {
        noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
        Log.d(TAG, "NoiseSuppressor enabled=${noiseSuppressor?.enabled}")
      }
    } catch (error: Exception) {
      Log.w(TAG, "Could not enable audio effects", error)
    }
  }

  fun stopRecording() {
    val job = recordJob
    recordJob = null
    job?.cancel()
    echoCanceler?.let { runCatching { it.enabled = false; it.release() } }
    noiseSuppressor?.let { runCatching { it.enabled = false; it.release() } }
    echoCanceler = null
    noiseSuppressor = null
    val record = audioRecord
    audioRecord = null
    if (record != null) {
      try {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
      } catch (error: Exception) {
        Log.w(TAG, "AudioRecord.stop failed", error)
      }
      record.release()
      Log.d(TAG, "Microphone stopped and released")
    }
    _level.value = 0f
  }

  // --------------------------------------------------------------- playback

  fun startPlayback() {
    if (playbackJob?.isActive == true) return
    playbackEpoch++
    idleNotified = true
    playQueue.clear()
    playbackActive = false
    playbackJob = scope.launch {
      while (isActive) {
        val chunk = playQueue.poll()
        if (chunk != null) {
          idleSince = 0L
          writeChunk(chunk)
        } else {
          if (playbackActive && !idleNotified) {
            if (idleSince == 0L) {
              idleSince = SystemClock.elapsedRealtime()
            } else if (SystemClock.elapsedRealtime() - idleSince > IDLE_GRACE_MS) {
              finishPlayback()
            }
          }
          delay(15)
        }
      }
    }
  }

  /** Queues one PCM16 chunk returned by Gemini. */
  fun queueAudio(pcm: ByteArray, sampleRate: Int) {
    if (pcm.isEmpty()) return
    val rate = sampleRate.takeIf { it in 8_000..48_000 } ?: AudioPcm.DEFAULT_OUTPUT_RATE
    if (ensureTrack(rate) == null) return

    if (playbackJob?.isActive != true) startPlayback()
    playQueue.add(PcmChunk(pcm, rate, playbackEpoch))
    chunksReceived++
    if (chunksReceived == 1 || chunksReceived % 20 == 0) {
      Log.d(
        TAG,
        "Queued Gemini audio chunk #$chunksReceived (${pcm.size} bytes, rate=$rate, " +
          "queue=${playQueue.size})"
      )
    }
  }

  fun isPlayingOrQueued(): Boolean = playQueue.isNotEmpty() || playbackActive

  fun hasQueuedAudio(): Boolean = playQueue.isNotEmpty()

  /** Immediately stops current speech and discards everything still queued. */
  fun interruptPlayback() {
    synchronized(writeLock) {
      val dropped = playQueue.size
      playQueue.clear()
      playbackEpoch++
      try {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
      } catch (error: Exception) {
        Log.e(TAG, "Could not flush AudioTrack", error)
      }
      playbackActive = false
      idleNotified = true
      _level.value = 0f
      Log.d(TAG, "Audio playback interrupted; dropped $dropped queued chunks")
    }
  }

  fun stopPlayback() {
    val job = playbackJob
    playbackJob = null
    job?.cancel()
    playQueue.clear()
    synchronized(writeLock) {
      playbackEpoch++
      releaseTrack()
    }
    playbackActive = false
    idleNotified = true
    _level.value = 0f
    Log.d(TAG, "Playback stopped")
  }

  /** Plays a 440 Hz diagnostic tone through the same output graph. */
  fun playTestTone() {
    val tone = AudioPcm.tone(440.0, 0.45, AudioPcm.DEFAULT_OUTPUT_RATE)
    Log.d(TAG, "Speaker test tone queued (440 Hz, ${tone.size} bytes)")
    queueAudio(tone, AudioPcm.DEFAULT_OUTPUT_RATE)
  }

  private fun ensureTrack(rate: Int): AudioTrack? {
    synchronized(writeLock) {
      val existing = audioTrack
      if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED && trackRate == rate) {
        return existing
      }
      releaseTrack()

      val minBuffer = AudioTrack.getMinBufferSize(
        rate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
      if (minBuffer <= 0) {
        Log.e(TAG, "AudioTrack.getMinBufferSize failed: $minBuffer")
        onError?.invoke("Audio output is unavailable on this device")
        return null
      }
      val bufferSize = max(minBuffer * 2, rate / 2 * 2) // at least ~0.5 s

      val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
      val format = AudioFormat.Builder()
        .setSampleRate(rate)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

      val track = try {
        AudioTrack(attributes, format, bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
      } catch (error: Exception) {
        Log.e(TAG, "AudioTrack creation failed", error)
        null
      }

      if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
        Log.e(TAG, "AudioTrack is not initialised (state=${track?.state})")
        track?.release()
        onError?.invoke("Could not initialise audio output")
        return null
      }

      track.play()
      audioTrack = track
      trackRate = rate
      Log.d(TAG, "AudioTrack ready (rate=$rate, buffer=$bufferSize, state=${track.state})")
      return track
    }
  }

  private fun writeChunk(chunk: PcmChunk) {
    synchronized(writeLock) {
      if (chunk.epoch != playbackEpoch) {
        Log.d(TAG, "Discarded stale audio chunk from an interrupted turn")
        return
      }
      val track = ensureTrack(chunk.sampleRate)
      if (track == null) return
      try {
        val written = track.write(chunk.data, 0, chunk.data.size)
        if (written < 0) {
          Log.e(TAG, "AudioTrack.write failed: $written")
          onError?.invoke("Audio playback failed ($written)")
          return
        }
        if (!playbackActive) {
          playbackActive = true
          idleNotified = false
          Log.d(TAG, "Audio playback started (${chunk.data.size} bytes)")
          onPlaybackStarted?.invoke()
        }
        _level.value = (AudioPcm.rms(chunk.data) * 4f).coerceIn(0f, 1f)
      } catch (error: Exception) {
        Log.e(TAG, "Audio playback exception", error)
        onError?.invoke("Audio playback error")
      }
    }
  }

  private fun finishPlayback() {
    synchronized(writeLock) {
      if (idleNotified) return
      playbackActive = false
      idleNotified = true
      _level.value = 0f
      Log.d(TAG, "Audio playback ended")
    }
    onPlaybackIdle?.invoke()
  }

  private fun releaseTrack() {
    val track = audioTrack
    audioTrack = null
    trackRate = 0
    if (track != null) {
      try {
        track.pause()
        track.flush()
        track.stop()
      } catch (error: Exception) {
        Log.w(TAG, "Error while stopping AudioTrack", error)
      }
      track.release()
      Log.d(TAG, "AudioTrack released")
    }
  }

  fun release() {
    stopRecording()
    endAudioSession()
  }
}
