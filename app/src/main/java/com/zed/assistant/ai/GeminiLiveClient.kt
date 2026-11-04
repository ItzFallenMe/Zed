package com.zed.assistant.ai

import android.util.Base64
import android.util.Log
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

data class ToolCallRequest(
  val id: String,
  val name: String,
  val args: JSONObject
)

/**
 * Minimal, explicit implementation of the Gemini Live BidiGenerateContent
 * WebSocket protocol.
 *
 * Audio in: raw signed 16-bit little-endian mono PCM, 16 kHz, Base64 encoded.
 * Audio out: raw signed 16-bit little-endian mono PCM, normally 24 kHz; the
 * rate is read from each response's MIME type rather than assumed.
 */
class GeminiLiveClient(
  private val apiKey: String,
  private val model: String,
  private val systemInstruction: String,
  private val voiceName: String,
  private val sessionHandle: String?,
  private val listener: Listener
) {

  interface Listener {
    fun onSocketOpen()
    fun onReady()
    fun onAudio(pcm: ByteArray, sampleRate: Int)
    fun onInterrupted()
    fun onUserTranscript(text: String, isFinal: Boolean)
    fun onZedTranscript(text: String)
    fun onTurnComplete(userText: String, zedText: String)
    fun onToolCalls(calls: List<ToolCallRequest>)
    fun onToolCallCancelled(ids: List<String>)
    fun onSessionHandle(handle: String)
    fun onInteractionStatus(status: String)
    fun onGoAway()
    fun onClosed(reason: String, byClient: Boolean)
    fun onError(message: String, fatal: Boolean)
  }

  companion object {
    private const val TAG = "ZedLive"
    private const val ENDPOINT =
      "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /** One shared client so reconnects do not leak connection pools or threads. */
    private val httpClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    }
  }

  /** Gemini 3.8 supports asynchronous tool calls; older Live models are blocking. */
  private val supportsAsyncTools: Boolean = model.removePrefix("models/").startsWith("gemini-3.8")

  private var webSocket: WebSocket? = null

  @Volatile
  private var setupComplete = false

  @Volatile
  private var closedByClient = false

  /** Set by a local long-press interrupt; cleared on turn end or server interruption. */
  @Volatile
  private var muteCurrentTurn = false

  private var audioChunksSent = 0
  private var audioChunksReceived = 0

  private val inputBuffer = StringBuilder()
  private val outputBuffer = StringBuilder()

  fun connect() {
    if (apiKey.isBlank()) {
      listener.onError("Missing Gemini API key", true)
      return
    }

    val url = "$ENDPOINT?key=${URLEncoder.encode(apiKey, "UTF-8")}"
    val request = Request.Builder().url(url).build()
    Log.d(TAG, "Opening Live WebSocket (model=$model, voice=$voiceName)")

    webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "WebSocket opened; sending setup")
        listener.onSocketOpen()
        val sent = webSocket.send(buildSetup().toString())
        if (!sent) {
          Log.e(TAG, "Could not send setup message")
          listener.onError("Could not start the Gemini Live session", true)
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        handleServerMessage(text)
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        handleServerMessage(bytes.utf8())
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closing: $code $reason")
        webSocket.close(1000, null)
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closed: $code ${reason.ifBlank { "(no reason)" }}")
        listener.onClosed(reason.ifBlank { "closed ($code)" }, closedByClient)
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket failure (HTTP ${response?.code})", t)
        if (!closedByClient) {
          // Auth/model/quota failures need the user to fix configuration.
          // A pure network failure can be resumed with the session handle.
          val httpCode = response?.code
          val fatal = httpCode == 400 || httpCode == 401 || httpCode == 403 || httpCode == 429
          listener.onError(t.message ?: "Network connection failed", fatal)
          listener.onClosed(t.message ?: "network failure", false)
        }
      }
    })
  }

  /** One still frame from the accessibility screenshot API, for visual grounding. */
  fun sendVideoFrame(jpeg: ByteArray) {
    if (jpeg.isEmpty()) return
    val socket = webSocket
    if (socket == null || !setupComplete) {
      Log.w(TAG, "Cannot send a screen frame before the session is ready")
      return
    }
    val payload = JSONObject().put(
      "realtimeInput",
      JSONObject().put(
        "video",
        JSONObject()
          .put("mimeType", "image/jpeg")
          .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
      )
    )
    if (!socket.send(payload.toString())) Log.w(TAG, "Screen frame could not be sent")
  }

  fun sendAudio(pcm: ByteArray) {
    if (pcm.isEmpty()) return
    val socket = webSocket
    if (socket == null || !setupComplete) {
      if (audioChunksSent == 0) Log.w(TAG, "Dropping mic audio because the session is not ready")
      return
    }

    val payload = JSONObject().put(
      "realtimeInput",
      JSONObject().put(
        "audio",
        JSONObject()
          .put("mimeType", "audio/pcm;rate=16000")
          .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
      )
    )

    if (!socket.send(payload.toString())) {
      Log.w(TAG, "Mic chunk could not be sent")
      return
    }
    audioChunksSent++
    if (audioChunksSent == 1 || audioChunksSent % 50 == 0) {
      Log.d(TAG, "Sent mic chunk #$audioChunksSent (${pcm.size} bytes)")
    }
  }

  /** Send a typed turn through the same Live session used by microphone input. */
  fun sendText(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return false
    val socket = webSocket
    if (socket == null || !setupComplete) {
      Log.w(TAG, "Cannot send typed text before the Live session is ready")
      return false
    }

    val payload = JSONObject().put(
      "clientContent",
      JSONObject()
        .put(
          "turns",
          JSONArray().put(
            JSONObject()
              .put("role", "user")
              .put("parts", JSONArray().put(JSONObject().put("text", trimmed)))
          )
        )
        .put("turnComplete", true)
    )
    val sent = socket.send(payload.toString())
    if (!sent) Log.w(TAG, "Typed text could not be sent")
    return sent
  }

  fun sendToolResponse(results: List<Pair<ToolCallRequest, JSONObject>>) {
    if (results.isEmpty()) return
    val responses = JSONArray()
    for ((call, payload) in results) {
      // scheduling is a field of FunctionResponse, not of the response payload.
      // INTERRUPT is correct for one-shot device actions: speak the result now.
      val functionResponse = JSONObject()
        .put("id", call.id)
        .put("name", call.name)
        .put("response", JSONObject(payload.toString()))
      if (supportsAsyncTools) functionResponse.put("scheduling", "INTERRUPT")
      responses.put(functionResponse)
    }
    val message = JSONObject().put(
      "toolResponse",
      JSONObject().put("functionResponses", responses)
    )
    val sent = webSocket?.send(message.toString()) ?: false
    if (sent) {
      Log.d(TAG, "Tool response sent (${results.size} result(s))")
    } else {
      Log.e(TAG, "Failed to send tool response")
    }
  }

  /**
   * Long-press interruption: keep dropping this turn's audio even if chunks are
   * already in flight, until the server ends the turn.
   */
  fun muteCurrentTurnAudio() {
    muteCurrentTurn = true
    Log.d(TAG, "Muting the current Gemini turn locally")
  }

  fun close() {
    closedByClient = true
    setupComplete = false
    webSocket?.close(1000, "Zed session closed")
    webSocket = null
  }

  // ------------------------------------------------------------------ setup

  private fun buildSetup(): JSONObject {
    val generationConfig = JSONObject()
      .put("responseModalities", JSONArray().put("AUDIO"))
      .put("temperature", 0.9)
      .put(
        "speechConfig",
        JSONObject().put(
          "voiceConfig",
          JSONObject().put(
            "prebuiltVoiceConfig",
            JSONObject().put("voiceName", voiceName)
          )
        )
      )

    if (model.contains("extended-thinking")) {
      generationConfig.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
    }

    val resumption = JSONObject()
    if (!sessionHandle.isNullOrBlank()) resumption.put("handle", sessionHandle)

    val setup = JSONObject()
      .put("model", if (model.startsWith("models/")) model else "models/$model")
      .put("generationConfig", generationConfig)
      .put(
        "systemInstruction",
        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
      )
      .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", buildFunctionDeclarations())))
      // Transcripts are useful for the on-screen state, but Zed's voice always
      // comes from Gemini's native audio output.
      .put("inputAudioTranscription", JSONObject())
      .put("outputAudioTranscription", JSONObject())
      .put("sessionResumption", resumption)
      .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))

    return JSONObject().put("setup", setup)
  }

  private fun buildFunctionDeclarations(): JSONArray {
    val declarations = JSONArray()

    declarations.put(
      functionDeclaration(
        name = "openWhatsApp",
        description = "Open the WhatsApp app on this phone. Use this for any request to open WhatsApp, in any language (for example 'WhatsApp kholo')."
      )
    )

    declarations.put(
      functionDeclaration(
        name = "openApp",
        description = "Open any installed app by its visible name. Use for requests such as open YouTube, Instagram, Maps, Chrome or a game, in any language. If several apps match, the result lists candidates and you must ask which one.",
        properties = JSONObject().put(
          "appName",
          JSONObject()
            .put("type", "STRING")
            .put("description", "Visible app name the user said, for example YouTube or Instagram.")
        ),
        required = JSONArray().put("appName")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "openUrl",
        description = "Open a website in the phone's browser. Only valid http/https URLs are allowed.",
        properties = JSONObject().put(
          "url",
          JSONObject()
            .put("type", "STRING")
            .put("description", "Complete website URL, for example https://example.com")
        ),
        required = JSONArray().put("url")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "makeCall",
        description = "Start a phone call or open the dialer for a phone number the user explicitly said. Use for 'call 9876543210' or 'phone 9876543210'.",
        properties = JSONObject().put(
          "phoneNumber",
          JSONObject()
            .put("type", "STRING")
            .put("description", "Phone number with optional country code, digits only apart from an optional leading +.")
        ),
        required = JSONArray().put("phoneNumber")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "callContact",
        description = "Look up a saved contact by name on this phone and call that one contact. Use for 'Call Mom', 'Rahul ko call karo', etc. If the result says multiple_matches, ask the user which contact they meant instead of guessing.",
        properties = JSONObject().put(
          "contactName",
          JSONObject()
            .put("type", "STRING")
            .put("description", "Name of the contact as the user said it.")
        ),
        required = JSONArray().put("contactName")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "sendWhatsAppMessage",
        description = "Send a WhatsApp message to a saved contact. Set sendNow=true only when the user explicitly asks to send it; otherwise the chat is prefilled for confirmation. Never claim delivery unless the result says auto_send_tapped, and even then delivery is confirmed by WhatsApp.",
        properties = JSONObject()
          .put("contactName", JSONObject().put("type", "STRING").put("description", "Saved contact name."))
          .put("message", JSONObject().put("type", "STRING").put("description", "Message text."))
          .put(
            "sendNow",
            JSONObject()
              .put("type", "BOOLEAN")
              .put("description", "True only when the user explicitly asked to send without another confirmation tap.")
          ),
        required = JSONArray().put("contactName").put("message")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "searchYouTube",
        description = "Search YouTube for a video, song or topic and open the results.",
        properties = JSONObject().put(
          "query",
          JSONObject().put("type", "STRING").put("description", "Search query as the user said it.")
        ),
        required = JSONArray().put("query")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "playYouTube",
        description = "Search YouTube and tap the first matching video result. Requires accessibility to auto-play; otherwise returns the results page.",
        properties = JSONObject().put(
          "query",
          JSONObject().put("type", "STRING").put("description", "Song, video or topic to play.")
        ),
        required = JSONArray().put("query")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "controlMedia",
        description = "Play, pause, skip or stop the media that is currently playing on the phone.",
        properties = JSONObject().put(
          "mediaAction",
          JSONObject()
            .put("type", "STRING")
            .put("description", "Media command.")
            .put("enum", JSONArray(listOf("play", "pause", "play_pause", "next", "previous", "stop")))
        ),
        required = JSONArray().put("mediaAction")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "setVolume",
        description = "Set the phone's media volume to a percentage between 0 and 100.",
        properties = JSONObject().put(
          "level",
          JSONObject().put("type", "INTEGER").put("description", "Target media volume, 0 to 100.")
        ),
        required = JSONArray().put("level")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "setBrightness",
        description = "Set the screen brightness to a percentage between 0 and 100. Requires the Write Settings permission; if it is missing, Android's settings screen opens.",
        properties = JSONObject().put(
          "level",
          JSONObject().put("type", "INTEGER").put("description", "Target brightness, 0 to 100.")
        ),
        required = JSONArray().put("level")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "setFlashlight",
        description = "Turn the phone's flashlight (torch) on or off.",
        properties = JSONObject().put(
          "on",
          JSONObject().put("type", "BOOLEAN").put("description", "true to turn the torch on, false to turn it off.")
        ),
        required = JSONArray().put("on")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "inspectScreen",
        description = "Read the current screen using Android's accessibility tree. Returns visible text, buttons, fields and their bounds. Use before clicking or typing in another app."
      )
    )

    declarations.put(
      functionDeclaration(
        name = "clickElement",
        description = "Click a visible element in the current app by its text, content description or view id. Inspect the screen first when unsure.",
        properties = JSONObject().put(
          "target",
          JSONObject().put("type", "STRING").put("description", "Visible label or description of the element to click.")
        ),
        required = JSONArray().put("target")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "typeText",
        description = "Type text into the focused editable field, or into a field identified by a visible label or hint.",
        properties = JSONObject()
          .put("text", JSONObject().put("type", "STRING").put("description", "Exact text to enter."))
          .put("target", JSONObject().put("type", "STRING").put("description", "Optional field label or hint. Omit for the focused field.")),
        required = JSONArray().put("text")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "scrollScreen",
        description = "Scroll the current screen up or down.",
        properties = JSONObject().put(
          "direction",
          JSONObject()
            .put("type", "STRING")
            .put("description", "Scroll direction.")
            .put("enum", JSONArray(listOf("down", "up")))
        ),
        required = JSONArray().put("direction")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "pressKey",
        description = "Press a system navigation key: back, home, recents, notifications or quick settings.",
        properties = JSONObject().put(
          "key",
          JSONObject()
            .put("type", "STRING")
            .put("description", "System key to press.")
            .put("enum", JSONArray(listOf("back", "home", "recents", "notifications", "quick_settings")))
        ),
        required = JSONArray().put("key")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "lookAtScreen",
        description = "Capture the current screen as a JPEG and attach it to the conversation so you can see it. Requires the accessibility service. Use for visual questions about what is on screen."
      )
    )

    declarations.put(
      functionDeclaration(
        name = "rememberFact",
        description = "Permanently remember one fact the user explicitly asked you to remember. Do not store secrets, passwords or payment details.",
        properties = JSONObject().put(
          "fact",
          JSONObject().put("type", "STRING").put("description", "The fact to remember, in the user's words.")
        ),
        required = JSONArray().put("fact")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "forgetMemory",
        description = "Delete saved long-term memories that match a keyword.",
        properties = JSONObject().put(
          "query",
          JSONObject().put("type", "STRING").put("description", "Keyword identifying the memory to forget.")
        ),
        required = JSONArray().put("query")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "recallMemories",
        description = "List the facts Zed has saved about the user."
      )
    )

    declarations.put(
      functionDeclaration(
        name = "getWeather",
        description = "Get the current weather and today's high/low for the phone's location."
      )
    )

    declarations.put(
      functionDeclaration(
        name = "runTask",
        description = "Run a short, ordered sequence of phone actions in one go. Use for genuinely multi-step requests (open an app, search, tap a result, type a message). Every step is declared up front; execution stops at the first failure. Do not use for passwords, payments or destructive actions.",
        properties = JSONObject()
          .put("goal", JSONObject().put("type", "STRING").put("description", "One-line user goal."))
          .put(
            "steps",
            JSONObject()
              .put("type", "ARRAY")
              .put("description", "Ordered steps, at most 12.")
              .put(
                "items",
                JSONObject()
                  .put("type", "OBJECT")
                  .put(
                    "properties",
                    JSONObject()
                      .put(
                        "op",
                        JSONObject().put("type", "STRING").put(
                          "enum",
                          JSONArray(
                            listOf(
                              "open_app", "open_url", "whatsapp", "youtube", "media",
                              "volume", "brightness", "flashlight", "call", "tap", "type",
                              "scroll", "press", "inspect", "look", "wait"
                            )
                          )
                        )
                      )
                      .put("appName", JSONObject().put("type", "STRING"))
                      .put("url", JSONObject().put("type", "STRING"))
                      .put("contactName", JSONObject().put("type", "STRING"))
                      .put("phoneNumber", JSONObject().put("type", "STRING"))
                      .put("message", JSONObject().put("type", "STRING"))
                      .put("sendNow", JSONObject().put("type", "BOOLEAN"))
                      .put("query", JSONObject().put("type", "STRING"))
                      .put("mediaAction", JSONObject().put("type", "STRING"))
                      .put("percent", JSONObject().put("type", "INTEGER"))
                      .put("on", JSONObject().put("type", "BOOLEAN"))
                      .put("text", JSONObject().put("type", "STRING"))
                      .put("targetHint", JSONObject().put("type", "STRING"))
                      .put("direction", JSONObject().put("type", "STRING"))
                      .put("key", JSONObject().put("type", "STRING"))
                      .put("waitMs", JSONObject().put("type", "INTEGER"))
                      .put("expect", JSONObject().put("type", "STRING").put("description", "Optional text that must appear on screen after the step."))
                  )
                  .put("required", JSONArray().put("op"))
              )
          ),
        required = JSONArray().put("goal").put("steps")
      )
    )

    declarations.put(
      functionDeclaration(
        name = "appControl",
        description = "Change Zed app settings or invoke a device navigation action explicitly requested by the user. Requires normal Android permissions and confirmations for sensitive actions.",
        properties = JSONObject().put(
          "action",
          JSONObject()
            .put("type", "STRING")
            .put("description", "App-control command.")
            .put(
              "enum",
              JSONArray(
                listOf(
                  "go_home", "go_back", "open_recents",
                  "open_notifications", "open_quick_settings", "clear_memory",
                  "clear_conversation", "enable_wake_word", "disable_wake_word",
                  "enable_overlay", "disable_overlay", "set_dark_theme",
                  "set_light_theme", "enable_screen_context",
                  "disable_screen_context"
                )
              )
            )
        ),
        required = JSONArray().put("action")
      )
    )

    return declarations
  }

  private fun functionDeclaration(
    name: String,
    description: String,
    properties: JSONObject? = null,
    required: JSONArray? = null
  ): JSONObject {
    val declaration = JSONObject()
      .put("name", name)
      .put("description", description)

    if (properties != null) {
      val parameters = JSONObject()
        .put("type", "OBJECT")
        .put("properties", properties)
      if (required != null && required.length() > 0) parameters.put("required", required)
      declaration.put("parameters", parameters)
    }

    if (supportsAsyncTools) declaration.put("behavior", "NON_BLOCKING")
    return declaration
  }

  // ------------------------------------------------------------- server data

  private fun handleServerMessage(raw: String) {
    try {
      val message = JSONObject(raw)

      if (message.has("setupComplete")) {
        setupComplete = true
        Log.d(TAG, "Session ready (setupComplete)")
        listener.onReady()
        return
      }

      message.optJSONObject("error")?.let { error ->
        val detail = error.optString("message").ifBlank { error.toString() }
        Log.e(TAG, "Gemini server error: $detail")
        listener.onError(detail, true)
        return
      }

      message.optJSONObject("goAway")?.let { goAway ->
        Log.w(TAG, "Server goAway (timeLeft=${goAway.optString("timeLeft")})")
        listener.onGoAway()
      }

      message.optJSONObject("sessionResumptionUpdate")?.let { update ->
        if (update.optBoolean("resumable")) {
          val handle = update.optString("newHandle")
          if (handle.isNotBlank()) {
            Log.d(TAG, "Received session resumption handle")
            listener.onSessionHandle(handle)
          }
        }
      }

      val interactionStatus = message.optString(
        "interactionStatus",
        message.optString("interaction_status", "")
      )
      if (interactionStatus.isNotBlank()) {
        Log.d(TAG, "Interaction status: $interactionStatus")
        listener.onInteractionStatus(interactionStatus)
      }

      message.optJSONObject("toolCallCancellation")?.let { cancellation ->
        val ids = mutableListOf<String>()
        val array = cancellation.optJSONArray("ids") ?: JSONArray()
        for (i in 0 until array.length()) ids.add(array.optString(i))
        if (ids.isNotEmpty()) {
          Log.d(TAG, "Tool calls cancelled: $ids")
          listener.onToolCallCancelled(ids)
        }
      }

      message.optJSONObject("toolCall")?.let { toolCall ->
        val calls = mutableListOf<ToolCallRequest>()
        val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()
        for (i in 0 until functionCalls.length()) {
          val call = functionCalls.optJSONObject(i) ?: continue
          val name = call.optString("name")
          if (name.isBlank()) continue
          calls.add(
            ToolCallRequest(
              id = call.optString("id"),
              name = name,
              args = call.optJSONObject("args") ?: JSONObject()
            )
          )
        }
        if (calls.isNotEmpty()) {
          Log.d(TAG, "Tool call(s): ${calls.joinToString { it.name }}")
          listener.onToolCalls(calls)
        }
      }

      message.optJSONObject("serverContent")?.let { handleServerContent(it) }
    } catch (error: Exception) {
      Log.e(TAG, "Could not parse Live message: ${error.message}", error)
      listener.onError("Received an unreadable response from Gemini", false)
    }
  }

  private fun handleServerContent(content: JSONObject) {
    val wasInterrupted = content.optBoolean("interrupted")
    if (wasInterrupted) {
      Log.w(TAG, "Server reported interruption; discarding Zed's stale audio")
      muteCurrentTurn = false
      listener.onInterrupted()
      inputBuffer.setLength(0)
      outputBuffer.setLength(0)
    }

    content.optJSONObject("inputTranscription")?.optString("text")
      ?.takeIf { it.isNotBlank() }
      ?.let { text ->
        inputBuffer.append(text)
        listener.onUserTranscript(inputBuffer.toString(), false)
      }

    content.optJSONObject("outputTranscription")?.optString("text")
      ?.takeIf { it.isNotBlank() }
      ?.let { text ->
        outputBuffer.append(text)
        listener.onZedTranscript(outputBuffer.toString())
      }

    val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
    var audioInMessage = false
    if (parts != null && !wasInterrupted && !muteCurrentTurn) {
      for (index in 0 until parts.length()) {
        val part = parts.optJSONObject(index) ?: continue
        val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
        if (inline == null) {
          val text = part.optString("text")
          if (text.isNotBlank()) Log.d(TAG, "Ignoring model text part (Zed speaks via audio): ${text.take(60)}")
          continue
        }

        val mimeType = inline.optString("mimeType", inline.optString("mime_type", ""))
        val data = inline.optString("data", "")
        if (data.isBlank()) continue

        if (mimeType.isNotBlank() && !mimeType.startsWith("audio/")) {
          Log.d(TAG, "Ignoring non-audio inline data: $mimeType")
          continue
        }

        val bytes = try {
          Base64.decode(data, Base64.DEFAULT)
        } catch (error: Exception) {
          Log.e(TAG, "Base64 audio decode failed", error)
          listener.onError("Could not decode Gemini audio", false)
          continue
        }

        val sampleRate = AudioPcm.sampleRateFromMime(mimeType)
        audioChunksReceived++
        if (audioChunksReceived == 1 || audioChunksReceived % 20 == 0) {
          Log.d(
            TAG,
            "Received audio chunk #$audioChunksReceived " +
              "(mime='$mimeType', rate=$sampleRate, ${bytes.size} bytes)"
          )
        }
        listener.onAudio(bytes, sampleRate)
        audioInMessage = true
      }
    }

    if (content.optBoolean("turnComplete")) {
      val userText = inputBuffer.toString().trim()
      val zedText = outputBuffer.toString().trim()
      Log.d(
        TAG,
        "Turn complete (audioChunks=$audioChunksReceived, " +
          "userChars=${userText.length}, zedChars=${zedText.length})"
      )
      audioChunksReceived = 0
      inputBuffer.setLength(0)
      outputBuffer.setLength(0)
      muteCurrentTurn = false
      listener.onTurnComplete(userText, zedText)
    } else if (parts != null && !audioInMessage) {
      Log.d(TAG, "Server content had no audio inline data (keys=${content.keys().asSequence().toList()})")
    }
  }
}
