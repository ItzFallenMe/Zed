package com.zed.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.audio.SpeakerVerifier
import com.zed.assistant.audio.WakeWordDetector
import com.zed.assistant.audio.VoskModelManager
import com.zed.assistant.audio.ZedSoundEffects
import com.zed.assistant.data.ZedPreferences
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Always-listening wake-word service. It owns the microphone only while no
 * Gemini session is active, releases it the instant Zed starts talking, and
 * resumes listening after the session ends. Everything is recognized on-device.
 */
class ZedWakeWordService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val listening = AtomicBoolean(false)
  private var recordJob: Job? = null
  private var detector: WakeWordDetector? = null

  override fun onCreate() {
    super.onCreate()
    if (!hasMicrophonePermission()) {
      Log.w(TAG, "Wake service stopped: RECORD_AUDIO not granted")
      stopSelf()
      return
    }
    createChannel()
    startForegroundSafely("Listening for “Zed”", "On-device wake word · audio never leaves the phone")
    recordJob = scope.launch { runLoop() }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    _isRunning.value = false
    listening.set(false)
    runCatching { detector?.close() }
    detector = null
    scope.cancel()
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  private suspend fun runLoop() {
    if (!hasMicrophonePermission()) {
      Log.w(TAG, "Wake service stopped: RECORD_AUDIO not granted")
      stopSelf()
      return
    }

    VoskModelManager.init(applicationContext, scope)
    val ready = withTimeoutOrNull(60_000L) {
      VoskModelManager.isModelReady.first { it }
    } ?: false
    if (!ready || !scope.isActive) {
      Log.e(TAG, "Wake service stopped: speech model did not load")
      stopSelf()
      return
    }

    val model = VoskModelManager.model ?: run {
      stopSelf()
      return
    }
    detector = WakeWordDetector(model)
    _isRunning.value = true
    updateNotification("Listening for “Zed”", "Say “Zed” to start")

    val minimum = AudioRecord.getMinBufferSize(
      SAMPLE_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    )
    if (minimum <= 0) {
      stopSelf()
      return
    }
    val bufferSize = maxOf(minimum * 2, 8_192)
    val buffer = ByteArray(bufferSize)

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      Log.w(TAG, "Wake service stopped: RECORD_AUDIO was revoked")
      stopSelf()
      return
    }

    while (scope.isActive) {
      // Never fight the Gemini session for the microphone.
      if (ZedEngine.isActive.value || ZedEngine.state.value == com.zed.assistant.model.AssistantState.SPEAKING) {
        if (listening.getAndSet(false)) updateNotification("Zed is active", "Wake word paused during conversation")
        delay(250)
        continue
      }

      var record: AudioRecord? = null
      var wakeTriggered = false
      try {
        record = try {
          AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
          )
        } catch (security: SecurityException) {
          Log.w(TAG, "Wake service stopped: microphone permission was revoked", security)
          stopSelf()
          return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
          Log.e(TAG, "AudioRecord unavailable; stopping wake service")
          break
        }
        record.startRecording()
        listening.set(true)
        updateNotification("Listening for “Zed”", "Say “Zed” to start")

        while (scope.isActive && !ZedEngine.isActive.value) {
          val read = record.read(buffer, 0, buffer.size)
          if (read == 0) continue
          if (read < 0) {
            Log.w(TAG, "AudioRecord read failed: $read")
            break
          }
          val triggered = detector?.accept(buffer, read) == true
          if (!triggered) continue

          Log.i(TAG, "Wake word accepted")
          val preferences = ZedPreferences(applicationContext)
          if (preferences.isOwnerVoiceEnabled && preferences.hasOwnerVoiceProfile()) {
            val match = SpeakerVerifier.verify(
              detector?.recentAudio() ?: ByteArray(0),
              preferences
            )
            if (!match.isMatch) {
              Log.i(
                TAG,
                "Wake ignored: owner voice mismatch (similarity=${match.similarityPercent}%)"
              )
              updateNotification(
                "Voice not recognized",
                "Say the wake word again as the enrolled owner"
              )
              continue
            }
          }
          if (preferences.soundEffectsEnabled) ZedSoundEffects.wake()
          updateNotification("Waking Zed", "Opening a Gemini Live session")
          wakeTriggered = true
          break
        }
      } catch (error: Exception) {
        Log.e(TAG, "Wake loop error", error)
        delay(1_000)
      } finally {
        listening.set(false)
        runCatching { record?.stop() }
        runCatching { record?.release() }
      }

      // Release wake-word AudioRecord before Gemini creates its own recorder.
      // Starting Gemini inside this loop races for the microphone and can
      // leave the Live session silent on devices that allow one input client.
      if (wakeTriggered && scope.isActive) {
        val preferences = ZedPreferences(applicationContext)
        val roleManager = if (android.os.Build.VERSION.SDK_INT >= 29) {
          getSystemService(RoleManager::class.java)
        } else null
        val isDefaultAssistant = roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        val sessionShown = isDefaultAssistant &&
          com.zed.assistant.voice.ZedVoiceInteractionService.requestShow(fromWakeWord = true)

        if (!sessionShown) {
          // Fallback for devices/versions where Zed is not the selected system assistant.
          if (preferences.overlayEnabled && Settings.canDrawOverlays(applicationContext)) {
            com.zed.assistant.overlay.ZedOverlayService.start(applicationContext)
          }
          ZedEngine.start(applicationContext, fromWakeWord = true)
          withTimeoutOrNull(8_000L) { ZedEngine.isActive.first { it } }
        } else {
          // The system-managed session starts ZedEngine after its panel is shown.
          withTimeoutOrNull(8_000L) { ZedEngine.isActive.first { it } }
        }
      }
      delay(200)
    }
  }

  private fun hasMicrophonePermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  private fun createChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Wake word",
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = "On-device wake-word listening. Audio is processed locally and never uploaded."
      setShowBadge(false)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun startForegroundSafely(title: String, text: String) {
    val notification = notificationBuilder(title, text).build()
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      notification,
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      } else {
        0
      }
    )
  }

  private fun updateNotification(title: String, text: String) {
    runCatching {
      getSystemService(NotificationManager::class.java)
        .notify(NOTIFICATION_ID, notificationBuilder(title, text).build())
    }
  }

  private fun notificationBuilder(title: String, text: String): NotificationCompat.Builder =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(NotificationCompat.PRIORITY_LOW)

  companion object {
    private const val TAG = "ZedWakeService"
    private const val CHANNEL_ID = "zed_wake_word"
    private const val NOTIFICATION_ID = 1002
    private const val SAMPLE_RATE = 16_000
    private const val ACTION_STOP = "com.zed.assistant.STOP_WAKE_WORD"

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start(context: Context) {
      if (!hasMicrophonePermission(context)) {
        Log.w(TAG, "Not starting wake service: RECORD_AUDIO not granted")
        stop(context)
        return
      }
      val intent = Intent(context, ZedWakeWordService::class.java)
      runCatching { ContextCompat.startForegroundService(context, intent) }
        .onFailure { Log.e(TAG, "Could not start wake service", it) }
    }

    fun stop(context: Context) {
      runCatching { context.stopService(Intent(context, ZedWakeWordService::class.java)) }
    }

    /** Starts or stops listening to match the persisted preference. */
    fun sync(context: Context) {
      if (ZedPreferences(context).wakeWordEnabled && hasMicrophonePermission(context)) {
        start(context)
      } else {
        stop(context)
      }
    }

    private fun hasMicrophonePermission(context: Context): Boolean =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
  }

}
