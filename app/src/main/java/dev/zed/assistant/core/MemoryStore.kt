package dev.zed.assistant.core

import android.content.Context
import org.json.JSONObject

/**
 * Small, private long-term memory: "remember my locker code is 4412" survives restarts.
 *
 * Stored as JSON in the app's private preferences. Nothing is uploaded, and the whole
 * store can be wiped from settings.
 */
class MemoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun remember(fact: String): String? {
        val cleaned = fact.trim().trim('.', ' ')
        if (cleaned.length < 2) return null
        val key = deriveKey(cleaned)
        val data = read()
        data.put(key, cleaned)
        write(data)
        ZedState.log("MEMORY", "Remembered: $cleaned")
        return key
    }

    fun all(): Map<String, String> = read().let { json ->
        json.keys().asSequence().associateWith { json.optString(it) }
    }

    fun recall(query: String): List<String> {
        val needle = query.trim().lowercase()
        val facts = all().values.toList()
        if (needle.isBlank()) return facts.takeLast(6)
        return facts.filter { it.lowercase().contains(needle) }
            .ifEmpty { facts.filter { needle.split(" ").any { word -> word.length > 2 && it.lowercase().contains(word) } } }
    }

    fun forget(query: String): Int {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return 0
        val data = read()
        val keys = data.keys().asSequence()
            .filter { key ->
                key.lowercase().contains(needle) ||
                    data.optString(key).lowercase().contains(needle)
            }
            .toList()
        keys.forEach { data.remove(it) }
        if (keys.isNotEmpty()) write(data)
        ZedState.log("MEMORY", "Forgot ${keys.size} item(s)")
        return keys.size
    }

    fun clear() {
        prefs.edit().remove(KEY_DATA).apply()
        ZedState.log("MEMORY", "Memory cleared")
    }

    /** Compact block injected into the model prompt so recall feels natural. */
    fun summary(limit: Int = 12): String {
        val facts = all().values.toList().takeLast(limit)
        if (facts.isEmpty()) return ""
        return facts.joinToString("\n") { "- $it" }
    }

    private fun deriveKey(fact: String): String {
        val words = fact.lowercase()
            .replace(Regex("[^a-z0-9\\u0900-\\u097F ]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
        return (words.take(5).joinToString("_") + "_" + (fact.hashCode() and 0xFFFF)).take(80)
    }

    private fun read(): JSONObject {
        val raw = prefs.getString(KEY_DATA, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun write(data: JSONObject) {
        prefs.edit().putString(KEY_DATA, data.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "zed_memory"
        private const val KEY_DATA = "facts"
    }
}
