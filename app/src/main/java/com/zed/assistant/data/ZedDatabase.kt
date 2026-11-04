package com.zed.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One database so history and memory share a single connection and schema version. */
class ZedDatabase(context: Context) : SQLiteOpenHelper(
  context.applicationContext,
  "zed.db",
  null,
  DATABASE_VERSION
) {

  companion object {
    private const val DATABASE_VERSION = 1
    const val TABLE_TURNS = "chat_turns"
    const val TABLE_MEMORIES = "memories"
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE $TABLE_TURNS (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        role TEXT NOT NULL,
        text TEXT NOT NULL,
        created_at INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX idx_turns_created ON $TABLE_TURNS(created_at)")
    db.execSQL(
      """
      CREATE TABLE $TABLE_MEMORIES (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        text TEXT NOT NULL,
        category TEXT NOT NULL,
        created_at INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX idx_memories_created ON $TABLE_MEMORIES(created_at)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Version 1 is the initial schema. Future migrations must be additive.
  }
}
