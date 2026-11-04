package com.zed.assistant.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import java.util.Locale

data class Memory(
  val id: Long,
  val text: String,
  val category: String,
  val createdAt: Long
)

/**
 * Small, explicit long-term memory. Zed writes only what the user asks it to
 * remember (or explicitly confirms), and can forget it again by keyword.
 */
class MemoryStore(context: Context) {

  private val helper = ZedDatabase(context)

  fun add(rawText: String, requestedCategory: String? = null): Memory? {
    val text = rawText.trim().take(500)
    if (text.isBlank()) return null
    val category = requestedCategory?.trim()?.takeIf { it.isNotBlank() } ?: categorize(text)
    val createdAt = System.currentTimeMillis()
    val id = runCatching {
      helper.writableDatabase.insert(ZedDatabase.TABLE_MEMORIES, null, ContentValues().apply {
        put("text", text)
        put("category", category)
        put("created_at", createdAt)
      })
    }.onFailure { Log.e(TAG, "Could not save memory", it) }.getOrDefault(-1L)
    return if (id > 0) Memory(id, text, category, createdAt) else null
  }

  fun all(limit: Int = 100): List<Memory> = query(null, limit)

  fun search(query: String, limit: Int = 20): List<Memory> {
    val clean = query.trim()
    if (clean.isBlank()) return emptyList()
    return query("%${escapeLike(clean)}%", limit)
  }

  fun delete(id: Long): Boolean = runCatching {
    helper.writableDatabase.delete(ZedDatabase.TABLE_MEMORIES, "id = ?", arrayOf(id.toString())) > 0
  }.getOrDefault(false)

  fun deleteByKeyword(query: String): Int = runCatching {
    helper.writableDatabase.delete(
      ZedDatabase.TABLE_MEMORIES,
      "text LIKE ? ESCAPE '\\'",
      arrayOf("%${escapeLike(query.trim())}%")
    )
  }.getOrDefault(0)

  fun clear() {
    runCatching { helper.writableDatabase.delete(ZedDatabase.TABLE_MEMORIES, null, null) }
      .onFailure { Log.e(TAG, "Could not clear memories", it) }
  }

  /** Compact context block sent with every Live session. */
  fun promptContext(limit: Int = 20): String =
    all(limit).joinToString("\n") { "- ${it.text}" }

  private fun query(selectionArg: String?, limit: Int): List<Memory> {
    val result = mutableListOf<Memory>()
    val (selection, args) = if (selectionArg == null) {
      null to null
    } else {
      "text LIKE ? ESCAPE '\\'" to arrayOf(selectionArg)
    }
    runCatching {
      helper.readableDatabase.query(
        ZedDatabase.TABLE_MEMORIES,
        arrayOf("id", "text", "category", "created_at"),
        selection,
        args,
        null,
        null,
        "created_at DESC, id DESC",
        limit.toString()
      ).use { cursor ->
        while (cursor.moveToNext()) {
          result.add(
            Memory(
              id = cursor.getLong(0),
              text = cursor.getString(1),
              category = cursor.getString(2),
              createdAt = cursor.getLong(3)
            )
          )
        }
      }
    }.onFailure { Log.e(TAG, "Could not read memories", it) }
    return result
  }

  /** Escape SQL LIKE metacharacters so a keyword is always treated literally. */
  private fun escapeLike(value: String): String =
    value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

  private fun categorize(text: String): String {
    val lower = text.lowercase(Locale.ROOT)
    return when {
      listOf("favorite", "prefer", "like", "pasand", "song", "music").any(lower::contains) -> "preferences"
      listOf("routine", "gym", "work", "wake up", "sleep", "birthday").any(lower::contains) -> "routine"
      listOf("my name", "i am", "mera", "meri", "live in", "age").any(lower::contains) -> "personal"
      listOf("call", "contact", "phone", "number", "friend", "brother", "sister").any(lower::contains) -> "contacts"
      else -> "notes"
    }
  }

  private companion object {
    const val TAG = "ZedMemory"
  }
}
