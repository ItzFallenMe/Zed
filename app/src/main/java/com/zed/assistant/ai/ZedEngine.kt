package com.zed.assistant.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.zed.assistant.BuildConfig
import com.zed.assistant.ZedSessionService
import com.zed.assistant.ZedWakeWordService
import com.zed.assistant.automation.AgentTaskEngine
import com.zed.assistant.automation.ZedAccessibilityService
import com.zed.assistant.audio.CreatorChallengeSession
import com.zed.assistant.audio.VoskModelManager
import com.zed.assistant.data.AppIntegrity
import com.zed.assistant.data.ChatHistoryStore
import com.zed.assistant.data.CreatorAuth
import com.zed.assistant.data.CreatorGate
import com.zed.assistant.data.MemoryStore
import com.zed.assistant.data.WeatherRepository
import com.zed.assistant.data.ZedPreferences
import com.zed.assistant.data.ZedVoiceProfiles
import com.zed.assistant.model.AssistantState
import com.zed.assistant.model.ChatMessage
import com.zed.assistant.overlay.ZedOverlayService
import com.zed.assistant.screen.ScreenCaptureService
import com.zed.assistant.ui.theme.ZedThemeMode
import com.zed.assistant.ui.theme.ZedThemeRepository
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** UI state for the local creator-code gate. */
data class CreatorPrompt(
  val listening: Boolean = false,
  val voice: Boolean = false,
  val error: String? = null,
  val lockedSeconds: Long = 0L
)

/**
 * Single owner of a Zed conversation: permission handling, the Live session,
 * microphone streaming, playback state and tool execution.
 *
 * Application-scoped by design. Every Context retained here is an application
 * context, so the static lifetime cannot leak an Activity.
 */
@SuppressLint("StaticFieldLeak")
object ZedEngine {

  private const val TAG = "ZedEngine"

  const val PREFS = "zed_prefs"
  const val KEY_API_KEY = "api_key"
  const val KEY_MODEL = "gemini_model"
  const val KEY_VOICE = "gemini_voice"
  const val KEY_USER_NAME = "user_name"

  const val DEFAULT_MODEL = "gemini-3.8-live"
  const val DEFAULT_VOICE = "Aoede"

  val AVAILABLE_MODELS = listOf(
    "gemini-3.8-live",
    "gemini-3.8-live-extended-thinking",
    "gemini-3.1-flash-live-preview"
  )

  val AVAILABLE_VOICES = listOf("Aoede", "Kore", "Zephyr", "Leda", "Puck", "Charon", "Fenrir", "Orus")

  // Public UI state -------------------------------------------------------
  val state = MutableStateFlow(AssistantState.IDLE)
  val status = MutableStateFlow("Tap to talk")
  val userText = MutableStateFlow("")
  val zedText = MutableStateFlow("")
  val actionNote = MutableStateFlow<String?>(null)
  val errorMessage = MutableStateFlow<String?>(null)
  val isActive = MutableStateFlow(false)
  val level = MutableStateFlow(0f)
  val chatTurns = MutableStateFlow<List<ChatMessage>>(emptyList())

  /** Persisted master-control status, verified locally by [CreatorAuth]. */
  val creatorUnlocked = MutableStateFlow(false)
  val creatorPrompt = MutableStateFlow<CreatorPrompt?>(null)

  /**
   * Robot-avatar state: idle, thinking, listening, speaking, searching,
   * working, happy or error. The floating overlay and the home avatar render it.
   */
  val avatarState = MutableStateFlow("idle")

  // Internals -------------------------------------------------------------
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val toolScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val handler = Handler(Looper.getMainLooper())

  @Volatile
  private var appContext: Context? = null

  private var audioEngine: AudioEngine? = null
  private var actions: DeviceActions? = null
  private var client: GeminiLiveClient? = null

  private var preferences: ZedPreferences? = null
  private var history: ChatHistoryStore? = null
  private var memory: MemoryStore? = null
  private var weather: WeatherRepository? = null
  private var avatarResetJob: Job? = null
  private var screenFrameJob: Job? = null
  private var creatorChallenge: CreatorChallengeSession? = null

  @Volatile
  private var manualStop = false

  @Volatile
  private var pendingGoAwayReconnect = false

  @Volatile
  private var reconnectAttempts = 0

  @Volatile
  private var sessionHandle: String? = null

  @Volatile
  private var turnFinished = true

  @Volatile
  private var lastSubmittedText: String? = null

  private val queuedText = ConcurrentLinkedQueue<String>()

