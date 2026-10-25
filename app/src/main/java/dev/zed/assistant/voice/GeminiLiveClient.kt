package dev.zed.assistant.voice

import android.util.Base64
import dev.zed.assistant.core.ZedState
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live (BidiGenerateContent) over WebSocket.
 *
 * Streams 16 kHz PCM up, receives 24 kHz PCM down, and routes function calls so the
 * model can drive the phone. Only active while the user is talking to Zed.
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val tools: JSONArray?,
    private val voiceName: String = "Puck",
    private val listener: Listener
) {

    interface Listener {
        fun onReady()
        fun onAudio(pcm: ByteArray)
        fun onModelText(text: String)
        fun onUserText(text: String)
        fun onTurnComplete()
        fun onToolCall(callId: String, name: String, args: JSONObject)
        fun onError(message: String)
        fun onClosed()

        /** The model was cut off — usually because the user started speaking over it. */
        fun onInterrupted() {}
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null

    @Volatile
    private var ready = false

    @Volatile
    private var closed = false

    val isReady: Boolean get() = ready

    fun connect() {
        if (apiKey.isBlank()) {
            listener.onError("No Gemini API key configured")
            return
        }
        val url = "$ENDPOINT?key=$apiKey"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupPayload().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                handleFrame(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false
                ZedState.log("GEMINI", "Socket failure: ${t.message} (${response?.code})")
                listener.onError(t.message ?: "Socket failure")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
                ZedState.log("GEMINI", "Socket closed: $code $reason")
                if (!closed) listener.onClosed()
            }
        })
    }

    private fun setupPayload(): JSONObject {
        val setup = JSONObject().apply {
            put("model", "models/$model")
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put(
                        "speechConfig",
                        JSONObject().apply {
                            put(
                                "voiceConfig",
                                JSONObject().apply {
                                    put(
                                        "prebuiltVoiceConfig",
                                        JSONObject().apply { put("voiceName", voiceName) }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            put(
                "systemInstruction",
                JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                }
            )
            put("outputAudioTranscription", JSONObject())
            put("inputAudioTranscription", JSONObject())
            if (tools != null && tools.length() > 0) {
                put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
            }
        }
        return JSONObject().put("setup", setup)
    }

    private fun handleFrame(raw: String) {
        try {
            val frame = JSONObject(raw)
            if (frame.has("setupComplete")) {
                ready = true
                ZedState.log("GEMINI", "Live session ready ($model)")
                listener.onReady()
                return
            }
            frame.optJSONObject("serverContent")?.let { content ->
                content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        part.optJSONObject("inlineData")?.let { inline ->
                            val data = inline.optString("data")
                            if (data.isNotEmpty()) {
                                val bytes = Base64.decode(data, Base64.DEFAULT)
                                listener.onAudio(bytes)
                            }
                        }
                        part.optString("text").takeIf { it.isNotEmpty() }?.let {
                            listener.onModelText(it)
                        }
                    }
                }
                content.optJSONObject("outputTranscription")?.optString("text")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { listener.onModelText(it) }
                content.optJSONObject("inputTranscription")?.optString("text")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { listener.onUserText(it) }
                if (content.optBoolean("interrupted")) {
                    ZedState.log("GEMINI", "Turn interrupted")
                    listener.onInterrupted()
                }
                if (content.optBoolean("turnComplete")) listener.onTurnComplete()
            }
            frame.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
                for (i in 0 until calls.length()) {
                    val call = calls.optJSONObject(i) ?: continue
                    val id = call.optString("id")
                    val name = call.optString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()
                    ZedState.log("GEMINI", "Tool call: $name")
                    listener.onToolCall(id, name, args)
                }
            }
            if (frame.has("goAway")) {
                ZedState.log("GEMINI", "Server asked to close session")
            }
        } catch (t: Throwable) {
            ZedState.log("GEMINI", "Bad frame: ${t.message}")
        }
    }

    fun sendAudio(pcm: ByteArray) {
        if (!ready) return
        val payload = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "mediaChunks",
                JSONArray().put(
                    JSONObject()
                        .put("mimeType", "audio/pcm;rate=16000")
                        .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
                )
            )
        )
        socket?.send(payload.toString())
    }

    /** Pushes one screen frame into the conversation, hands-free. */
    fun sendVideoFrame(jpeg: ByteArray, mimeType: String = "image/jpeg") {
        if (!ready || jpeg.isEmpty()) return
        val payload = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "mediaChunks",
                JSONArray().put(
                    JSONObject()
                        .put("mimeType", mimeType)
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                )
            )
        )
        socket?.send(payload.toString())
    }

    fun sendText(text: String) {
        if (!ready || text.isBlank()) return
        val payload = JSONObject().put(
            "clientContent",
            JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                }))
                put("turnComplete", true)
            }
        )
        socket?.send(payload.toString())
    }

    fun sendToolResponse(callId: String, name: String, result: JSONObject) {
        val payload = JSONObject().put(
            "toolResponse",
            JSONObject().put(
                "functionResponses",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", callId)
                        put("name", name)
                        put("response", result)
                    }
                )
            )
        )
        socket?.send(payload.toString())
    }

    fun close() {
        closed = true
        ready = false
        runCatching { socket?.send(JSONObject().put("clientContent", JSONObject().put("turnComplete", true)).toString()) }
        runCatching { socket?.close(1000, "session finished") }
        socket = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}
