package dev.zed.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.zed.assistant.MainActivity
import dev.zed.assistant.R
import dev.zed.assistant.command.CommandExecutor
import dev.zed.assistant.command.IntentParser
import dev.zed.assistant.command.ParsedCommand
import dev.zed.assistant.command.ZedPrompts
import dev.zed.assistant.command.ZedTools
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.device.HardwareController
import dev.zed.assistant.security.AntiTheftService
import dev.zed.assistant.security.SirenPlayer
import dev.zed.assistant.vision.OcrEngine
import dev.zed.assistant.vision.OnDeviceTranslator
import dev.zed.assistant.vision.ScreenCapture
import dev.zed.assistant.voice.GeminiLiveClient
import dev.zed.assistant.voice.MicRecorder
import dev.zed.assistant.voice.OneShotRecognizer
import dev.zed.assistant.voice.PcmPlayer
import dev.zed.assistant.voice.WakeWordDetector
import dev.zed.assistant.voice.ZedSpeaker
import dev.zed.assistant.whatsapp.WhatsAppController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The always-on brain. Owns the wake word, the live Gemini session, screen vision and
 * every command route. The Compose UI only sends intents and observes [ZedState].
 */
class ZedCoreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var prefs: ZedPrefs
    private lateinit var hardware: HardwareController
    private lateinit var whatsapp: WhatsAppController
    private lateinit var screenCapture: ScreenCapture
    private lateinit var ocr: OcrEngine
    private lateinit var translator: OnDeviceTranslator
    private lateinit var speaker: ZedSpeaker
    private lateinit var executor: CommandExecutor
    private lateinit var siren: SirenPlayer

    private var wakeDetector: WakeWordDetector? = null
    private var oneShot: OneShotRecognizer? = null
    private var gemini: GeminiLiveClient? = null
    private var mic: MicRecorder? = null
    private var player: PcmPlayer? = null

    private val liveTranscript = StringBuilder()
    private var pendingPrompt: String? = null
    private var idleJob: Job? = null

    @Volatile
    private var liveReady = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = ZedPrefs(this)
        hardware = HardwareController(this)
        whatsapp = WhatsAppController(this)
        screenCapture = ScreenCapture(this)
        ocr = OcrEngine()
        translator = OnDeviceTranslator()
        siren = SirenPlayer(this)
        speaker = ZedSpeaker(this).apply {
            init(prefs.language, prefs.bestVoice)
            onFinished = { resumeWake() }
        }
        executor = CommandExecutor(
            context = this,
            hardware = hardware,
            whatsapp = whatsapp,
            screen = screenCapture,
            ocr = ocr,
            translator = translator,
            scope = scope,
            speak = { message -> deliver(message) },
            startSiren = { siren.start() },
            stopSiren = { siren.stop() },
            setGuard = { enabled ->
                runCatching {
                    if (enabled) AntiTheftService.arm(this) else AntiTheftService.disarm(this)
                }.onFailure {
                    ZedState.log("GUARD", "Could not switch guard: ${it.message}")
                }
            }
        )
        createChannel()
        promoteToForeground()
        ZedState.setStatus("Standby")
        ZedState.log("SVC", "Zed core online")
        hardware.batteryPercent()

        if (prefs.wakeEnabled) {
            scope.launch {
                delay(700)
                startWake()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> beginConversation()
            ACTION_COMMAND -> intent.getStringExtra(EXTRA_TEXT)?.let { handleCommandText(it) }
            ACTION_SET_WAKE -> setWakeEnabled(intent.getBooleanExtra(EXTRA_ENABLED, true))
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = IntentCompat.getParcelableExtra(
                    intent, EXTRA_DATA, Intent::class.java
                )
                if (data != null) startProjection(resultCode, data)
            }
            ACTION_STOP_PROJECTION -> screenCapture.stop()
            ACTION_STOP_SPEAK -> stopSpeaking()
            ACTION_RELOAD -> reloadSettings()
            ACTION_STOP -> shutdown()
            ACTION_SIREN -> {
                if (intent.getBooleanExtra(EXTRA_ENABLED, true)) siren.start() else siren.stop()
            }
            else -> Unit
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------ listening

    private fun beginConversation() {
        if (ZedState.speaking.value) {
            stopSpeaking()
            return
        }
        wakeDetector?.pause()
        oneShot?.cancel()
        ZedState.partialText.value = ""
        ZedState.setStatus("Listening")
        val canLive = prefs.liveVoice && prefs.cloudBrain &&
            prefs.apiKey.isNotBlank() && ZedPermissions.hasMic(this)
        if (canLive) {
            startLiveSession()
        } else {
            startLocalListen()
        }
    }

    private fun startLocalListen() {
        if (!ZedPermissions.hasMic(this)) {
            deliver("I need microphone permission, sir.", speakIt = false)
            return
        }
        val recognizer = OneShotRecognizer(this).also { oneShot = it }
        recognizer.listen(
            language = prefs.language,
            onPartial = { ZedState.partialText.value = it },
            onFinal = { text ->
                ZedState.partialText.value = ""
                if (!text.isNullOrBlank()) {
                    handleCommandText(text)
                } else {
                    ZedState.setStatus("Standby")
                    resumeWake()
                }
            },
            onError = { message ->
                ZedState.setStatus("Standby")
                ZedState.addMessage(ZedState.Role.SYSTEM, message)
                if (prefs.speakReplies) speaker.speak(message)
                resumeWake()
            }
        )
    }

    private fun handleCommandText(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        ZedState.addMessage(ZedState.Role.USER, cleaned)
        ZedState.listening.value = false
        ZedState.setStatus("Thinking")

        val parsed = IntentParser.parse(cleaned)
        if (parsed.kind == ParsedCommand.Kind.UNKNOWN) {
            if (prefs.cloudBrain && prefs.apiKey.isNotBlank() && ZedPermissions.hasMic(this)) {
                pendingPrompt = cleaned
                startLiveSession()
            } else {
                deliver("I could not map that to an action, sir.", speakIt = true)
            }
            return
        }

        scope.launch {
            val reply = try {
                executor.execute(parsed)
            } catch (t: Throwable) {
                ZedState.log("CMD", "Execution failed: ${t.message}")
                "That action failed, sir."
            }
            ZedState.setStatus("Standby")
            if (reply.isNotBlank()) {
                deliver(reply)
            } else {
                resumeWake()
            }
        }
    }

    // --------------------------------------------------------- live session

    private fun startLiveSession() {
        if (gemini != null) {
            pendingPrompt?.let { prompt ->
                pendingPrompt = null
                gemini?.sendText(prompt)
            }
            return
        }
        if (prefs.apiKey.isBlank()) {
            startLocalListen()
            return
        }
        if (!ZedPermissions.hasMic(this)) {
            deliver("Microphone permission is required, sir.", speakIt = false)
            return
        }
        val client = GeminiLiveClient(
            apiKey = prefs.apiKey,
            model = prefs.model,
            systemPrompt = ZedPrompts.system(prefs.assistantName, prefs.jarvisMode),
            tools = ZedTools.declarations(),
            listener = liveListener
        )
        gemini = client
        liveReady = false
        liveTranscript.clear()
        player = PcmPlayer(24000).also { it.start() }
        mic = MicRecorder(16000) { chunk -> client.sendAudio(chunk) }
        ZedState.setStatus("Connecting to Gemini")
        ZedState.log("VOICE", "Opening live session")
        client.connect()
    }

    private val liveListener = object : GeminiLiveClient.Listener {
        override fun onReady() {
            liveReady = true
            ZedState.listening.value = true
            ZedState.setStatus("Live")
            mic?.start()
            ZedState.log("VOICE", "Live audio streaming")
            pendingPrompt?.let { prompt ->
                pendingPrompt = null
                gemini?.sendText(prompt)
            }
            resetIdle()
        }

        override fun onAudio(pcm: ByteArray) {
            player?.enqueue(pcm)
            resetIdle()
        }

        override fun onModelText(text: String) {
            liveTranscript.append(text)
            ZedState.partialText.value = liveTranscript.toString()
            resetIdle()
        }

        override fun onUserText(text: String) {
            ZedState.addMessage(ZedState.Role.USER, text)
            resetIdle()
        }

        override fun onTurnComplete() {
            val text = liveTranscript.toString().trim()
            liveTranscript.clear()
            ZedState.partialText.value = ""
            if (text.isNotBlank()) ZedState.addMessage(ZedState.Role.ZED, text)
            resetIdle()
        }

        override fun onToolCall(callId: String, name: String, args: JSONObject) {
            scope.launch {
                val result = try {
                    executor.runTool(name, args)
                } catch (t: Throwable) {
                    "failed: ${t.message}"
                }
                ZedState.log("TOOL", "$name -> ${result.take(120)}")
                gemini?.sendToolResponse(callId, name, JSONObject().put("result", result))
                resetIdle()
            }
        }

        override fun onError(message: String) {
            ZedState.log("VOICE", "Live error: $message")
            if (!liveReady) {
                closeLive()
                deliver("Live connection failed, sir. Falling back to offline control.", speakIt = false)
                startLocalListen()
            }
        }

        override fun onClosed() {
            closeLive()
        }
    }

    private fun resetIdle() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(LIVE_IDLE_TIMEOUT_MS)
            ZedState.log("VOICE", "Live session idle, closing")
            closeLive()
        }
    }

    private fun closeLive() {
        idleJob?.cancel()
        idleJob = null
        mic?.stop()
        mic = null
        player?.stop()
        player = null
        gemini?.close()
        gemini = null
        liveReady = false
        ZedState.listening.value = false
        ZedState.speaking.value = false
        ZedState.partialText.value = ""
        val text = liveTranscript.toString().trim()
        liveTranscript.clear()
        if (text.isNotBlank()) ZedState.addMessage(ZedState.Role.ZED, text)
        ZedState.setStatus("Standby")
        resumeWake()
    }

    // -------------------------------------------------------------- replies

    private fun deliver(text: String, speakIt: Boolean = true) {
        if (text.isBlank()) return
        ZedState.addMessage(ZedState.Role.ZED, text)
        ZedState.thinking.value = false
        val shouldSpeak = speakIt && prefs.speakReplies && gemini == null
        if (shouldSpeak) {
            val spoken = if (text.length > 900) text.take(900) + " ..." else text
            speaker.speak(spoken)
        } else {
            resumeWake()
        }
    }

    private fun stopSpeaking() {
        player?.stop()
        speaker.stop()
        ZedState.speaking.value = false
        ZedState.setStatus("Standby")
    }

    // ---------------------------------------------------------------- wake

    private fun startWake() {
        if (!ZedPermissions.hasMic(this) || !SpeechRecognizer.isRecognitionAvailable(this)) {
            ZedState.log("WAKE", "Wake word unavailable on this device")
            return
        }
        if (wakeDetector == null) {
            wakeDetector = WakeWordDetector(
                context = this,
                languageProvider = { prefs.language },
                phraseProvider = { prefs.wakePhrase },
                onWake = { beginConversation() }
            )
        }
        wakeDetector?.start()
    }

    private fun setWakeEnabled(enabled: Boolean) {
        prefs.wakeEnabled = enabled
        if (enabled) {
            startWake()
        } else {
            wakeDetector?.stop()
            ZedState.wakeActive.value = false
        }
    }

    private fun resumeWake() {
        if (!prefs.wakeEnabled || gemini != null) return
        scope.launch {
            delay(450)
            if (prefs.wakeEnabled && gemini == null) wakeDetector?.resume()
        }
    }

    // -------------------------------------------------------------- vision

    private fun startProjection(resultCode: Int, data: Intent) {
        promoteToForeground(withProjection = true)
        val started = screenCapture.start(resultCode, data)
        prefs.lastProjectionAt = System.currentTimeMillis()
        ZedState.log("VISION", if (started) "Screen vision ready" else "Screen vision failed")
    }

    // ------------------------------------------------------------ plumbing

    private fun reloadSettings() {
        speaker.shutdown()
        speaker.init(prefs.language, prefs.bestVoice)
        if (prefs.wakeEnabled) setWakeEnabled(true) else setWakeEnabled(false)
        ZedState.log("SVC", "Settings reloaded")
    }

    private fun promoteToForeground(withProjection: Boolean = false) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification)
            return
        }
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (withProjection) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        try {
            startForeground(NOTIF_ID, notification, types)
        } catch (t: Throwable) {
            ZedState.log("SVC", "Foreground start fallback: ${t.message}")
            runCatching { startForeground(NOTIF_ID, notification) }
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Zed is on standby")
        .setContentText("Say \"${prefs.wakePhrase}\" or tap Listen")
        .setSmallIcon(R.drawable.ic_zed_notification)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(openAppIntent())
        .addAction(R.drawable.ic_zed_notification, "Listen", serviceAction(ACTION_LISTEN))
        .addAction(R.drawable.ic_zed_notification, "Stop", serviceAction(ACTION_STOP))
        .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 21, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceAction(action: String): PendingIntent {
        val intent = Intent(this, ZedCoreService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Zed assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the assistant listening for the wake word"
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun shutdown() {
        closeLive()
        wakeDetector?.stop()
        oneShot?.cancel()
        speaker.shutdown()
        executor.stopAll()
        screenCapture.stop()
        siren.stop()
        ZedState.resetRuntime()
        ZedState.setStatus("Offline")
        ZedState.log("SVC", "Zed core stopped")
        isRunning = false
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        closeLive()
        wakeDetector?.stop()
        oneShot?.cancel()
        speaker.shutdown()
        executor.stopAll()
        screenCapture.stop()
        siren.stop()
        ocr.close()
        translator.close()
        ZedState.resetRuntime()
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_LISTEN = "dev.zed.assistant.action.LISTEN"
        const val ACTION_COMMAND = "dev.zed.assistant.action.COMMAND"
        const val ACTION_SET_WAKE = "dev.zed.assistant.action.SET_WAKE"
        const val ACTION_START_PROJECTION = "dev.zed.assistant.action.START_PROJECTION"
        const val ACTION_STOP_PROJECTION = "dev.zed.assistant.action.STOP_PROJECTION"
        const val ACTION_STOP_SPEAK = "dev.zed.assistant.action.STOP_SPEAK"
        const val ACTION_RELOAD = "dev.zed.assistant.action.RELOAD"
        const val ACTION_STOP = "dev.zed.assistant.action.STOP"
        const val ACTION_SIREN = "dev.zed.assistant.action.SIREN"

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ENABLED = "extra_enabled"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        const val CHANNEL_ID = "zed_core"
        const val NOTIF_ID = 4200
        private const val LIVE_IDLE_TIMEOUT_MS = 35_000L

        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun listen(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java).setAction(ACTION_LISTEN)
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendCommand(context: Context, text: String) {
            val intent = Intent(context, ZedCoreService::class.java)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(context, intent)
        }

        fun setWake(context: Context, enabled: Boolean) {
            val intent = Intent(context, ZedCoreService::class.java)
                .setAction(ACTION_SET_WAKE)
                .putExtra(EXTRA_ENABLED, enabled)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startProjection(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ZedCoreService::class.java)
                .setAction(ACTION_START_PROJECTION)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopProjection(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java).setAction(ACTION_STOP_PROJECTION)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopSpeaking(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java).setAction(ACTION_STOP_SPEAK)
            ContextCompat.startForegroundService(context, intent)
        }

        fun siren(context: Context, on: Boolean) {
            val intent = Intent(context, ZedCoreService::class.java)
                .setAction(ACTION_SIREN)
                .putExtra(EXTRA_ENABLED, on)
            ContextCompat.startForegroundService(context, intent)
        }

        fun reload(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java).setAction(ACTION_RELOAD)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