  @Synchronized
  fun initialize(context: Context) {
    if (appContext != null) return
    val app = context.applicationContext
    appContext = app

    val engine = AudioEngine(app)
    audioEngine = engine
    actions = DeviceActions(app, ContactsRepository(app))

    val storedPreferences = ZedPreferences(app)
    preferences = storedPreferences
    history = ChatHistoryStore(app)
    memory = MemoryStore(app)
    weather = WeatherRepository(app)
    creatorUnlocked.value = CreatorAuth.isUnlocked(app)

    scope.launch {
      engine.level.collect { level.value = it }
    }

    scope.launch {
      val saved = history?.recent(60).orEmpty()
      if (saved.isNotEmpty()) chatTurns.value = saved
    }

    if (storedPreferences.wakeWordEnabled) VoskModelManager.init(app, scope)

    engine.onMicChunk = { chunk -> client?.sendAudio(chunk) }

    engine.onPlaybackStarted = {
      if (isActive.value) {
        setState(AssistantState.SPEAKING)
        status.value = "Zed is talking…"
      }
    }

    engine.onPlaybackIdle = {
      if (isActive.value && turnFinished && state.value == AssistantState.SPEAKING) {
        setState(AssistantState.LISTENING)
        status.value = "Listening…"
        dispatchQueuedText()
      }
    }

    engine.onError = { message ->
      Log.e(TAG, "Audio engine error: $message")
      if (isActive.value) {
        errorMessage.value = message
      } else {
        errorMessage.value = message
        setState(AssistantState.ERROR)
      }
    }
  }

  fun toggle(context: Context) {
    if (isActive.value) stop() else start(context)
  }

  fun start(context: Context) {
    initialize(context)
    if (isActive.value) return
    if (creatorPrompt.value != null) {
      status.value = "Finish creator verification first"
      return
    }
    val app = appContext ?: return

    val apiKey = resolveApiKey()
    if (apiKey.isBlank()) {
      queuedText.clear()
      Log.w(TAG, "Cannot start: no Gemini API key configured")
      errorMessage.value = "Add your Gemini API key in Settings to wake me up."
      status.value = "API key needed"
      setState(AssistantState.ERROR)
      return
    }

    errorMessage.value = null
    actionNote.value = null
    userText.value = ""
    zedText.value = ""
    isActive.value = true
    manualStop = false
    pendingGoAwayReconnect = false
    reconnectAttempts = 0
    sessionHandle = null
    turnFinished = true
    lastSubmittedText = null
    setState(AssistantState.CONNECTING)
    status.value = "Connecting to Gemini Live…"

    audioEngine?.beginAudioSession()

    scope.launch {
      val granted = PermissionGateway.ensure(app, Manifest.permission.RECORD_AUDIO)
      if (!isActive.value) return@launch
      if (!granted) {
        queuedText.clear()
        Log.e(TAG, "Microphone permission denied")
        errorMessage.value = "Microphone permission is required for voice chat."
        teardown(keepError = true, stateAfter = AssistantState.ERROR)
        status.value = "Microphone blocked"
        return@launch
      }
      if (!ZedSessionService.start(app)) {
        Log.e(TAG, "Could not start the foreground session service")
        errorMessage.value = "Could not start the background voice session."
        teardown(keepError = true, stateAfter = AssistantState.ERROR)
        status.value = "Session error"
        return@launch
      }
      connect(apiKey, resume = false)
    }
  }

  fun stop() {
    Log.d(TAG, "Stopping session")
    manualStop = true
    avatarResetJob?.cancel()
    screenFrameJob?.cancel()
    screenFrameJob = null
    avatarState.value = "idle"
    pendingGoAwayReconnect = false
    handler.removeCallbacksAndMessages(null)
    teardown(keepError = false, stateAfter = AssistantState.IDLE)
    status.value = "Tap to talk"
    userText.value = ""
    zedText.value = ""
    actionNote.value = null
    queuedText.clear()
    lastSubmittedText = null
  }

  /** Start a session if needed, then send a typed turn over Gemini Live. */
  fun sendText(context: Context, text: String) {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return

    // Creator verification is handled entirely on-device. The challenge phrase
    // and the code are never forwarded to Gemini.
    if (creatorPrompt.value != null) {
      if (CreatorGate.normalizeCodeInput(trimmed) != null) {
        verifyCreatorCode(trimmed)
      } else {
        status.value = "Finish creator verification first"
      }
      return
    }
    if (!creatorUnlocked.value && CreatorGate.isCreatorRequest(trimmed)) {
      beginCreatorChallenge(voice = false)
      return
    }

    if (queuedText.size >= 20) {
      errorMessage.value = "Too many messages are queued. Wait for Zed to finish first."
      return
    }
    queuedText.offer(trimmed)

    if (!isActive.value) {
      start(context)
      return
    }

    if (state.value == AssistantState.SPEAKING) interrupt()
    dispatchQueuedText()
  }

  fun clearConversation() {
    chatTurns.value = emptyList()
    userText.value = ""
    zedText.value = ""
    history?.clear()
  }

  /** Long-press / explicit "stop talking" action. */
  fun interrupt() {
    Log.d(TAG, "User interrupted Zed")
    client?.muteCurrentTurnAudio()
    audioEngine?.interruptPlayback()
    turnFinished = true
    if (isActive.value) {
      setState(AssistantState.LISTENING)
      status.value = "Listening…"
    }
  }

  fun dismissError() {
    errorMessage.value = null
    if (!isActive.value && state.value == AssistantState.ERROR) {
      avatarResetJob?.cancel()
      setState(AssistantState.IDLE)
      status.value = "Tap to talk"
    }
  }

