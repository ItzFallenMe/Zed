package dev.zed.assistant.voice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Enrolled voices, kept in the app's private storage as JSON. Never uploaded.
 */
class VoiceProfileStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("zed_voices", Context.MODE_PRIVATE)

    data class Profile(val name: String, val vector: FloatArray)

    fun all(): List<Profile> {
        val raw = sp.getString(KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name")
                val vectorJson = item.optJSONArray("v") ?: return@mapNotNull null
                val vector = FloatArray(vectorJson.length()) { vectorJson.optDouble(it).toFloat() }
                if (name.isBlank() || vector.isEmpty()) null else Profile(name, vector)
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun names(): List<String> = all().map { it.name }

    fun enroll(name: String, samples: ShortArray): Boolean {
        val vector = Voiceprint.embed(samples) ?: return false
        val clean = name.trim()
        if (clean.isBlank()) return false
        val existing = all().filterNot { it.name.equals(clean, ignoreCase = true) }
        val array = JSONArray()
        (existing + Profile(clean, vector)).forEach { profile ->
            array.put(
                JSONObject()
                    .put("name", profile.name)
                    .put("v", JSONArray().apply { profile.vector.forEach { put(it.toDouble()) } })
            )
        }
        sp.edit().putString(KEY, array.toString()).apply()
        return true
    }

    fun remove(name: String) {
        val remaining = all().filterNot { it.name.equals(name, ignoreCase = true) }
        val array = JSONArray()
        remaining.forEach { profile ->
            array.put(
                JSONObject()
                    .put("name", profile.name)
                    .put("v", JSONArray().apply { profile.vector.forEach { put(it.toDouble()) } })
            )
        }
        sp.edit().putString(KEY, array.toString()).apply()
    }

    /** The closest enrolled person, if they are close enough to be a confident match. */
    fun identify(samples: ShortArray): Pair<String, Float>? {
        val vector = Voiceprint.embed(samples) ?: return null
        var best: Pair<String, Float>? = null
        for (profile in all()) {
            val score = Voiceprint.cosine(vector, profile.vector)
            if (best == null || score > best.second) best = profile.name to score
        }
        return best?.takeIf { it.second >= THRESHOLD }
    }

    fun has(name: String): Boolean =
        all().any { it.name.equals(name.trim(), ignoreCase = true) }

    companion object {
        private const val KEY = "profiles"
        private const val THRESHOLD = 0.90f
    }
}
