package com.zed.assistant.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.zed.assistant.model.ChatMessage

/** Persistent conversation transcript, newest last. */
class ChatHistoryStore(context: Context) {

  private val helper = ZedDatabase(context)

  fun append(role: ChatMessage.Role, text: String) {
    val clean = text.trim()
    if (clean.isBlank()) return
    runCatching {
      helper.writableDatabase.insert(ZedDatabase.TABLE_TURNS, null, ContentValues().apply {
        put("role", role.name)
        put("text", clean)
        put("created_at", System.currentTimeMillis())
      })
    }.onFailure { Log.e(TAG, "Could not append history", it) }
  }

  fun recent(limit: Int = 60): List<ChatMessage> {
    val result = mutableListOf<ChatMessage>()
    runCatching {
      helper.readableDatabase.query(
        ZedDatabase.TABLE_TURNS,
        arrayOf("role", "text"),
        null,
        null,
        null,
        null,
        "created_at DESC, id DESC",
        limit.toString()
      ).use { cursor ->
        val roleIndex = cursor.getColumnIndexOrThrow("role")
        val textIndex = cursor.getColumnIndexOrThrow("text")
        while (cursor.moveToNext()) {
          val role = runCatching {
            ChatMessage.Role.valueOf(cursor.getString(roleIndex))
          }.getOrNull() ?: continue
          result.add(ChatMessage(role, cursor.getString(textIndex)))
        }
      }
    }.onFailure { Log.e(TAG, "Could not read history", it) }
    return result.asReversed()
  }

  fun clear() {
    runCatching { helper.writableDatabase.delete(ZedDatabase.TABLE_TURNS, null, null) }
      .onFailure { Log.e(TAG, "Could not clear history", it) }
  }

  private companion object {
    const val TAG = "ZedHistory"
  }
}
