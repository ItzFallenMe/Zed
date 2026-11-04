package com.zed.assistant.model

/** A completed turn shown in the conversation screen. */
data class ChatMessage(
  val role: Role,
  val text: String
) {
  enum class Role {
    USER,
    ASSISTANT
  }
}
