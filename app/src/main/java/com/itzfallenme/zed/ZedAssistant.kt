package com.itzfallenme.zed

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.sin

data class AssistantState(
    val label: String = "Tap Start talking",
    val isActive: Boolean = false,
    val error: String? = null
)

/**
 * Native Android audio and Gemini Live transport.
 * Input: PCM16 little-endian, mono, 16 kHz.
 * Output: PCM16 little-endian, mono, 24 kHz.
 */
class ZedAssistant(private val context: Context) : Closeable {
    companion object {
        private const val TAG = "ZedLive"
        private const val INPUT_RATE = 16_000
        private const val OUTPUT_RATE = 24_000
        private const val MODEL = "models/gemini-3.8-live"
        private const val VOICE = "Aoede"
        private const val SYSTEM_PROMPT =
            "You are Zed, a confident, witty, friendly, playful and emotionally aware voice assistant. " +
            "Speak naturally and concisely. Detect the language the user is speaking and reply in that language, " +
            "including Hindi, English and natural Hinglish; adapt when the user switches languages. " +
            "Use declared tools only for the safe actions they describe. Opening the dialer does not place a call. " +
            "Never claim an action succeeded unless its tool response confirms success."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val _state = MutableStateFlow(AssistantState())
    val state = _state.asStateFlow()
    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()

    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var speaker: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    @Volatile private var userStopped = false
    @Volatile private var serverTurnComplete = false
    @Volatile private var setupFinished = false

    fun setError(message: String) {
        Log.e(TAG, message)
        _state.value = _state.value.copy(label = "Error", error = message)
    }

    suspend fun start(apiKey: String) {
        if (_state.value.isActive) return
        if (apiKey.isBlank()) {
            setError("Enter a Gemini API key before starting.")
            return
        }
        userStopped = false
        setupFinished = false
        serverTurnComplete = false
        _state.value = AssistantState("Connecting to Gemini Live…", true)

        try {
            prepareAudio()
            connect(apiKey)
        } catch (error: Exception) {
            Log.e(TAG, "Could not start voice session", error)
            setError("Could not start voice session: " + (error.message ?: error.javaClass.simpleName))
            stopAudio()
            _state.value = _state.value.copy(isActive = false)
            socket?.close(1000, "Startup failed")
            socket = null
        }
    }

    private fun prepareAudio() {
        val inputMin = AudioRecord.getMinBufferSize(
            INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(inputMin > 0) { "16 kHz PCM microphone capture is not supported." }
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            INPUT_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(inputMin, 4096)
        )
        require(recorder?.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed." }

        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(recorder!!.audioSessionId)
            echoCanceler?.enabled = true
        }

        val outputMin = AudioTrack.getMinBufferSize(
            OUTPUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(outputMin > 0) { "24 kHz PCM speaker output is not supported." }
        speaker = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(OUTPUT_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(outputMin, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        require(speaker?.state == AudioTrack.STATE_INITIALIZED) { "Speaker initialization failed." }
        Log.i(TAG, "Audio devices initialized: input=16kHz mono PCM16; output=24kHz mono PCM16.")
    }

    private suspend fun connect(apiKey: String) {
        val endpoint = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
            .toHttpUrl().newBuilder().addQueryParameter("key", apiKey).build()
        val ready = CompletableDeferred<Unit>()

        socket = http.newWebSocket(Request.Builder().url(endpoint).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Gemini Live WebSocket connected.")
                val setup = JSONObject().put(
                    "setup",
                    JSONObject()
                        .put("model", MODEL)
                        .put(
                            "generationConfig",
                            JSONObject()
                                .put("responseModalities", JSONArray().put("AUDIO"))
                                .put(
                                    "speechConfig",
                                    JSONObject().put(
                                        "voiceConfig",
                                        JSONObject().put(
                                            "prebuiltVoiceConfig",
                                            JSONObject().put("voiceName", VOICE)
                                        )
                                    )
                                )
                        )
                        .put(
                            "systemInstruction",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))
                            )
                        )
                        .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", toolDeclarations())))
                )
                if (!webSocket.send(setup.toString())) {
                    ready.completeExceptionally(IllegalStateException("Could not send Gemini Live setup."))
                } else {
                    Log.d(TAG, "Gemini Live setup sent.")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = JSONObject(text)

                    if (event.has("setupComplete")) {
                        setupFinished = true
                        if (!ready.isCompleted) ready.complete(Unit)
                        startAudioLoops()
                    }

                    val serverContent = event.optJSONObject("serverContent")
                    if (serverContent != null) {
                        if (serverContent.optBoolean("interrupted", false)) {
                            Log.i(TAG, "Gemini interrupted playback; flushing old audio.")
                            clearPlaybackQueue()
                            _state.value = _state.value.copy(label = "Listening")
                        }

                        val parts = serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")
                        if (parts != null) {
                            for (index in 0 until parts.length()) {
                                val part = parts.optJSONObject(index) ?: continue
                                val inline = part.optJSONObject("inlineData") ?: continue
                                val mime = inline.optString("mimeType", "audio/pcm;rate=24000")
                                if (!mime.startsWith("audio/pcm", ignoreCase = true)) {
                                    Log.w(TAG, "Unsupported Gemini audio MIME type: " + mime)
                                    continue
                                }
                                if (mime.contains("rate=") && !mime.contains("rate=24000")) {
                                    throw IllegalArgumentException("Unexpected Gemini audio sample rate: " + mime)
                                }

                                val encoded = inline.optString("data")
                                if (encoded.isNotBlank()) {
                                    val pcm = Base64.decode(encoded, Base64.DEFAULT)
                                    require(pcm.isNotEmpty() && pcm.size % 2 == 0) {
                                        "Gemini returned invalid PCM16 bytes: " + pcm.size
                                    }
                                    playbackQueue.offer(pcm)
                                    serverTurnComplete = false
                                    _state.value = _state.value.copy(label = "Speaking", error = null)
                                    Log.d(TAG, "Queued " + pcm.size + " audio bytes; MIME=" + mime)
                                }
                            }
                        }

                        if (serverContent.optBoolean("turnComplete", false)) {
                            serverTurnComplete = true
                            Log.d(TAG, "Gemini turn complete.")
                        }
                    }

                    event.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
                        for (index in 0 until calls.length()) {
                            val call = calls.optJSONObject(index) ?: continue
                            scope.launch { executeToolCall(call) }
                        }
                    }

                    event.optJSONObject("goAway")?.let { goAway ->
                        Log.w(TAG, "Gemini requested session shutdown; timeLeft=" + goAway.optString("timeLeft"))
                        _state.value = _state.value.copy(label = "Session ending")
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to parse Gemini Live event", error)
                    setError("Gemini response error: " + (error.message ?: "invalid event"))
                }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket failed. HTTP=" + response?.code, error)
                if (!ready.isCompleted) ready.completeExceptionally(error)
                if (!userStopped) {
                    setError("Gemini Live connection failed: " + (error.message ?: "network error"))
                    stopAudio()
                    _state.value = _state.value.copy(isActive = false)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Gemini Live disconnected: code=" + code + " reason=" + reason)
                if (!userStopped) {
                    stopAudio()
                    _state.value = AssistantState("Disconnected", false, reason.ifBlank { null })
                }
            }
        })

        withTimeout(20_000) { ready.await() }
        check(setupFinished) { "Gemini Live setup did not complete." }
    }