  /** Uses the exact same AudioTrack graph as Gemini's voice. */
  fun testSpeaker(context: Context) {
    initialize(context)
    audioEngine?.beginAudioSession()
    audioEngine?.startPlayback()
    audioEngine?.playTestTone()
    Log.d(TAG, "Speaker diagnostic triggered")
    handler.postDelayed({
      if (!isActive.value) audioEngine?.endAudioSession()
    }, 1_500)
  }

  fun hasApiKey(context: Context): Boolean =
    (preferences?.apiKey ?: ZedPreferences(context).apiKey).isNotBlank() ||
      BuildConfig.GEMINI_API_KEY.isNotBlank()

  // ------------------------------------------------------------- connection

  private fun connect(apiKey: String, resume: Boolean) {
    if (!isActive.value) return
    val app = appContext ?: return
    val stored = preferences ?: ZedPreferences(app).also { preferences = it }
    val model = stored.model.ifBlank { DEFAULT_MODEL }
    val voiceProfile = ZedVoiceProfiles.resolve(stored.voicePreset, stored.voice)
    val voice = voiceProfile.geminiVoice.ifBlank { DEFAULT_VOICE }
    val userName = stored.userName
    val remembered = memory?.promptContext().orEmpty()
    val handle = if (resume) sessionHandle else null

    client?.close()
    val newClient = GeminiLiveClient(
      apiKey = apiKey,
      model = model,
      systemInstruction = ZedPersona.systemInstruction(
        userName = userName,
        memories = remembered,
        isCreator = creatorUnlocked.value,
        voiceStyle = voiceProfile.style
      ),
      voiceName = voice,
      sessionHandle = handle,
      listener = listener
    )
    client = newClient
    setState(AssistantState.CONNECTING)
    status.value = if (resume) "Reconnecting…" else "Connecting to Gemini Live…"
    Log.d(TAG, "Connecting (model=$model, voice=$voice, resume=${!handle.isNullOrBlank()})")
    newClient.connect()
  }

  private fun resumeSession() {
    val apiKey = resolveApiKey()

    if (sessionHandle.isNullOrBlank() || apiKey.isBlank()) {
      errorMessage.value = "The Gemini session ended. Tap to reconnect."
      teardown(keepError = true, stateAfter = AssistantState.ERROR)
      return
    }

    reconnectAttempts++
    if (reconnectAttempts > 2) {
      Log.e(TAG, "Too many resume attempts; giving up")
      errorMessage.value = "Could not resume the Gemini session. Tap to reconnect."
      teardown(keepError = true, stateAfter = AssistantState.ERROR)
      return
    }

    Log.d(TAG, "Resuming Live session (attempt $reconnectAttempts)")
    audioEngine?.interruptPlayback()
    audioEngine?.stopRecording()
    setState(AssistantState.CONNECTING)
    status.value = "Reconnecting…"
    handler.postDelayed({
      if (isActive.value) connect(apiKey, resume = true)
    }, 600)
  }

