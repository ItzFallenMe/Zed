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
 * The reasoning half of Zed, on Groq's free tier.
 *
 * Gemini Live is the ear and the voice; Groq is the part that thinks when the user types,
 * when the live session is unavailable, or when a request needs several tool calls in a
 * row. It speaks the OpenAI chat-completions dialect, with real function calling, so the
 * model can drive the same [CommandExecutor] tools the live session uses.
 */
class GroqBrain(
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val tools: JSONArray,
    private val executeTool: suspend (name: String, args: JSONObject) -> String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val toolSpec: JSONArray = openAiTools(tools)

    /**
     * Runs a full turn: ask, execute any tool calls, feed the results back, repeat, and
     * finally return the sentence Zed should say.
     */
    suspend fun reply(history: List<Pair<String, String>>, userText: String): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext ""
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(12).forEach { (role, text) ->
                messages.put(
                    JSONObject()
                        .put("role", if (role == "user") "user" else "assistant")
                        .put("content", text)
                )
            }
            messages.put(JSONObject().put("role", "user").put("content", userText))

            repeat(MAX_ROUNDS) { round ->
                val payload = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("temperature", 0.7)
                    put("max_tokens", 512)
                    if (toolSpec.length() > 0) {
                        put("tools", toolSpec)
                        put("tool_choice", "auto")
                    }
                }
                val response = post(payload) ?: return@withContext ""
                val message = response.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?: return@withContext ""

                val calls = message.optJSONArray("tool_calls")
                if (calls == null || calls.length() == 0) {
                    return@withContext message.optString("content").trim()
                }

                messages.put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", message.optString("content"))
                        .put("tool_calls", calls)
                )

                for (i in 0 until calls.length()) {
                    val call = calls.optJSONObject(i) ?: continue
                    val id = call.optString("id")
                    val function = call.optJSONObject("function") ?: continue
                    val name = function.optString("name")
                    val rawArgs = function.optString("arguments")
                    val args = runCatching { JSONObject(rawArgs.ifBlank { "{}" }) }
                        .getOrElse { JSONObject() }
                    val result = runCatching { executeTool(name, args) }
                        .getOrElse { "failed: ${it.message}" }
                    ZedState.log("GROQ", "$name -> ${result.take(100)}")
                    messages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", id)
                            .put("name", name)
                            .put("content", result.take(3000))
                    )
                }
                if (round == MAX_ROUNDS - 1) {
                    // Final pass without tools, so the model has to answer in words.
                    val finalPayload = JSONObject().apply {
                        put("model", model)
                        put("messages", messages)
                        put("temperature", 0.7)
                        put("max_tokens", 512)
                    }
                    val finalResponse = post(finalPayload) ?: return@withContext ""
                    return@withContext finalResponse.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                        .orEmpty()
                }
            }
            ""
        }

    private fun post(payload: JSONObject): JSONObject? {
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ZedState.log("GROQ", "HTTP ${response.code}: ${body.take(160)}")
                    return null
                }
                JSONObject(body)
            }
        } catch (t: Throwable) {
            ZedState.log("GROQ", "Request failed: ${t.message}")
            null
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        private const val MAX_ROUNDS = 4
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Gemini declares schemas in shouting case; OpenAI wants lowercase. */
        fun openAiTools(declarations: JSONArray): JSONArray {
            val out = JSONArray()
            for (i in 0 until declarations.length()) {
                val declaration = declarations.optJSONObject(i) ?: continue
                out.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", declaration.optString("name"))
                                .put("description", declaration.optString("description"))
                                .put("parameters", lowerSchema(declaration.optJSONObject("parameters")))
                        )
                )
            }
            return out
        }

        private fun lowerSchema(schema: JSONObject?): JSONObject {
            if (schema == null) return JSONObject().put("type", "object")
            val out = JSONObject()
            schema.keys().forEach { key ->
                when (key) {
                    "type" -> out.put(
                        "type",
                        when (schema.optString(key)) {
                            "OBJECT" -> "object"
                            "STRING" -> "string"
                            "INTEGER" -> "integer"
                            "BOOLEAN" -> "boolean"
                            "ARRAY" -> "array"
                            "NUMBER" -> "number"
                            else -> schema.optString(key).lowercase()
                        }
                    )
                    "properties" -> {
                        val properties = schema.optJSONObject(key) ?: JSONObject()
                        val lowered = JSONObject()
                        properties.keys().forEach { name ->
                            lowered.put(name, lowerSchema(properties.optJSONObject(name)))
                        }
                        out.put("properties", lowered)
                    }
                    "items" -> out.put("items", lowerSchema(schema.optJSONObject(key)))
                    else -> out.put(key, schema.opt(key))
                }
            }
            return out
        }
    }
}
