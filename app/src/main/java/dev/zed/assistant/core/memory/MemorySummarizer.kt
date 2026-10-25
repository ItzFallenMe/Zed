package dev.zed.assistant.core.memory

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
 * Turns a finished conversation into long-term memory: one short episode plus the durable
 * facts worth keeping. Runs on Groq first (fast and free) and falls back to Gemini.
 */
class MemorySummarizer(
    private val groqKey: String,
    private val groqModel: String,
    private val geminiKey: String
) {

    data class Summary(val episode: String, val facts: List<String>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(transcript: String): Summary? = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) return@withContext null
        val prompt = buildString {
            appendLine("You maintain the long-term memory of a phone assistant called Zed.")
            appendLine("Read the conversation and reply with JSON only, no prose, no code fences:")
            appendLine("""{"episode":"one or two sentences describing what happened","facts":["..."]}""")
            appendLine()
            appendLine("Rules:")
            appendLine("- facts = durable things about the user worth knowing in a month: names,")
            appendLine("  relationships, preferences, routines, plans, places, ongoing projects, decisions.")
            appendLine("- Never store small talk, one-off questions, or anything already implied.")
            appendLine("- Never store passwords, card numbers or identity numbers unless the user")
            appendLine("  explicitly asked you to remember them.")
            appendLine("- At most 5 facts. Empty array is fine.")
            appendLine("- episode is written in third person, past tense, under 220 characters.")
            appendLine()
            appendLine("Conversation:")
            append(transcript.take(6000))
        }

        val raw = callGroq(prompt) ?: callGemini(prompt) ?: return@withContext null
        parse(raw)
    }

    private fun parse(raw: String): Summary? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val json = JSONObject(raw.substring(start, end + 1))
            val episode = json.optString("episode").trim()
            val factsArray: JSONArray? = json.optJSONArray("facts")
            val facts = buildList {
                if (factsArray != null) {
                    for (i in 0 until factsArray.length()) {
                        factsArray.optString(i).trim()
                            .takeIf { it.isNotBlank() && it.length in 4..300 }
                            ?.let { add(it) }
                    }
                }
            }
            if (episode.isBlank() && facts.isEmpty()) null else Summary(episode, facts.distinct().take(5))
        } catch (t: Throwable) {
            ZedState.log("MEMORY", "Summary parse failed: ${t.message}")
            null
        }
    }

    private fun callGroq(prompt: String): String? {
        if (groqKey.isBlank()) return null
        val payload = JSONObject().apply {
            put("model", groqModel)
            put("temperature", 0.2)
            put("max_tokens", 400)
            put("response_format", JSONObject().put("type", "json_object"))
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "Reply with JSON only."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
        }
        return post("https://api.groq.com/openai/v1/chat/completions", payload, groqKey)
            ?.let { body ->
                JSONObject(body).optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
            }
    }

    private fun callGemini(prompt: String): String? {
        if (geminiKey.isBlank()) return null
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
            )
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-2.0-flash:generateContent?key=$geminiKey"
        return post(url, payload, null)?.let { body ->
            JSONObject(body).optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
        }
    }

    private fun post(url: String, payload: JSONObject, bearer: String?): String? {
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
        if (bearer != null) builder.addHeader("Authorization", "Bearer $bearer")
        return try {
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ZedState.log("MEMORY", "Summariser HTTP ${response.code}")
                    null
                } else {
                    body
                }
            }
        } catch (t: Throwable) {
            ZedState.log("MEMORY", "Summariser failed: ${t.message}")
            null
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