  private val listener = object : GeminiLiveClient.Listener {

    override fun onSocketOpen() {
      Log.d(TAG, "Live socket open")
    }

    override fun onReady() {
      Log.d(TAG, "Live session ready")
      pendingGoAwayReconnect = false
      reconnectAttempts = 0
      if (!isActive.value) return
      errorMessage.value = null

      val micStarted = audioEngine?.startRecording() == true
      if (!micStarted) {
        errorMessage.value = "Could not start the microphone."
        teardown(keepError = true, stateAfter = AssistantState.ERROR)
        status.value = "Microphone error"
        return
      }

      turnFinished = audioEngine?.isPlayingOrQueued() != true
      if (audioEngine?.isPlayingOrQueued() == true) {
        setState(AssistantState.SPEAKING)
        status.value = "Zed is talking…"
      } else {
        setState(AssistantState.LISTENING)
        status.value = "Listening…"
      }
      startScreenContext()
      dispatchQueuedText()
    }

    override fun onAudio(pcm: ByteArray, sampleRate: Int) {
      if (!isActive.value) return
      turnFinished = false
      audioEngine?.queueAudio(pcm, sampleRate)
      if (state.value != AssistantState.SPEAKING) {
        setState(AssistantState.SPEAKING)
        status.value = "Zed is talking…"
      }
    }

    override fun onInterrupted() {
      Log.w(TAG, "Interrupted by user speech")
      audioEngine?.interruptPlayback()
      turnFinished = true
      if (isActive.value) {
        setState(AssistantState.LISTENING)
        status.value = "Listening…"
        dispatchQueuedText()
      }
    }

    override fun onUserTranscript(text: String, isFinal: Boolean) {
      userText.value = text
      if (text.isNotBlank()) lastSubmittedText = text
      if (isFinal && creatorPrompt.value == null && !creatorUnlocked.value &&
        CreatorGate.isCreatorRequest(text)
      ) {
        beginCreatorChallenge(voice = true)
      }
    }

    override fun onZedTranscript(text: String) {
      zedText.value = text
    }

    override fun onTurnComplete(userTranscript: String, zedTranscript: String) {
      val completedUserText = userTranscript.ifBlank { lastSubmittedText.orEmpty() }
      if (completedUserText.isNotBlank()) userText.value = completedUserText
      if (zedTranscript.isNotBlank()) zedText.value = zedTranscript
      if (completedUserText.isNotBlank() && zedTranscript.isNotBlank() &&
        chatTurns.value.lastOrNull()?.text != zedTranscript
      ) {
        chatTurns.value = chatTurns.value + listOf(
          ChatMessage(ChatMessage.Role.USER, completedUserText),
          ChatMessage(ChatMessage.Role.ASSISTANT, zedTranscript)
        )
        history?.append(ChatMessage.Role.USER, completedUserText)
        history?.append(ChatMessage.Role.ASSISTANT, zedTranscript)
      }
      lastSubmittedText = null
      turnFinished = true
      if (isActive.value && audioEngine?.isPlayingOrQueued() != true) {
        setState(AssistantState.LISTENING)
        status.value = "Listening…"
        dispatchQueuedText()
      }
    }

    override fun onToolCalls(calls: List<ToolCallRequest>) {
      handleToolCalls(calls)
    }

    override fun onToolCallCancelled(ids: List<String>) {
      Log.w(TAG, "Tool calls cancelled by server: $ids")
    }

    override fun onSessionHandle(handle: String) {
      sessionHandle = handle
    }

    override fun onInteractionStatus(interactionStatus: String) {
      // Extended thinking may finish without a normal turnComplete. IDLE is the
      // authoritative "ready for input" signal for those models.
      if (!interactionStatus.equals("IDLE", ignoreCase = true)) return
      turnFinished = true
      if (isActive.value && audioEngine?.isPlayingOrQueued() != true) {
        setState(AssistantState.LISTENING)
        status.value = "Listening…"
        dispatchQueuedText()
      }
    }

    override fun onGoAway() {
      Log.w(TAG, "Server is closing the session; resuming")
      pendingGoAwayReconnect = true
      status.value = "Reconnecting…"
      client?.close()
    }

    override fun onClosed(reason: String, byClient: Boolean) {
      Log.w(TAG, "Live session closed: $reason (byClient=$byClient)")
      if (!isActive.value || manualStop) return
      if (pendingGoAwayReconnect || !sessionHandle.isNullOrBlank()) {
        // Routine goAway or a network drop: resume the same conversation.
        pendingGoAwayReconnect = false
        resumeSession()
      } else {
        errorMessage.value = "Gemini Live disconnected. Tap to reconnect."
        teardown(keepError = true, stateAfter = AssistantState.ERROR)
        status.value = "Disconnected"
      }
    }

    override fun onError(message: String, fatal: Boolean) {
      Log.e(TAG, "Gemini error (fatal=$fatal): $message")
      val friendly = friendlyError(message)
      errorMessage.value = friendly
      if (fatal && isActive.value) {
        teardown(keepError = true, stateAfter = AssistantState.ERROR)
        status.value = "Connection error"
      } else if (!isActive.value) {
        setState(AssistantState.ERROR)
      }
    }
  }

  // ------------------------------------------------------------------ tools

  private fun handleToolCalls(calls: List<ToolCallRequest>) {
    val executor = actions ?: return
    toolScope.launch {
      val responses = mutableListOf<Pair<ToolCallRequest, JSONObject>>()
      for (call in calls) {
        Log.d(TAG, "Executing tool ${call.name} args=${call.args}")
        avatarResetJob?.cancel()
        avatarState.value = if (call.name in SEARCH_TOOLS) "searching" else "working"
        val result = try {
          executeTool(executor, call)
        } catch (error: Exception) {
          Log.e(TAG, "Tool ${call.name} threw", error)
          toolFailure(call.name, "tool_execution_failed")
        }
        Log.d(TAG, "Tool ${call.name} result=$result")
        actionNote.value = describeAction(result)
        if (result.optBoolean("success")) flashAvatar("happy", 2_400) else flashAvatar("error", 2_800)
        responses.add(call to result)
      }
      client?.sendToolResponse(responses)
    }
  }

