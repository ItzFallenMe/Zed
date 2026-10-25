package dev.zed.assistant.command

import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The reasoning half of Zed, on the same Google AI Studio key as the voice.
 *
 * Gemini Live is the ear and the voice; this is the part that thinks when the user types,
 * when the live session is unavailable, or when a request needs several tool calls in a
 * row. It uses `generateContent` with function declarations, so the model drives the same
 * [CommandExecutor] tools the live session uses.
 *
 * One provider, one key: there is no second service to expire, no second quota to hit, and
 * no second model name to go stale. A turn walks a short chain of Gemini model names and
 * reports the real reason when all of them fail.
 */
class GeminiBrain(
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val tools: JSONArray,
    private val executeTool: suspend (name: String, args: JSONObject) -> String,
    private val fallbackModels: List<String> = DEFAULT_FALLBACKS
) {

    /** The answer, or the reason there is none. Never both. */
    data class Outcome(val text: String = "", val error: String? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun reply(history: List<Pair<String, String>>, userText: String): Outcome =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext Outcome(error = "key: no Gemini key saved")
            var lastError: String? = null
            for (candidate in candidates()) {
                val (text, error) = attempt(candidate, history, userText)
                if (text.isNotBlank()) return@withContext Outcome(text = text)
                lastError = error ?: lastError
                // Only a dead model name is worth retrying under another name.
                if (error == null || !error.startsWith("model:")) break
                ZedState.log("GEMINI", "Model $candidate unusable, trying the next one")
            }
            Outcome(error = lastError ?: "parse: empty reply")
        }

    private fun candidates(): List<String> =
        (listOf(model) + fallbackModels).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private suspend fun attempt(
        candidate: String,
        history: List<Pair<String, String>>,
        userText: String
    ): Pair<String, String?> {
        val contents = JSONArray()
        history.takeLast(12).forEach { (role, text) ->
            contents.put(
                JSONObject()
                    .put("role", if (role == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userText)))
        )

        repeat(MAX_ROUNDS) { round ->
            val useTools = round < MAX_ROUNDS - 1 && tools.length() > 0
            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                )
                put("contents", contents)
                put(
                    "generationConfig",
                    JSONObject().put("temperature", 0.7).put("maxOutputTokens", 700)
                )
                if (useTools) {
                    put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
                }
            }

            val (response, error) = post(candidate, payload)
            if (response == null) return Pair("", error ?: "http: request failed")
            val content = response.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?: return Pair("", "parse: no candidates in reply")

            val parts = content.optJSONArray("parts") ?: JSONArray()
            val text = StringBuilder()
            val calls = mutableListOf<Pair<String, JSONObject>>()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                part.optString("text").takeIf { it.isNotBlank() }?.let { text.append(it) }
                part.optJSONObject("functionCall")?.let { call ->
                    val name = call.optString("name")
                    if (name.isNotBlank()) {
                        calls += name to (call.optJSONObject("args") ?: JSONObject())
                    }
                }
            }

            if (calls.isEmpty() || !useTools) return Pair(text.toString().trim(), null)

            // Hand the model's own turn back, then every tool result, then let it speak.
            contents.put(content)
            val responses = JSONArray()
            calls.forEach { (name, args) ->
                val result = runCatching { executeTool(name, args) }
                    .getOrElse { "failed: ${it.message}" }
                ZedState.log("TOOL", "$name -> ${result.take(120)}")
                responses.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject()
                            .put("name", name)
                            .put("response", JSONObject().put("result", result.take(3000)))
                    )
                )
            }
            contents.put(JSONObject().put("role", "user").put("parts", responses))
        }
        return Pair("", "loop: no answer after $MAX_ROUNDS rounds")
    }

    private fun post(candidate: String, payload: JSONObject): Pair<JSONObject?, String?> {
        val request = Request.Builder()
            .url("$ENDPOINT/models/$candidate:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ZedState.log("GEMINI", "HTTP ${response.code} on $candidate: ${body.take(160)}")
                    return null to classify(response.code)
                }
                JSONObject(body) to null
            }
        } catch (t: Throwable) {
            ZedState.log("GEMINI", "Request failed: ${t.message}")
            null to "network: ${t.message ?: "unreachable"}"
        }
    }

    private fun classify(code: Int): String = when (code) {
        400 -> "request: Gemini rejected the request ($code)"
        401, 403 -> "key: Gemini rejected the key ($code)"
        404 -> "model: not available on this key ($code)"
        429 -> "rate: Gemini is rate-limiting this key ($code)"
        in 500..599 -> "server: Gemini is having a moment ($code)"
        else -> "http: request failed ($code)"
    }

    companion object {
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_ROUNDS = 4
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Current general-purpose text models, newest first. */
        val DEFAULT_FALLBACKS = listOf(
            "gemini-2.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.0-flash"
        )
    }
}
