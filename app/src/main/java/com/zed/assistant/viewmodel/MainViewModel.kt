package com.zed.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.model.AssistantState
import com.zed.assistant.model.ChatMessage
import com.zed.assistant.ui.theme.ZedThemeMode
import com.zed.assistant.ui.theme.ZedThemeRepository
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
  val avatarState: StateFlow<String> = ZedEngine.avatarState
  val themeMode: StateFlow<ZedThemeMode> = ZedThemeRepository.mode
  val liquidGlass: StateFlow<Boolean> = ZedThemeRepository.liquidGlass

  init {
    ZedEngine.initialize(application)
    ZedThemeRepository.initialize(application)
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

  fun setThemeMode(mode: ZedThemeMode) {
    ZedThemeRepository.set(getApplication(), mode)
  }

  fun setLiquidGlass(enabled: Boolean) {
    ZedThemeRepository.setLiquidGlass(getApplication(), enabled)
  }

  fun setScreenContext(enabled: Boolean) {
    ZedEngine.setScreenContext(getApplication(), enabled)
  }


}
