package com.zed.assistant.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * System-selected assistant entry point. Android keeps the selected service
 * available for assistant activation; the session owns the visible UI.
 */
class ZedVoiceInteractionService : VoiceInteractionService() {
  override fun onCreate() {
    super.onCreate()
    instance = this
  }

  override fun onReady() {
    super.onReady()
    instance = this
    Log.i(TAG, "Zed is ready as the selected system assistant")
  }

  override fun onDestroy() {
    if (instance === this) instance = null
    super.onDestroy()
  }

  fun showZedSession(fromWakeWord: Boolean = false) {
    val args = Bundle().apply {
      putBoolean(ZedVoiceInteractionSession.ARG_WAKE_WORD, fromWakeWord)
    }
    showSession(args, 0)
  }

  companion object {
    private const val TAG = "ZedVoiceAssistant"
    @Volatile
    private var instance: ZedVoiceInteractionService? = null

    /** Called only after the local wake-word listener has released AudioRecord. */
    fun requestShow(fromWakeWord: Boolean = true): Boolean {
      val service = instance ?: return false
      return runCatching {
        service.showZedSession(fromWakeWord)
        true
      }.getOrDefault(false)
    }
  }
}
