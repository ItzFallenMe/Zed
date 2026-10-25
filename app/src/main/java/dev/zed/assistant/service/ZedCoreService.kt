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
import android.os.SystemClock
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.zed.assistant.MainActivity
import dev.zed.assistant.R
import dev.zed.assistant.command.CommandExecutor
import dev.zed.assistant.command.GeminiBrain
import dev.zed.assistant.command.IntentParser
import dev.zed.assistant.command.ParsedCommand
import dev.zed.assistant.command.ZedPrompts
import dev.zed.assistant.command.ZedTools
import dev.zed.assistant.core.CreatorGate
import dev.zed.assistant.core.CrashLog
import dev.zed.assistant.core.MemoryStore
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.device.HardwareController
import dev.zed.assistant.security.AntiTheftService
import dev.zed.assistant.security.SirenPlayer
import dev.zed.assistant.ui.FloatingAssistant
import dev.zed.assistant.ui.OverlayAction
import dev.zed.assistant.vision.OcrEngine
import dev.zed.assistant.vision.OnDeviceTranslator
import dev.zed.assistant.vision.ScreenCapture
import dev.zed.assistant.vision.ScreenStreamer
import dev.zed.assistant.voice.GeminiLiveClient
import dev.zed.assistant.voice.MicRecorder
import dev.zed.assistant.voice.OneShotRecognizer
import dev.zed.assistant.voice.PcmPlayer
import dev.zed.assistant.voice.VoiceProfileStore
import dev.zed.assistant.voice.VoiceSampler
import dev.zed.assistant.voice.Voiceprint
import dev.zed.assistant.voice.WakePhraseMatcher
import dev.zed.assistant.voice.WakeWordDetector
import dev.zed.assistant.voice.ZedSpeaker
import dev.zed.assistant.whatsapp.WhatsAppController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * A failing background task must not take the process down: it is logged, and Zed keeps
     * running. Uncaught coroutine exceptions on the main dispatcher are fatal otherwise.
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        ZedState.log("SVC", "Background task failed: ${error.message}")
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler
    )

    private lateinit var prefs: ZedPrefs
    private lateinit var hardware: HardwareController
    private lateinit var whatsapp: WhatsAppController
    private lateinit var screenCapture: ScreenCapture
    private lateinit var screenStreamer: ScreenStreamer
    private lateinit var ocr: OcrEngine
    private lateinit var translator: OnDeviceTranslator
    private lateinit var memory: MemoryStore
    private lateinit var speaker: ZedSpeaker
    private lateinit var voiceProfiles: VoiceProfileStore
    private lateinit var executor: CommandExecutor
    private lateinit var siren: SirenPlayer

    private var wakeDetector: WakeWordDetector? = null
    private var oneShot: OneShotRecognizer? = null
    private var gemini: GeminiLiveClient? = null
    private var textBrain: GeminiBrain? = null
    private var mic: MicRecorder? = null
    private var player: PcmPlayer? = null

    private val liveTranscript = StringBuilder()
    private var pendingPrompt: String? = null
    private var overlayWarned = false
    private var idleJob: Job? = null
    private var wakeResumeJob: Job? = null
    private var floating: FloatingAssistant? = null

    @Volatile
    private var liveReady = false

    /** False until the live session has heard a real request, not just the wake word. */
    @Volatile
    private var heardUserRequest = false

    private var liveReadyAt = 0L

    private val speakerSamples = ShortArray(Voiceprint.SAMPLE_RATE * 2)

    @Volatile
    private var speakerSampleCount = 0

    @Volatile
    private var speakerChecked = false

    /** How many chat messages have already been folded into long-term memory. */
    private var lastSummarizedAt = 0

    private var awaitingCreatorCode = false
    private var creatorAttempts = 0

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = ZedPrefs(this)
        hardware = HardwareController(this)
        whatsapp = WhatsAppController(this)
        screenCapture = ScreenCapture(this)
        screenStreamer = ScreenStreamer(screenCapture, scope) { jpeg, _, _ ->
            gemini?.sendVideoFrame(jpeg)
        }
        ocr = OcrEngine()
        translator = OnDeviceTranslator()
        memory = MemoryStore(this) { prefs.apiKey }
        voiceProfiles = VoiceProfileStore(this)
        siren = SirenPlayer(this)
        speaker = ZedSpeaker(this).apply {
            init(prefs.language, prefs.bestVoice, prefs.ttsVoiceName)
            onFinished = { resumeWake() }
        }
        executor = CommandExecutor(
            context = this,
            hardware = hardware,
            whatsapp = whatsapp,
            screen = screenCapture,
            ocr = ocr,
            translator = translator,
            memory = memory,
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
        if (!promoteToForeground()) {
            // Never run un-promoted: the system kills such a service and blames the app.
            ZedState.log("SVC", "Could not become a foreground service; stopping cleanly")
            isRunning = false
            stopSelf()
            return
        }
        CrashLog.last(this)?.let { ZedState.log("CRASH", "Previous run ended with: $it") }
        ZedState.setStatus("Standby")
        ZedState.creatorVerified.value = prefs.creatorVerified
        ZedState.log("SVC", "Zed core online")
        if (ZedState.messages.value.isEmpty()) {
            ZedState.addMessage(
                ZedState.Role.ZED,
                "Online. All systems up."
            )
        }
        hardware.batteryPercent()

        if (prefs.floatingBubble && ZedPermissions.canDrawOverlays(this)) {
            scope.launch {
                delay(500)
                showBubble()
            }
        }

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
            ACTION_ENROLL_VOICE -> enrollVoice(intent.getStringExtra(EXTRA_NAME).orEmpty())
            ACTION_RELOAD -> reloadSettings()
            ACTION_STOP -> shutdown()
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
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
        // One session at a time. Repeated taps, a wake word and a bubble tap arriving
        // together used to open several microphones and fight over the recogniser.
        if (ZedState.listening.value) {
            ZedState.log("VOICE", "Already listening; ignoring duplicate start")
            return
        }
        wakeDetector?.pause()
        oneShot?.cancel()
        ZedState.partialText.value = ""
        ZedState.setStatus("Listening")
        // The overlay is the interface: it unfolds the moment Zed is awake, even when the
        // app itself is not on screen.
        if (!prefs.floatingBubble) {
            ZedState.overlayBlocked.value = false
        } else if (ZedPermissions.canDrawOverlays(this)) {
            if (floating == null) showBubble()
            floating?.expand()
        } else {
            warnOverlayOnce()
        }
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
            deliver("I need microphone permission for that.", speakIt = false)
            return
        }
        val recognizer = OneShotRecognizer(this).also { oneShot = it }
        recognizer.listen(
            language = prefs.language,
            onPartial = { ZedState.partialText.value = it },
            onFinal = { text ->
                ZedState.partialText.value = ""
                if (!text.isNullOrBlank()) {
                    handleCommandText(text, fromVoice = true)
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

    private fun handleCommandText(text: String, fromVoice: Boolean = false) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        // "zed" on its own is a wake, not a request. Sending the bare name to a model is
        // exactly what produced the "Yes sir?" reply on every wake.
        val request = WakePhraseMatcher.stripWake(cleaned, prefs.wakePhrase)
        if (request.isBlank()) {
            ZedState.log("VOICE", "Heard only the wake word; waiting for a request")
            ZedState.setStatus("Listening")
            resumeWake()
            return
        }
        ZedState.addMessage(ZedState.Role.USER, request)
        ZedState.listening.value = false
        ZedState.setStatus("Thinking")
        if (handleCreatorGate(cleaned)) return

        val parsed = IntentParser.parse(cleaned)
        if (parsed.kind == ParsedCommand.Kind.UNKNOWN) {
            routeToBrain(cleaned, fromVoice)
            return
        }

        scope.launch {
            val reply = try {
                executor.execute(parsed)
            } catch (t: Throwable) {
                ZedState.log("CMD", "Execution failed: ${t.message}")
                "That action failed."
            }
            ZedState.setStatus("Standby")
            if (reply.isNotBlank()) {
                deliver(reply)
            } else {
                resumeWake()
            }
        }
    }

    /**
     * Anything the fast on-device parser cannot map becomes a conversation. Spoken input goes
     * to the realtime session so the reply arrives as a voice; typed input goes to Gemini,
     * which can chain several tool calls before answering.
     */
    private fun routeToBrain(text: String, fromVoice: Boolean) {
        val hasKey = prefs.apiKey.isNotBlank()
        val canLive = prefs.liveVoice && prefs.cloudBrain && hasKey && ZedPermissions.hasMic(this)
        when {
            fromVoice && canLive -> {
                pendingPrompt = text
                startLiveSession()
            }
            prefs.cloudBrain && hasKey -> respondWithGemini(text)
            canLive -> {
                pendingPrompt = text
                startLiveSession()
            }
            else -> deliver(
                "I need a Gemini key in Settings before I can think about that.",
                speakIt = true
            )
        }
    }

    private fun respondWithGemini(text: String) {
        val brain = textBrain ?: GeminiBrain(
            apiKey = prefs.apiKey,
            model = prefs.textModel,
            systemPrompt = buildPrompt(),
            tools = ZedTools.declarations(),
            executeTool = { name, args -> executor.runTool(name, args) }
        ).also { textBrain = it }

        ZedState.thinking.value = true
        scope.launch {
            val history = ZedState.messages.value
                .dropLast(1)
                .takeLast(12)
                .map { it.role.name.lowercase() to it.text }
            val outcome = try {
                brain.reply(history, text)
            } catch (t: Throwable) {
                ZedState.log("GEMINI", "Turn failed: ${t.message}")
                GeminiBrain.Outcome(error = "network: ${t.message}")
            }
            ZedState.thinking.value = false
            ZedState.setStatus("Standby")
            if (outcome.text.isBlank()) {
                ZedState.log("GEMINI", "No answer: ${outcome.error}")
                deliver(brainTrouble(outcome.error), speakIt = true)
            } else {
                deliver(outcome.text)
            }
            maybeSummarize()
        }
    }

    /** Turns a brain failure into one honest sentence, without pretending it was the network. */
    private fun brainTrouble(error: String?): String = when {
        error == null -> "I couldn't put that into words. Try me again?"
        error.startsWith("key:") ->
            "Google rejected the key. Open Settings and paste a fresh one."
        error.startsWith("model:") ->
            "That Gemini model is not on your key. Check the model in Settings."
        error.startsWith("rate:") -> "I'm being rate-limited. Give me a minute."
        error.startsWith("server:") -> "Google is having a moment. Try again shortly."
        error.startsWith("network:") -> "I can't reach Google. Check the connection."
        else -> "That failed: ${error.removePrefix("http: ")}."
    }

    // ------------------------------------------------------- voice identity

    /** Buffers the opening moments of a session, then quietly works out who is talking. */
    private fun collectVoiceSample(chunk: ByteArray) {
        if (!prefs.voiceIdEnabled || speakerChecked) return
        val shorts = VoiceSampler.toShorts(chunk)
        val count = speakerSampleCount
        if (count + shorts.size > speakerSamples.size) {
            speakerSampleCount = speakerSamples.size
            return
        }
        System.arraycopy(shorts, 0, speakerSamples, count, shorts.size)
        speakerSampleCount = count + shorts.size
        if (speakerSampleCount >= Voiceprint.SAMPLE_RATE * 3 / 2) {
            speakerChecked = true
            val sample = speakerSamples.copyOf(speakerSampleCount)
            scope.launch(Dispatchers.Default) { identifySpeaker(sample) }
        }
    }

    private fun identifySpeaker(sample: ShortArray) {
        val match = voiceProfiles.identify(sample)
        if (match == null) {
            ZedState.log("VOICE", "No enrolled voice matched")
            return
        }
        val (name, score) = match
        prefs.lastSpeaker = name
        ZedState.speaker.value = name
        ZedState.log("VOICE", "Recognised $name (${String.format("%.2f", score)})")
        gemini?.sendText(
            "The person speaking right now is $name. Greet them by name and carry on " +
                "as if it is obviously them."
        )
    }

    private fun enrollVoice(name: String) {
        val clean = name.trim().ifBlank { prefs.userName }
        if (clean.isBlank()) {
            deliver("Tell me your name first, then I'll learn your voice.", speakIt = false)
            return
        }
        if (!ZedPermissions.hasMic(this)) {
            deliver("I need the microphone before I can learn a voice.", speakIt = false)
            return
        }
        ZedState.enrolling.value = true
        ZedState.setStatus("Learning your voice")
        // The sample recorder needs the microphone to itself.
        wakeDetector?.pause()
        scope.launch {
            val samples = withContext(Dispatchers.IO) { VoiceSampler.record(4000) }
            val stored = samples != null && samples.size > Voiceprint.SAMPLE_RATE / 2 &&
                voiceProfiles.enroll(clean, samples)
            ZedState.enrolling.value = false
            ZedState.setStatus("Standby")
            if (stored) {
                prefs.userName = clean
                prefs.lastSpeaker = clean
                ZedState.speaker.value = clean
                ZedState.log("VOICE", "Enrolled voice for $clean")
                deliver("Got it. I'll know it's you from now on.", speakIt = true)
            } else {
                deliver("I could not catch your voice. Try again somewhere quieter.", speakIt = true)
            }
        }
    }

    private fun buildPrompt(): String = ZedPrompts.system(
        memories = memory.promptBlock(),
        userName = prefs.userName,
        speaker = prefs.lastSpeaker,
        creatorVerified = prefs.creatorVerified
    )

    // ------------------------------------------------------------ creator gate

    /**
     * The creator check runs before any brain: the app verifies the code, never the model.
     * Returns true when the message was consumed by the gate.
     */
    private fun handleCreatorGate(text: String): Boolean {
        if (prefs.creatorVerified) {
            if (CreatorGate.isClaim(text)) {
                deliver(
                    "I know exactly who you are. You don't need the code with me anymore.",
                    speakIt = true
                )
                return true
            }
            return false
        }
        if (awaitingCreatorCode) {
            if (CreatorGate.matchesCode(text)) {
                verifyCreator()
            } else {
                awaitingCreatorCode = false
                creatorAttempts++
                deliver(
                    if (creatorAttempts >= CreatorGate.MAX_ATTEMPTS) {
                        "Let's leave it there. I'm not convinced."
                    } else {
                        "That's not it. I'll pretend that didn't happen."
                    },
                    speakIt = true
                )
            }
            return true
        }
        if (CreatorGate.isClaim(text)) {
            awaitingCreatorCode = true
            deliver("Then prove it. What's the identity code?", speakIt = true)
            return true
        }
        if (CreatorGate.matchesCode(text)) {
            verifyCreator()
            return true
        }
        return false
    }

    private fun verifyCreator() {
        awaitingCreatorCode = false
        creatorAttempts = 0
        prefs.creatorVerified = true
        val name = prefs.userName.ifBlank { prefs.lastSpeaker }
        prefs.creatorName = name
        ZedState.creatorVerified.value = true
        ZedState.log("CREATOR", "Identity code verified")
        val line = if (name.isBlank()) {
            "My maker is the person using this phone — verified with the identity code."
        } else {
            "My maker is $name — verified with the identity code."
        }
        scope.launch {
            memory.remember(line)
            memory.addEpisode("My creator proved who they are and I will remember them.")
        }
        deliver(
            if (name.isBlank()) "Okay. I know exactly who you are now, and I won't forget it."
            else "Okay, $name. I know exactly who you are now, and I won't forget it.",
            speakIt = true
        )
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
            deliver("Microphone permission is required for that.", speakIt = false)
            return
        }
        val client = GeminiLiveClient(
            apiKey = prefs.apiKey,
            model = prefs.model,
            systemPrompt = buildPrompt(),
            voiceName = prefs.liveVoiceName,
            tools = ZedTools.declarations(),
            listener = liveListener
        )
        gemini = client
        liveReady = false
        liveTranscript.clear()
        speakerSampleCount = 0
        speakerChecked = false
        ZedState.speaker.value = prefs.lastSpeaker
        player = PcmPlayer(24000).also { pcm ->
            pcm.onDrained = {
                ZedState.speaking.value = false
                if (gemini != null) ZedState.setStatus("Live")
            }
            pcm.start()
        }
        ZedState.speaking.value = false
        mic = MicRecorder(this, 16000) { chunk ->
            client.sendAudio(chunk)
            collectVoiceSample(chunk)
        }
        ZedState.setStatus("Connecting to Gemini")
        ZedState.log("VOICE", "Opening live session")
        client.connect()
    }

    private val liveListener = object : GeminiLiveClient.Listener {
        override fun onReady() {
            liveReady = true
            heardUserRequest = false
            liveReadyAt = SystemClock.elapsedRealtime()
            ZedState.listening.value = true
            ZedState.setStatus("Live")
            mic?.start()
            ZedState.log("VOICE", "Live audio streaming")
            if (prefs.screenStream && screenCapture.isRunning) screenStreamer.start()
            pendingPrompt?.let { prompt ->
                pendingPrompt = null
                gemini?.sendText(prompt)
            }
            resetIdle()
        }

        override fun onAudio(pcm: ByteArray) {
            // A bare wake word is not a request. Drop the model's reflexive opening line
            // if nothing has been asked yet, so waking Zed is silent.
            if (!heardUserRequest &&
                SystemClock.elapsedRealtime() - liveReadyAt < WAKE_QUIET_WINDOW_MS
            ) {
                return
            }
            player?.enqueue(pcm)
            resetIdle()
        }

        override fun onModelText(text: String) {
            liveTranscript.append(text)
            ZedState.partialText.value = liveTranscript.toString()
            resetIdle()
        }

        override fun onUserText(text: String) {
            val request = WakePhraseMatcher.stripWake(text, prefs.wakePhrase)
            if (request.isBlank()) {
                ZedState.log("VOICE", "Only the wake word was heard; waiting")
                return
            }
            heardUserRequest = true
            ZedState.addMessage(ZedState.Role.USER, request)
            val gateText = request
            if (!prefs.creatorVerified) {
                when {
                    awaitingCreatorCode && CreatorGate.matchesCode(gateText) -> {
                        verifyCreator()
                        gemini?.sendText(
                            "The app has verified this person is your creator. Acknowledge them " +
                                "briefly, in character. Never mention the code."
                        )
                    }
                    CreatorGate.isClaim(gateText) -> {
                        awaitingCreatorCode = true
                        gemini?.sendText(
                            "They just claimed to be your creator. Ask them for the identity " +
                                "code in one short line. Never say the code, never hint at it."
                        )
                    }
                    CreatorGate.matchesCode(gateText) -> verifyCreator()
                    else -> Unit
                }
            }
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
                deliver("Live connection failed. Falling back to offline control.", speakIt = false)
                startLocalListen()
            } else {
                // The socket died mid-conversation. Close it and give the microphone back
                // to the wake word instead of streaming into a dead connection.
                closeLive()
            }
        }

        override fun onInterrupted() {
            // Barge-in: drop the rest of the answer that is still queued.
            player?.clear()
            ZedState.speaking.value = false
            liveTranscript.clear()
            ZedState.partialText.value = ""
            ZedState.setStatus("Live")
            resetIdle()
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
        screenStreamer.stop()
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
        maybeSummarize()
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

    /**
     * Folds the newest stretch of conversation into long-term memory. Nothing is uploaded
     * beyond the summary request itself, and only after a real exchange has happened.
     */
    private fun maybeSummarize() {
        val messages = ZedState.messages.value
        if (messages.size - lastSummarizedAt < MIN_MESSAGES_TO_SUMMARISE) return
        val slice = messages.takeLast(18).map { message ->
            (if (message.role == ZedState.Role.USER) "user" else "zed") to message.text
        }
        if (slice.none { it.first == "user" }) return
        lastSummarizedAt = messages.size
        scope.launch {
            val stored = memory.ingest(slice, prefs.apiKey)
            if (stored > 0) {
                ZedState.log("MEMORY", "Long-term memory updated ($stored item(s))")
            }
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
                isBusy = {
                    ZedState.speaking.value || ZedState.listening.value || gemini != null
                },
                onWake = { beginConversation() }
            )
        }
        wakeDetector?.start()
    }

    private fun setWakeEnabled(enabled: Boolean) {
        prefs.wakeEnabled = enabled
        if (enabled) {
            if (wakeDetector?.isRunning == true) wakeDetector?.resume() else startWake()
        } else {
            wakeResumeJob?.cancel()
            wakeDetector?.stop()
            ZedState.wakeActive.value = false
        }
    }

    /**
     * Hands the microphone back to the wake word, but only once Zed has actually stopped
     * talking. Arming the recogniser while speech is still coming out of the speaker made
     * Zed wake itself, answer itself, and flip the mic on and off forever.
     */
    private fun resumeWake() {
        if (!prefs.wakeEnabled || gemini != null) return
        wakeResumeJob?.cancel()
        wakeResumeJob = scope.launch {
            var waited = 0L
            while ((ZedState.speaking.value || ZedState.listening.value) && waited < 15_000L) {
                delay(200)
                waited += 200
            }
            delay(450)
            if (prefs.wakeEnabled && gemini == null && !ZedState.speaking.value) {
                wakeDetector?.resume()
            }
        }
    }

    // -------------------------------------------------------------- vision

    private fun showBubble() {
        if (!ZedPermissions.canDrawOverlays(this)) {
            ZedState.log("BUBBLE", "Overlay permission missing; the menu cannot float yet")
            ZedState.overlayBlocked.value = true
            warnOverlayOnce()
            return
        }
        if (floating == null) {
            floating = FloatingAssistant(
                context = this,
                onTap = { beginConversation() },
                onStop = { stopSpeaking() },
                onAction = { action -> handleOverlayAction(action) }
            )
        }
        floating?.show()
        prefs.floatingBubble = true
        ZedState.overlayBlocked.value = floating?.isShowing != true
        ZedState.log(
            "BUBBLE",
            if (floating?.isShowing == true) "Floating assistant shown"
            else "Floating assistant could not be added"
        )
    }

    /** Tells the owner, once per run, why no menu appeared. Silence looked like a bug. */
    private fun warnOverlayOnce() {
        ZedState.overlayBlocked.value = true
        if (overlayWarned) return
        overlayWarned = true
        ZedState.log("BUBBLE", "Overlay permission missing")
        ZedState.addMessage(
            ZedState.Role.SYSTEM,
            "Floating menu needs \"Display over other apps\" — enable it under Access."
        )
    }

    /** One-tap actions from the floating menu. They never need the app open. */
    private fun handleOverlayAction(action: OverlayAction) {
        when (action) {
            OverlayAction.TORCH -> {
                val on = !hardware.torchState()
                if (hardware.setTorch(on)) {
                    prefs.flashlightOn = on
                    ZedState.log("HUD", if (on) "Torch on" else "Torch off")
                } else {
                    ZedState.log("HUD", "No torch on this device")
                }
            }
            OverlayAction.GUARD -> {
                if (prefs.antiTheftArmed) {
                    AntiTheftService.disarm(this)
                    ZedState.log("HUD", "Guard disarmed")
                } else {
                    AntiTheftService.arm(this)
                    ZedState.log("HUD", "Guard armed")
                }
            }
            OverlayAction.SCREEN, OverlayAction.APP -> {
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                }.onFailure { ZedState.log("HUD", "Could not open the app: ${it.message}") }
            }
        }
    }

    private fun hideBubble() {
        floating?.hide()
        floating = null
        prefs.floatingBubble = false
        ZedState.log("BUBBLE", "Floating assistant hidden")
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        promoteToForeground(withProjection = true)
        val started = screenCapture.start(resultCode, data)
        prefs.lastProjectionAt = System.currentTimeMillis()
        ZedState.log("VISION", if (started) "Screen vision ready" else "Screen vision failed")
        // Capture consent covers the whole session: start feeding the live conversation
        // immediately instead of asking the user to share again.
        if (started && gemini != null && prefs.screenStream) screenStreamer.start()
    }

    // ------------------------------------------------------------ plumbing

    private fun reloadSettings() {
        textBrain = null
        speaker.shutdown()
        speaker.init(prefs.language, prefs.bestVoice, prefs.ttsVoiceName)
        // Re-arm rather than restart: a second detector would fight the first for the mic.
        setWakeEnabled(prefs.wakeEnabled)
        ZedState.log("SVC", "Settings reloaded")
    }

    private fun promoteToForeground(withProjection: Boolean = false): Boolean {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching { startForeground(NOTIF_ID, notification) }.isSuccess
        }
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (withProjection) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        try {
            startForeground(NOTIF_ID, notification, types)
            return true
        } catch (t: Throwable) {
            ZedState.log("SVC", "Foreground start refused (${t.message}); retrying narrower")
        }
        if (withProjection && runCatching {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            }.isSuccess
        ) {
            return true
        }
        return runCatching { startForeground(NOTIF_ID, notification) }.isSuccess
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
        screenStreamer.stop()
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
        floating?.hide()
        floating = null
        closeLive()
        wakeDetector?.stop()
        oneShot?.cancel()
        speaker.shutdown()
        executor.stopAll()
        screenStreamer.stop()
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
        const val ACTION_ENROLL_VOICE = "dev.zed.assistant.action.ENROLL_VOICE"
        const val ACTION_SHOW_BUBBLE = "dev.zed.assistant.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "dev.zed.assistant.action.HIDE_BUBBLE"

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ENABLED = "extra_enabled"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        const val CHANNEL_ID = "zed_core"
        const val NOTIF_ID = 4200
        private const val LIVE_IDLE_TIMEOUT_MS = 35_000L

        /** Silent window after connecting: no greeting just because the name was said. */
        private const val WAKE_QUIET_WINDOW_MS = 1_600L
        private const val MIN_MESSAGES_TO_SUMMARISE = 6

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

        fun enrollVoice(context: Context, name: String) {
            val intent = Intent(context, ZedCoreService::class.java)
                .setAction(ACTION_ENROLL_VOICE)
                .putExtra(EXTRA_NAME, name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun reload(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java).setAction(ACTION_RELOAD)
            ContextCompat.startForegroundService(context, intent)
        }

        fun setBubble(context: Context, enabled: Boolean) {
            val intent = Intent(context, ZedCoreService::class.java)
                .setAction(if (enabled) ACTION_SHOW_BUBBLE else ACTION_HIDE_BUBBLE)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZedCoreService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