    private fun startAudioLoops() {
        if (captureJob?.isActive == true) return
        try {
            recorder?.startRecording()
            speaker?.play()
            _state.value = AssistantState("Listening", true)
            Log.i(TAG, "Microphone capture and speaker playback started.")

            // Keep the mic running while Zed speaks; Gemini VAD can then detect user interruption.
            captureJob = scope.launch(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(2048)
                    while (isActive) {
                        val count = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: -1
                        if (count > 0) {
                            val payload = JSONObject().put(
                                "realtimeInput",
                                JSONObject().put(
                                    "audio",
                                    JSONObject()
                                        .put("mimeType", "audio/pcm;rate=16000")
                                        .put("data", Base64.encodeToString(buffer.copyOf(count), Base64.NO_WRAP))
                                )
                            )
                            if (socket?.send(payload.toString()) != true) {
                                Log.w(TAG, "Microphone chunk could not be sent.")
                            }
                        } else if (count < 0) {
                            throw IllegalStateException("AudioRecord.read returned " + count)
                        }
                    }
                } catch (error: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Microphone capture failed", error)
                        setError("Microphone capture failed: " + (error.message ?: "unknown error"))
                    }
                }
            }

            playbackJob = scope.launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val pcm = playbackQueue.poll()
                        if (pcm == null) {
                            if (serverTurnComplete) {
                                serverTurnComplete = false
                                _state.value = _state.value.copy(label = "Listening")
                            }
                            delay(8)
                            continue
                        }
                        val written = speaker?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) ?: -1
                        if (written < 0) throw IllegalStateException("AudioTrack.write failed: " + written)
                        if (written != pcm.size) Log.w(TAG, "AudioTrack partial write: " + written + "/" + pcm.size)
                    }
                } catch (error: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Speaker playback failed", error)
                        setError("Speaker playback failed: " + (error.message ?: "unknown error"))
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Audio loop startup failed", error)
            setError("Audio pipeline failed: " + (error.message ?: "unknown error"))
        }
    }

    private fun toolDeclarations(): JSONArray {
        val openApp = JSONObject()
            .put("name", "openApp")
            .put("description", "Open an installed app supported by Zed's explicit allowlist.")
            .put(
                "parameters",
                JSONObject()
                    .put("type", "OBJECT")
                    .put(
                        "properties",
                        JSONObject().put(
                            "appName",
                            JSONObject().put("type", "STRING")
                                .put("description", "App name, for example WhatsApp, YouTube or Instagram.")
                        )
                    )
                    .put("required", JSONArray().put("appName"))
            )

        val openUrl = JSONObject()
            .put("name", "openUrl")
            .put("description", "Open a website with a valid HTTP or HTTPS URL.")
            .put(
                "parameters",
                JSONObject()
                    .put("type", "OBJECT")
                    .put(
                        "properties",
                        JSONObject().put(
                            "url",
                            JSONObject().put("type", "STRING").put("description", "Full HTTP or HTTPS URL.")
                        )
                    )
                    .put("required", JSONArray().put("url"))
            )

        val openDialer = JSONObject()
            .put("name", "openDialer")
            .put("description", "Open Android dialer with a user-provided number. This does not place a call.")
            .put(
                "parameters",
                JSONObject()
                    .put("type", "OBJECT")
                    .put(
                        "properties",
                        JSONObject().put(
                            "phoneNumber",
                            JSONObject().put("type", "STRING").put("description", "Phone number explicitly provided by the user.")
                        )
                    )
                    .put("required", JSONArray().put("phoneNumber"))
            )

        return JSONArray().put(openApp).put(openUrl).put(openDialer)
    }

    private suspend fun executeToolCall(call: JSONObject) {
        val id = call.optString("id")
        val name = call.optString("name")
        val args = call.optJSONObject("args") ?: JSONObject()
        val actions = DeviceActions(context)

        val result = when (name) {
            "openApp" -> actions.openApp(args.optString("appName"))
            "openUrl" -> actions.openUrl(args.optString("url"))
            "openDialer" -> actions.openDialer(args.optString("phoneNumber"))
            else -> Result.failure(IllegalArgumentException("Unknown Zed action: " + name))
        }

        val resultBody = result.fold(
            onSuccess = { JSONObject().put("success", true).put("action", name) },
            onFailure = {
                JSONObject().put("success", false).put("action", name)
                    .put("error", it.message ?: "Action failed")
            }
        )

        val functionResponse = JSONObject()
            .put("name", name)
            .put("id", id)
            .put("response", JSONObject().put("result", resultBody))
        val outgoing = JSONObject().put(
            "toolResponse",
            JSONObject().put("functionResponses", JSONArray().put(functionResponse))
        )

        if (socket?.send(outgoing.toString()) != true) {
            Log.e(TAG, "Could not send tool response for " + name + " (id=" + id + ").")
        }

        if (result.isFailure) {
            val error = result.exceptionOrNull()
            Log.e(TAG, "Action " + name + " failed", error)
            _state.value = _state.value.copy(error = error?.message ?: "Action failed.")
        } else {
            Log.i(TAG, "Action " + name + " completed.")
            _state.value = _state.value.copy(error = null)
        }
    }

    private fun clearPlaybackQueue() {
        playbackQueue.clear()
        serverTurnComplete = false
        speaker?.pause()
        speaker?.flush()
        speaker?.play()
    }

    fun interrupt() {
        clearPlaybackQueue()
        // Gemini VAD handles ordinary barge-in. This method is for an explicit UI Stop command.
        val payload = JSONObject().put(
            "clientContent",
            JSONObject().put("turns", JSONArray()).put("turnComplete", true)
        )
        val sent = socket?.send(payload.toString()) == true
        Log.i(TAG, "Explicit interruption sent=" + sent)
        _state.value = _state.value.copy(label = "Listening")
    }

    /** 440 Hz diagnostic routed through the same AudioTrack and queue as Gemini output. */
    fun testSpeaker() {
        val output = speaker ?: return setError("Start a Live session before testing the speaker.")
        val sampleCount = OUTPUT_RATE / 2
        val bytes = ByteArray(sampleCount * 2)
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val sample = (sin(2.0 * Math.PI * 440.0 * index / OUTPUT_RATE) * 5000.0).toInt().toShort()
            buffer.putShort(sample)
        }
        playbackQueue.offer(bytes)
        Log.i(TAG, "440 Hz speaker diagnostic queued; AudioTrack playState=" + output.playState)
    }

    private fun stopAudio() {
        captureJob?.cancel()
        playbackJob?.cancel()
        captureJob = null
        playbackJob = null

        try { recorder?.stop() } catch (error: Exception) { Log.w(TAG, "AudioRecord stop failed", error) }
        recorder?.release()
        recorder = null

        echoCanceler?.release()
        echoCanceler = null

        try { speaker?.stop() } catch (error: Exception) { Log.w(TAG, "AudioTrack stop failed", error) }
        speaker?.release()
        speaker = null
        playbackQueue.clear()
        serverTurnComplete = false
    }

    fun stop() {
        userStopped = true
        stopAudio()
        socket?.close(1000, "User stopped Zed")
        socket = null
        setupFinished = false
        _state.value = AssistantState()
    }

    override fun close() {
        stop()
        scope.cancel()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
