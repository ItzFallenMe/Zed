package com.zed.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.model.AssistantState
import com.zed.assistant.model.ChatMessage
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

  val state: StateFlow<AssistantState> = ZedEngine.state
  val status: StateFlow<String> = ZedEngine.status
  val userText: StateFlow<String> = ZedEngine.userText
  val zedText: StateFlow<String> = ZedEngine.zedText
  val actionNote: StateFlow<String?> = ZedEngine.actionNote
  val errorMessage: StateFlow<String?> = ZedEngine.errorMessage
  val isActive: StateFlow<Boolean> = ZedEngine.isActive
  val level: StateFlow<Float> = ZedEngine.level
  val chatTurns: StateFlow<List<ChatMessage>> = ZedEngine.chatTurns

  init {
    ZedEngine.initialize(application)
  }

  fun toggleSession() {
    ZedEngine.toggle(getApplication())
  }

  fun stopSession() {
    if (ZedEngine.isActive.value) ZedEngine.stop()
  }

  fun interrupt() {
    ZedEngine.interrupt()
  }

  fun dismissError() {
    ZedEngine.dismissError()
  }

  fun testSpeaker() {
    ZedEngine.testSpeaker(getApplication())
  }

  fun hasApiKey(): Boolean = ZedEngine.hasApiKey(getApplication())

  fun sendText(text: String) {
    ZedEngine.sendText(getApplication(), text)
  }

  fun clearConversation() {
    ZedEngine.clearConversation()
  }
}
