package com.zed.assistant.model

/** Top-level states shown by the UI and used to gate transitions. */
enum class AssistantState {
  IDLE,
  CONNECTING,
  LISTENING,
  SPEAKING,
  ERROR
}
