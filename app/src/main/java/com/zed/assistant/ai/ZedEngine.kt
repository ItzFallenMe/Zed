package com.zed.assistant.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zed.assistant.BuildConfig
import com.zed.assistant.ZedSessionService
import com.zed.assistant.model.AssistantState
import com.zed.assistant.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

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

  // Internals -------------------------------------------------------------
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val toolScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val handler = Handler(Looper.getMainLooper())

  @Volatile
  private var appContext: Context? = null

  private var audioEngine: AudioEngine? = null
  private var actions: DeviceActions? = null
  private var client: GeminiLiveClient? = null

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
  private var pendingText: String? = null

  @Volatile
  private var lastSubmittedText: String? = null

  @Synchronized
  fun initialize(context: Context) {
    if (appContext != null) return
    val app = context.applicationContext
    appContext = app

    val engine = AudioEngine(app)
    audioEngine = engine
    actions = DeviceActions(app, ContactsRepository(app))

    scope.launch {
      engine.level.collect { level.value = it }
    }

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
    val app = appContext ?: return
    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val apiKey = resolveApiKey(prefs)
    if (apiKey.isBlank()) {
      pendingText = null
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
        pendingText = null
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
      connect(apiKey, prefs, resume = false)
    }
  }

  fun stop() {
    Log.d(TAG, "Stopping session")
    manualStop = true
    pendingGoAwayReconnect = false
    handler.removeCallbacksAndMessages(null)
    teardown(keepError = false, stateAfter = AssistantState.IDLE)
    status.value = "Tap to talk"
    userText.value = ""
    zedText.value = ""
    actionNote.value = null
    pendingText = null
    lastSubmittedText = null
  }

  /** Start a session if needed, then send a typed turn over Gemini Live. */
  fun sendText(context: Context, text: String) {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return

    if (!isActive.value) {
      pendingText = trimmed
      start(context)
      return
    }

    if (state.value == AssistantState.SPEAKING) interrupt()
    userText.value = trimmed
    zedText.value = ""
    turnFinished = false
    status.value = "Sending…"
    if (client?.sendText(trimmed) != true) {
      errorMessage.value = "The Live session is still connecting. Try sending again in a moment."
      return
    }
    setState(AssistantState.LISTENING)
  }

  fun clearConversation() {
    chatTurns.value = emptyList()
    userText.value = ""
    zedText.value = ""
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

  fun hasApiKey(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return resolveApiKey(prefs).isNotBlank()
  }

  // ------------------------------------------------------------- connection

  private fun connect(apiKey: String, prefs: SharedPreferences, resume: Boolean) {
    if (!isActive.value) return
    val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
    val voice = prefs.getString(KEY_VOICE, DEFAULT_VOICE).orEmpty().ifBlank { DEFAULT_VOICE }
    val userName = prefs.getString(KEY_USER_NAME, "").orEmpty()
    val handle = if (resume) sessionHandle else null

    client?.close()
    val newClient = GeminiLiveClient(
      apiKey = apiKey,
      model = model,
      systemInstruction = ZedPersona.systemInstruction(userName),
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
    val app = appContext ?: return
    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val apiKey = resolveApiKey(prefs)

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
      if (isActive.value) connect(apiKey, prefs, resume = true)
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

      pendingText?.let { text ->
        pendingText = null
        lastSubmittedText = text
        userText.value = text
        turnFinished = false
        status.value = "Sending…"
        if (client?.sendText(text) != true) {
          errorMessage.value = "The typed message could not be sent. Try again."
        }
      }
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
      }
    }

    override fun onUserTranscript(text: String, isFinal: Boolean) {
      userText.value = text
      if (text.isNotBlank()) lastSubmittedText = text
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
      }
      lastSubmittedText = null
      turnFinished = true
      if (isActive.value && audioEngine?.isPlayingOrQueued() != true) {
        setState(AssistantState.LISTENING)
        status.value = "Listening…"
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
        val result = try {
          executeTool(executor, call)
        } catch (error: Exception) {
          Log.e(TAG, "Tool ${call.name} threw", error)
          toolFailure(call.name, "tool_execution_failed")
        }
        Log.d(TAG, "Tool ${call.name} result=$result")
        actionNote.value = describeAction(result)
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
        if (app.isBlank()) toolFailure("openApp", "missing_app_name") else executor.openApp(app)
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
      else -> toolFailure(call.name, "unsupported_tool")
    }
  }

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
        else -> "That action didn't work"
      }
    }
  }

  // ------------------------------------------------------------------ misc

  private fun teardown(keepError: Boolean, stateAfter: AssistantState) {
    isActive.value = false
    pendingText = null
    lastSubmittedText = null
    client?.close()
    client = null
    audioEngine?.stopRecording()
    audioEngine?.stopPlayback()
    audioEngine?.endAudioSession()
    level.value = 0f
    if (!keepError) errorMessage.value = null
    appContext?.let { ZedSessionService.stop(it) }
    setState(stateAfter)
  }

  private fun setState(newState: AssistantState) {
    if (state.value != newState) Log.d(TAG, "State ${state.value} -> $newState")
    state.value = newState
  }

  private fun resolveApiKey(prefs: SharedPreferences): String {
    val stored = prefs.getString(KEY_API_KEY, "").orEmpty().trim()
    if (stored.isNotEmpty()) return stored
    return BuildConfig.GEMINI_API_KEY.trim()
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
