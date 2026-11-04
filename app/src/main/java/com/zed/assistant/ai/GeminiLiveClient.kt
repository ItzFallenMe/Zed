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
        description = "Open one of the allowlisted apps installed on this phone. Use for requests such as open YouTube, Instagram, Maps or Chrome, in any language.",
        properties = JSONObject().put(
          "appName",
          JSONObject()
            .put("type", "STRING")
            .put("description", "App to open.")
            .put("enum", JSONArray(DeviceActions.supportedAppNames))
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