  private suspend fun executeTool(executor: DeviceActions, call: ToolCallRequest): JSONObject {
    return when (call.name) {
      "openWhatsApp" -> executor.openWhatsApp()
      "openApp" -> {
        val app = call.args.optString("appName").ifBlank { call.args.optString("app") }
        if (app.isBlank()) toolFailure("openApp", "missing_app_name") else executor.openAnyApp(app)
      }
      "openUrl" -> {
        val url = call.args.optString("url")
        if (url.isBlank()) toolFailure("openUrl", "invalid_url") else executor.openUrl(url)
      }
      "makeCall" -> {
        val number = call.args.optString("phoneNumber")
        if (number.isBlank()) toolFailure("makeCall", "invalid_phone_number") else executor.makeCall(number)
      }
      "callContact" -> {
        val name = call.args.optString("contactName")
        if (name.isBlank()) toolFailure("callContact", "invalid_contact_name") else executor.callContact(name)
      }
      "sendWhatsAppMessage" -> {
        val name = call.args.optString("contactName")
        val message = call.args.optString("message")
        if (name.isBlank() || message.isBlank()) {
          toolFailure(call.name, "missing_contact_or_message")
        } else {
          executor.sendWhatsAppMessage(
            name,
            message,
            sendNow = call.args.optBoolean("sendNow", false)
          )
        }
      }
      "searchYouTube" -> {
        val query = call.args.optString("query")
        if (query.isBlank()) toolFailure(call.name, "empty_query") else executor.searchYouTube(query)
      }
      "playYouTube" -> {
        val query = call.args.optString("query")
        if (query.isBlank()) toolFailure(call.name, "empty_query") else executor.playYouTube(query)
      }
      "controlMedia" -> executor.controlMedia(call.args.optString("mediaAction", "play_pause"))
      "setVolume" -> executor.setVolume(call.args.optInt("level", 50))
      "setBrightness" -> executor.setBrightness(call.args.optInt("level", 50))
      "setFlashlight" -> executor.setFlashlight(call.args.optBoolean("on", false))
      "inspectScreen" -> ZedAccessibilityService.screenSnapshot()
      "clickElement" -> {
        val target = call.args.optString("target")
        if (target.isBlank()) toolFailure(call.name, "missing_target") else ZedAccessibilityService.click(target)
      }
      "typeText" -> {
        val text = call.args.optString("text")
        if (text.isBlank()) {
          toolFailure(call.name, "missing_text")
        } else {
          ZedAccessibilityService.type(text, call.args.optString("target").ifBlank { null })
        }
      }
      "scrollScreen" -> ZedAccessibilityService.scroll(call.args.optString("direction", "down"))
      "pressKey" -> ZedAccessibilityService.press(call.args.optString("key", "back"))
      "runTask" -> executeAgentTask(executor, call.args)
      "creatorControl" -> executeCreatorControl(call.args.optString("action"))
      "lookAtScreen" -> {
        val jpeg = ZedAccessibilityService.screenshotJpeg()
        if (jpeg == null) {
          toolFailure(call.name, "screen_capture_unavailable")
        } else {
          client?.sendVideoFrame(jpeg)
          JSONObject()
            .put("success", true)
            .put("action", call.name)
            .put("note", "A screenshot of the current screen is attached to the conversation.")
        }
      }
      "rememberFact" -> {
        val fact = call.args.optString("fact")
        val saved = if (fact.isBlank()) null else memory?.add(fact)
        if (saved == null) toolFailure(call.name, "empty_fact")
        else JSONObject()
          .put("success", true)
          .put("action", call.name)
          .put("remembered", saved.text)
          .put("category", saved.category)
      }
      "forgetMemory" -> {
        val query = call.args.optString("query")
        if (query.isBlank()) {
          toolFailure(call.name, "missing_query")
        } else {
          JSONObject()
            .put("success", true)
            .put("action", call.name)
            .put("deleted", memory?.deleteByKeyword(query) ?: 0)
        }
      }
      "recallMemories" -> {
        val entries = JSONArray()
        memory?.all(50)?.forEach { item ->
          entries.put(JSONObject().put("fact", item.text).put("category", item.category))
        }
        JSONObject()
          .put("success", true)
          .put("action", call.name)
          .put("memories", entries)
      }
      "getWeather" -> weather?.current() ?: toolFailure(call.name, "weather_unavailable")
      else -> toolFailure(call.name, "unsupported_tool")
    }
  }

  private suspend fun executeAgentTask(
    executor: DeviceActions,
    args: JSONObject
  ): JSONObject {
    val goal = args.optString("goal").ifBlank { "Multi-step task" }
    val rawSteps = args.optJSONArray("steps")
      ?: return toolFailure("runTask", "missing_steps")
    val steps = buildList {
      for (index in 0 until rawSteps.length()) {
        rawSteps.optJSONObject(index)?.let { add(AgentTaskEngine.Step.from(it)) }
      }
    }
    if (steps.isEmpty()) return toolFailure("runTask", "empty_steps")
    if (!ZedAccessibilityService.isConnected()) {
      val needsUi = steps.any {
        it.op in setOf("tap", "type", "scroll", "press", "inspect", "look")
      }
      if (needsUi) return toolFailure("runTask", "accessibility_service_disabled")
    }
    status.value = "Planning task…"
    avatarState.value = "working"
    return AgentTaskEngine.run(
      goal = goal,
      steps = steps,
      executor = executor,
      onProgress = { status.value = it },
      onFrame = { frame -> client?.sendVideoFrame(frame) }
    ).also { result ->
      status.value = if (result.optBoolean("success")) {
        "Task complete"
      } else {
        "Task stopped: ${result.optString("error").replace('_', ' ')}"
      }
      if (isActive.value) {
        setState(AssistantState.LISTENING)
        syncAvatar()
      }
    }
  }

  /** Stops a multi-step task at the next safe boundary. */
  fun cancelTask() {
    AgentTaskEngine.cancel()
    status.value = "Stopping task…"
  }

