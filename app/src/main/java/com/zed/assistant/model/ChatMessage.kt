package com.zed.assistant.model

/** A completed turn shown in the Part 7-inspired conversation screen. */
data class ChatMessage(
  val role: Role,
  val text: String
) {
  enum class Role {
    USER,
    ASSISTANT
  }
}
