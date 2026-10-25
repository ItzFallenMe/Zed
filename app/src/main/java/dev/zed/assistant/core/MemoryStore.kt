package dev.zed.assistant.core

import android.content.Context
import dev.zed.assistant.core.memory.GeminiEmbeddings
import dev.zed.assistant.core.memory.MemorySummarizer
import dev.zed.assistant.core.memory.TextEmbedder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.math.exp

/**
 * Zed's long-term memory.
 *
 * Two kinds of memory live here:
 *  - **facts** — durable things about the user, either asked for explicitly ("remember my
 *    locker is 4412") or extracted from a finished conversation;
 *  - **episodes** — one-line summaries of past conversations, so Zed can refer back to what
 *    you did together without replaying the whole transcript.
 *
 * Every item is embedded twice: a free hashed vector computed on device, so recall always
 * works offline, and a Gemini semantic vector when a key and network are available, so
 * "where did I leave the car" still finds "parked in B2". Everything is stored in a single
 * private JSON file and can be wiped from Settings.
 */
class MemoryStore(
    context: Context,
    private val geminiKeyProvider: () -> String = { "" }
) {

    enum class Kind { FACT, EPISODE }

    data class Item(
        val id: String,
        val kind: Kind,
        val text: String,
        val at: Long,
        val source: String,
        val local: FloatArray,
        val semantic: FloatArray?
    )

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "memory.json")
    private val lock = Any()
    private var items: MutableList<Item>? = null

    // ------------------------------------------------------------- writing

    @Synchronized
    fun remember(fact: String): String? {
        val cleaned = fact.trim().trim('.').trim()
        if (cleaned.length < 3) return null
        return add(Kind.FACT, cleaned, "user")
    }

    @Synchronized
    fun addEpisode(text: String, source: String = "auto"): String? {
        val cleaned = text.trim()
        if (cleaned.length < 8) return null
        return add(Kind.EPISODE, cleaned, source)
    }

    /**
     * Compresses a finished conversation into an episode plus durable facts. Falls back to
     * storing the exchange verbatim-ish when no summary service is reachable, so memory
     * still accumulates offline.
     */
    suspend fun ingest(
        messages: List<Pair<String, String>>,
        groqKey: String,
        groqModel: String,
        geminiKey: String
    ): Int {
        if (messages.size < 4) return 0
        val transcript = messages.joinToString("\n") { (role, text) ->
            "${if (role == "user") "User" else "Zed"}: $text"
        }
        val summarizer = MemorySummarizer(groqKey, groqModel, geminiKey)
        val summary = summarizer.summarize(transcript)
        var stored = 0

        if (summary == null) {
            val lastExchange = messages.takeLast(4).joinToString(" ") { it.second }
                .replace(Regex("\\s+"), " ")
                .take(400)
            if (addEpisode("Conversation about: $lastExchange") != null) stored++
            ZedState.log("MEMORY", "Stored a local episode (no summariser available)")
            return stored
        }

        if (summary.episode.isNotBlank() && addEpisode(summary.episode) != null) stored++
        for (fact in summary.facts) {
            if (rememberInternal(fact, "auto") != null) {
                stored++
                // Store first, then enrich with the cloud vector so semantic recall improves.
                attachSemanticVector(fact)
            }
        }
        ZedState.log("MEMORY", "Kept ${summary.facts.size} fact(s) and 1 episode")
        return stored
    }

    /** Adds the cloud embedding for anything just written, in the background. */
    suspend fun attachSemanticVector(text: String) {
        val key = geminiKeyProvider()
        if (key.isBlank()) return
        val vector = GeminiEmbeddings(key).embed(text) ?: return
        synchronized(lock) {
            val list = load()
            val index = list.indexOfFirst { it.text == text }
            if (index >= 0 && list[index].semantic == null) {
                list[index] = list[index].copy(semantic = vector)
                save(list)
            }
        }
    }

    private fun rememberInternal(fact: String, source: String): String? {
        val cleaned = fact.trim().trim('.')
        if (cleaned.length < 3) return null
        return add(Kind.FACT, cleaned, source)
    }

    private fun add(kind: Kind, text: String, source: String): String? {
        synchronized(lock) {
            val list = load()
            val local = TextEmbedder.embed(text)
            val duplicate = list.firstOrNull { existing ->
                existing.text.equals(text, ignoreCase = true) ||
                    (existing.kind == kind &&
                        TextEmbedder.cosine(existing.local, local) > 0.96f)
            }
            if (duplicate != null) {
                ZedState.log("MEMORY", "Already known: ${text.take(60)}")
                return duplicate.id
            }
            val item = Item(
                id = "${System.currentTimeMillis()}-${(text.hashCode() and 0xFFFF)}",
                kind = kind,
                text = text,
                at = System.currentTimeMillis(),
                source = source,
                local = local,
                semantic = null
            )
            list += item
            save(list)
            ZedState.log("MEMORY", "Remembered (${kind.name.lowercase()}): ${text.take(70)}")
            return item.id
        }
    }

    // ------------------------------------------------------------- reading

    fun facts(): List<Item> = snapshot().filter { it.kind == Kind.FACT }

    fun episodes(): List<Item> = snapshot().filter { it.kind == Kind.EPISODE }

    fun count(): Int = snapshot().size

    /** Kept for the settings screen, which lists plain facts. */
    fun all(): Map<String, String> =
        facts().associate { it.id to it.text }

    fun summary(limit: Int = 12): String {
        val facts = facts().takeLast(limit)
        if (facts.isEmpty()) return ""
        return facts.joinToString("\n") { "- ${it.text}" }
    }

    /** Fast, offline, lexical recall. Always available. */
    fun recall(query: String, limit: Int = 6): List<String> {
        if (query.isBlank()) return facts().takeLast(limit).map { it.text }
        val vector = TextEmbedder.embed(query)
        return snapshot()
            .map { it to score(it, vector, query) }
            .filter { it.second > 0.12f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.text }
    }

    /** Semantic recall: Gemini embeddings when the key and network are there, offline otherwise. */
    suspend fun recallSemantic(query: String, limit: Int = 6): List<String> {
        if (query.isBlank()) return recall(query, limit)
        val key = geminiKeyProvider()
        if (key.isNotBlank()) {
            val queryVector = GeminiEmbeddings(key).embed(query)
            if (queryVector != null) {
                val semantic = snapshot().mapNotNull { item ->
                    val vector = item.semantic ?: return@mapNotNull null
                    item to cosine(vector, queryVector)
                }
                val merged = (semantic + recallPairs(query, limit)).distinctBy { it.first.id }
                return merged
                    .filter { it.second > 0.35f }
                    .sortedByDescending { it.second }
                    .take(limit)
                    .map { it.first.text }
            }
        }
        return recall(query, limit)
    }

    private fun recallPairs(query: String, limit: Int): List<Pair<Item, Float>> {
        val vector = TextEmbedder.embed(query)
        return snapshot()
            .map { it to score(it, vector, query) }
            .sortedByDescending { it.second }
            .take(limit)
    }

    /**
     * The block injected into the system prompt: everything durable, plus the last few
     * conversations. Recent episodes first so continuity feels natural.
     */
    fun promptBlock(limit: Int = 16): String {
        val facts = facts().takeLast(limit)
        val recent = episodes().takeLast(3)
        if (facts.isEmpty() && recent.isEmpty()) return ""
        return buildString {
            if (facts.isNotEmpty()) {
                appendLine("Things you know about them:")
                facts.forEach { appendLine("- ${it.text}") }
            }
            if (recent.isNotEmpty()) {
                if (facts.isNotEmpty()) appendLine()
                appendLine("Conversations you remember:")
                recent.forEach { appendLine("- ${it.text}") }
            }
        }.trim()
    }

    fun forget(query: String): Int {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return 0
        synchronized(lock) {
            val list = load()
            val matches = list.filter {
                it.text.lowercase().contains(needle) ||
                    needle.split(' ').filter { word -> word.length > 3 }
                        .any { word -> it.text.lowercase().contains(word) }
            }
            if (matches.isEmpty()) return 0
            list.removeAll(matches)
            save(list)
            ZedState.log("MEMORY", "Forgot ${matches.size} item(s)")
            return matches.size
        }
    }

    fun clear() {
        synchronized(lock) {
            items = mutableListOf()
            runCatching { file.delete() }
            appContext.getSharedPreferences("zed_memory", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        ZedState.log("MEMORY", "Memory cleared")
    }

    // ------------------------------------------------------------- storage

    private fun snapshot(): List<Item> = synchronized(lock) { load().toList() }

    private fun score(item: Item, queryVector: FloatArray, query: String): Float {
        val similarity = TextEmbedder.cosine(item.local, queryVector)
        val days = ((System.currentTimeMillis() - item.at) / 86_400_000f).coerceAtLeast(0f)
        val recency = 0.12f * exp(-days / 45f)
        val exact = if (query.isNotBlank() &&
            item.text.lowercase().contains(query.lowercase().trim())
        ) 0.35f else 0f
        val kindBoost = if (item.kind == Kind.FACT) 0.04f else 0f
        return similarity + recency + exact + kindBoost
    }

    private fun load(): MutableList<Item> {
        items?.let { return it }
        val loaded = mutableListOf<Item>()
        if (file.exists()) {
            runCatching {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    val json = array.optJSONObject(i) ?: continue
                    val local = decode(json.optString("e")) ?: continue
                    loaded += Item(
                        id = json.optString("id"),
                        kind = if (json.optString("k") == "EPISODE") Kind.EPISODE else Kind.FACT,
                        text = json.optString("t"),
                        at = json.optLong("at"),
                        source = json.optString("src", "user"),
                        local = local,
                        semantic = decode(json.optString("s"))
                    )
                }
            }.onFailure {
                ZedState.log("MEMORY", "Could not read memory: ${it.message}")
            }
        }
        if (loaded.isEmpty()) migrateLegacy(loaded)
        items = loaded
        return loaded
    }

    private fun save(list: List<Item>) {
        runCatching {
            val array = JSONArray()
            list.forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("k", item.kind.name)
                        .put("t", item.text)
                        .put("at", item.at)
                        .put("src", item.source)
                        .put("e", encode(item.local))
                        .apply { if (item.semantic != null) put("s", encode(item.semantic)) }
                )
            }
            val temp = File(file.parentFile, "memory.json.tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(file)) {
                file.writeText(array.toString())
                temp.delete()
            }
        }.onFailure { ZedState.log("MEMORY", "Could not save memory: ${it.message}") }
    }

    /** Imports the old flat fact list so upgrades keep what was already remembered. */
    private fun migrateLegacy(into: MutableList<Item>) {
        val prefs = appContext.getSharedPreferences("zed_memory", Context.MODE_PRIVATE)
        val raw = prefs.getString("facts", null) ?: return
        runCatching {
            val json = JSONObject(raw)
            json.keys().forEach { key ->
                val text = json.optString(key).trim()
                if (text.length >= 3) {
                    into += Item(
                        id = "legacy-$key",
                        kind = Kind.FACT,
                        text = text,
                        at = prefs.getLong("at_$key", System.currentTimeMillis()),
                        source = "user",
                        local = TextEmbedder.embed(text),
                        semantic = null
                    )
                }
            }
            if (into.isNotEmpty()) {
                save(into)
                ZedState.log("MEMORY", "Migrated ${into.size} old memory item(s)")
            }
        }
    }

    private fun encode(vector: FloatArray): String {
        val buffer = ByteBuffer.allocate(vector.size * 4)
        vector.forEach { buffer.putFloat(it) }
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    private fun decode(encoded: String): FloatArray? {
        if (encoded.isBlank()) return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            val buffer = ByteBuffer.wrap(bytes)
            FloatArray(bytes.size / 4) { buffer.float }
        }.getOrNull()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator == 0f) 0f else dot / denominator
    }
}