  private fun executeCreatorControl(rawAction: String): JSONObject {
    if (!creatorUnlocked.value) return toolFailure("creatorControl", "creator_access_required")
    val app = appContext ?: return toolFailure("creatorControl", "not_initialized")
    if (!AppIntegrity.isTrusted(app)) {
      Log.w(TAG, "Master control blocked: signing certificate does not match the pin")
      return toolFailure("creatorControl", "app_signature_mismatch")
    }
    val prefs = preferences ?: ZedPreferences(app).also { preferences = it }
    val action = rawAction.trim().lowercase()

    return when (action) {
      "lock_screen" -> ZedAccessibilityService.press("lock_screen")
      "go_home" -> ZedAccessibilityService.press("home")
      "go_back" -> ZedAccessibilityService.press("back")
      "open_recents" -> ZedAccessibilityService.press("recents")
      "open_notifications" -> ZedAccessibilityService.press("notifications")
      "open_quick_settings" -> ZedAccessibilityService.press("quick_settings")
      "clear_memory" -> {
        memory?.clear()
        creatorSuccess(action, "All saved memories cleared")
      }
      "clear_conversation" -> {
        clearConversation()
        creatorSuccess(action, "Conversation cleared")
      }
      "enable_wake_word" -> {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
          PackageManager.PERMISSION_GRANTED
        ) {
          return toolFailure("creatorControl", "microphone_permission_required")
        }
        prefs.wakeWordEnabled = true
        ZedWakeWordService.sync(app)
        creatorSuccess(action, "Wake word enabled")
      }
      "disable_wake_word" -> {
        prefs.wakeWordEnabled = false
        ZedWakeWordService.sync(app)
        creatorSuccess(action, "Wake word disabled")
      }
      "enable_overlay" -> {
        if (!Settings.canDrawOverlays(app)) {
          return toolFailure("creatorControl", "overlay_permission_required")
        }
        prefs.overlayEnabled = true
        ZedOverlayService.sync(app)
        creatorSuccess(action, "Floating avatar enabled")
      }
      "disable_overlay" -> {
        prefs.overlayEnabled = false
        ZedOverlayService.sync(app)
        creatorSuccess(action, "Floating avatar disabled")
      }
      "enable_screen_context" -> {
        setScreenContext(app, true)
        creatorSuccess(action, "Live screen context enabled")
      }
      "disable_screen_context" -> {
        setScreenContext(app, false)
        creatorSuccess(action, "Live screen context disabled")
      }
      "set_dark_theme" -> {
        ZedThemeRepository.set(app, ZedThemeMode.DARK)
        creatorSuccess(action, "Obsidian black theme active")
      }
      "set_light_theme" -> {
        ZedThemeRepository.set(app, ZedThemeMode.LIGHT)
        creatorSuccess(action, "Light theme active")
      }
      "lock_creator" -> {
        CreatorAuth.lock(app)
        creatorUnlocked.value = false
        creatorPrompt.value = null
        creatorSuccess(action, "Creator mode locked")
      }
      else -> toolFailure("creatorControl", "unsupported_action")
    }
  }

  private fun creatorSuccess(action: String, note: String): JSONObject =
    JSONObject()
      .put("success", true)
      .put("action", "creatorControl")
      .put("control", action)
      .put("note", note)

  private fun toolFailure(action: String, error: String): JSONObject =
    JSONObject().put("success", false).put("action", action).put("error", error)

  private fun describeAction(result: JSONObject): String {
    val action = result.optString("action")
    return if (result.optBoolean("success")) {
      when (action) {
        "openWhatsApp" -> "Opened WhatsApp"
        "openApp" -> "Opened ${result.optString("app").replaceFirstChar { it.uppercase() }}"
        "openUrl" -> "Opened website"
        "makeCall" -> if (result.optString("mode") == "call") {
          "Calling ${result.optString("phoneNumber")}"
        } else {
          "Dialer opened with ${result.optString("phoneNumber")}"
        }
        "callContact" -> if (result.optString("mode") == "call") {
          "Calling ${result.optString("contactName")}"
        } else {
          "Dialer opened for ${result.optString("contactName")}"
        }
        "sendWhatsAppMessage" -> "WhatsApp message ready for ${result.optString("contactName")}"
        "searchYouTube" -> "Searching YouTube for ${result.optString("query")}"
        "controlMedia" -> "Media ${result.optString("mediaAction")}"
        "setVolume" -> "Volume set to ${result.optInt("level")}%"
        "setBrightness" -> "Brightness set to ${result.optInt("level")}%"
        "setFlashlight" -> if (result.optBoolean("on")) "Flashlight on" else "Flashlight off"
        "inspectScreen" -> "Read the current screen"
        "clickElement" -> "Tapped ${result.optString("target")}"
        "typeText" -> "Text entered"
        "scrollScreen" -> "Scrolled ${result.optString("direction")}"
        "pressKey" -> "Pressed ${result.optString("key")}"
        "runTask" -> if (result.optBoolean("success")) {
          "Completed ${result.optInt("completedSteps")} task steps"
        } else {
          "Task stopped: ${result.optString("error").replace('_', ' ')}"
        }
        "lookAtScreen" -> "Took a look at your screen"
        "rememberFact" -> "Saved to memory"
        "forgetMemory" -> "Memory updated"
        "recallMemories" -> "Recalled your saved facts"
        "getWeather" -> "${result.optString("summary")}"
        "creatorControl" -> result.optString("note").ifBlank { "Master control" }
        else -> "Action completed"
      }
    } else {
      when (result.optString("error")) {
        "whatsapp_not_installed" -> "WhatsApp isn't installed on this phone"
        "app_not_installed" -> "That app isn't installed"
        "unsupported_app" -> "That app isn't on my safe list"
        "contact_not_found" -> "I couldn't find that contact"
        "multiple_matches" -> "I found more than one matching contact"
        "contacts_permission_denied" -> "Contacts permission is needed for that"
        "invalid_phone_number" -> "That phone number doesn't look valid"
        "invalid_url" -> "That URL doesn't look valid"
        "no_dialer_available", "no_app_can_open_url" -> "This phone can't open that"
        "accessibility_service_disabled" -> "Enable Zed's accessibility service first"
        "element_not_found" -> "I couldn't see that on screen"
        "no_editable_field" -> "There is no text field on screen"
        "screen_capture_unavailable" -> "Screen capture isn't available"
        "write_settings_required" -> "Allow Zed to change system settings"
        "location_permission_denied" -> "Location permission is needed for weather"
        "location_unavailable" -> "I can't get a location fix right now"
        "weather_service_unavailable" -> "The weather service is unreachable"
        "no_flashlight" -> "This phone has no flashlight"
        "empty_query", "empty_message", "empty_fact", "missing_query", "missing_text" ->
          "Something was missing from that request"
        "creator_access_required" -> "Creator verification is required"
        else -> "That action didn't work"
      }
    }
  }

  private fun resolveApiKey(): String {
    val stored = preferences?.apiKey ?: appContext?.let { ZedPreferences(it).apiKey }.orEmpty()
    return stored.ifBlank { BuildConfig.GEMINI_API_KEY.trim() }
  }

  // ------------------------------------------------------- creator protocol

  /** Opens the local code gate. [voice] starts a one-shot speech prompt. */
  fun requestCreatorChallenge(voice: Boolean = false) {
    if (creatorUnlocked.value) {
      status.value = "Master control is already active"
      return
    }
    beginCreatorChallenge(voice = voice)
  }

  private fun beginCreatorChallenge(voice: Boolean) {
    if (creatorPrompt.value != null || creatorUnlocked.value) return
    if (isActive.value) stop()
    creatorPrompt.value = CreatorPrompt(listening = voice, voice = voice)
    status.value = if (voice) "Say your creator code" else "Enter your creator code"
    flashAvatar("thinking", 8_000)
    if (voice) startCreatorVoice()
  }

  /** Re-opens the mic after a failed or unavailable voice attempt. */
  fun retryCreatorVoice() {
    if (creatorUnlocked.value) return
    if (creatorPrompt.value == null) return
    creatorPrompt.value = CreatorPrompt(listening = true, voice = true)
    status.value = "Say your creator code"
    startCreatorVoice()
  }

  fun cancelCreatorChallenge() {
    creatorChallenge?.stop()
    creatorChallenge = null
    creatorPrompt.value = null
    status.value = "Tap to talk"
    if (!isActive.value) setState(AssistantState.IDLE)
  }

  fun verifyCreatorCode(rawCode: String) {
    val app = appContext ?: return
    when (val result = CreatorAuth.verify(app, rawCode)) {
      CreatorAuth.Result.Success -> {
        creatorChallenge?.stop()
        creatorChallenge = null
        creatorUnlocked.value = true
        creatorPrompt.value = null
        status.value = "Master control active. Welcome back, Creator."
        actionNote.value = "Creator verified"
        flashAvatar("happy", 5_000)
      }

      CreatorAuth.Result.InvalidFormat -> {
        creatorPrompt.value = CreatorPrompt(
          listening = false,
          voice = creatorPrompt.value?.voice == true,
          error = "Enter the ${CreatorGate.MIN_CODE_LENGTH}–${CreatorGate.MAX_CODE_LENGTH} digit code."
        )
      }

      is CreatorAuth.Result.Failure -> {
        val error = if (result.lockedSeconds > 0L) {
          "Too many attempts. Try again in ${result.lockedSeconds} seconds."
        } else {
          "That code didn't match. ${result.attemptsLeft} attempts left."
        }
        creatorPrompt.value = CreatorPrompt(
          listening = false,
          voice = creatorPrompt.value?.voice == true,
          error = error,
          lockedSeconds = result.lockedSeconds
        )
        flashAvatar("error", 3_000)
      }
    }
  }

  /** Changes the code after master control is already active. */
  fun changeCreatorCode(newCode: String): Boolean {
    if (!creatorUnlocked.value) return false
    val app = appContext ?: return false
    val saved = CreatorAuth.setCode(app, newCode)
    if (saved) {
      status.value = "Creator code updated"
      actionNote.value = "Creator code changed"
    }
    return saved
  }

  fun lockCreator() {
    val app = appContext ?: return
    CreatorAuth.lock(app)
    creatorUnlocked.value = false
    creatorPrompt.value = null
    creatorChallenge?.stop()
    creatorChallenge = null
    if (isActive.value) stop()
    status.value = "Creator mode locked"
    actionNote.value = "Master control revoked"
    flashAvatar("idle", 2_000)
  }

  private fun startCreatorVoice() {
    val app = appContext ?: return
    creatorChallenge?.stop()
    creatorChallenge = CreatorChallengeSession(
      context = app,
      onHeard = { heard -> verifyCreatorCode(heard) },
      onUnavailable = { message ->
        creatorPrompt.value = creatorPrompt.value?.copy(listening = false, error = message)
        flashAvatar("error", 2_500)
      },
      onListening = {
        creatorPrompt.value = creatorPrompt.value?.copy(listening = true, error = null)
      }
    )
    creatorChallenge?.start("Creator code?")
  }

  private val SEARCH_TOOLS = setOf("searchYouTube", "inspectScreen", "lookAtScreen", "getWeather")

  private fun defaultAvatarState(): String = when (state.value) {
    AssistantState.IDLE -> "idle"
    AssistantState.CONNECTING -> "thinking"
    AssistantState.LISTENING -> "listening"
    AssistantState.SPEAKING -> "speaking"
    AssistantState.ERROR -> "error"
  }

  private fun syncAvatar() {
    if (avatarResetJob?.isActive == true) return
    avatarState.value = defaultAvatarState()
  }

  /** Shows a transient avatar expression (happy/error) before returning to the current activity. */
  private fun flashAvatar(state: String, holdMs: Long) {
    avatarResetJob?.cancel()
    avatarState.value = state
    avatarResetJob = scope.launch {
      delay(holdMs)
      avatarState.value = defaultAvatarState()
    }
  }

  // ------------------------------------------------------------------ misc

  private fun teardown(keepError: Boolean, stateAfter: AssistantState) {
    isActive.value = false
    screenFrameJob?.cancel()
    screenFrameJob = null
    queuedText.clear()
    lastSubmittedText = null
    client?.close()
    client = null
    audioEngine?.stopRecording()
    audioEngine?.stopPlayback()
    audioEngine?.endAudioSession()
    level.value = 0f
    if (!keepError) errorMessage.value = null
    appContext?.let { ScreenCaptureService.stop(it) }
    appContext?.let { ZedSessionService.stop(it) }
    setState(stateAfter)
  }

  /** Sends queued typed turns only after Live is ready and the current turn ends. */
  private fun dispatchQueuedText() {
    if (!isActive.value || state.value == AssistantState.CONNECTING || !turnFinished) return
    val text = queuedText.poll() ?: return
    if (client?.sendText(text) != true) {
      queuedText.add(text)
      errorMessage.value = "The Live session is still connecting. Your message is queued."
      return
    }

    lastSubmittedText = text
    userText.value = text
    zedText.value = ""
    turnFinished = false
    status.value = "Waiting for Zed…"
    setState(AssistantState.LISTENING)
  }

  private fun startScreenContext() {
    screenFrameJob?.cancel()
    screenFrameJob = null
    if (preferences?.screenContextEnabled != true) return
    // MediaProjection already streams frames through sendScreenFrame().
    if (ScreenCaptureService.isRunning) return
    screenFrameJob = scope.launch {
      while (isActive.value && preferences?.screenContextEnabled == true) {
        if (ScreenCaptureService.isRunning) break
        delay(2_500L)
        if (!isActive.value) break
        val jpeg = ZedAccessibilityService.screenshotJpeg() ?: continue
        client?.sendVideoFrame(jpeg)
      }
    }
  }

  /** Called by the MediaProjection service with a downscaled JPEG frame. */
  fun sendScreenFrame(jpeg: ByteArray) {
    if (isActive.value && preferences?.screenContextEnabled == true && jpeg.isNotEmpty()) {
      client?.sendVideoFrame(jpeg)
    }
  }

  /** Resume the accessibility fallback if a projection ends during a session. */
  fun onScreenProjectionStopped() {
    if (isActive.value && preferences?.screenContextEnabled == true) startScreenContext()
  }

  fun setScreenContext(context: Context, enabled: Boolean) {
    initialize(context)
    preferences?.screenContextEnabled = enabled
    if (enabled && isActive.value) startScreenContext() else {
      screenFrameJob?.cancel()
      screenFrameJob = null
    }
    if (!enabled) appContext?.let { ScreenCaptureService.stop(it) }
    actionNote.value = if (enabled) "Live screen context enabled" else "Live screen context disabled"
  }

  private fun setState(newState: AssistantState) {
    if (state.value != newState) Log.d(TAG, "State ${state.value} -> $newState")
    state.value = newState
    syncAvatar()
  }

  private fun friendlyError(detail: String): String {
    val lower = detail.lowercase()
    return when {
      lower.contains("api key") || lower.contains("permission") || lower.contains("unauthenticated") ->
        "Gemini rejected the API key. Check it in Settings."
      lower.contains("quota") || lower.contains("resource_exhausted") ->
        "Gemini's rate limit was reached. Try again in a moment."
      lower.contains("connect") || lower.contains("network") || lower.contains("timeout") ->
        "Couldn't reach Gemini Live. Check your internet connection."
      else -> "Gemini Live hit an error. Tap to try again."
    }
  }
}
