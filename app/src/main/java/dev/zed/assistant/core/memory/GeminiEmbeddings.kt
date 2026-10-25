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
 * Free semantic embeddings from the same Gemini key the voice uses.
 *
 * Stored alongside the offline vector for every memory, so recall is semantic when the
 * network is there and still works when it is not.
 */
class GeminiEmbeddings(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || text.isBlank()) return@withContext null
        val payload = JSONObject().apply {
            put("model", "models/$MODEL")
            put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text.take(4000)))))
            put("taskType", "SEMANTIC_SIMILARITY")
        }
        val request = Request.Builder()
            .url("$ENDPOINT/models/$MODEL:embedContent?key=$apiKey")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ZedState.log("MEMORY", "Embedding HTTP ${response.code}")
                    return@withContext null
                }
                val values = JSONObject(body)
                    .optJSONObject("embedding")
                    ?.optJSONArray("values")
                    ?: return@withContext null
                FloatArray(values.length()) { values.optDouble(it).toFloat() }
            }
        } catch (t: Throwable) {
            ZedState.log("MEMORY", "Embedding failed: ${t.message}")
            null
        }
    }

    companion object {
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL = "text-embedding-004"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
