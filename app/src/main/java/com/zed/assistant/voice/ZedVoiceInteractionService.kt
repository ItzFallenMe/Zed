package com.zed.assistant.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * System-selected assistant entry point. Keep this service lightweight; the
 * visible assistant experience belongs to ZedVoiceInteractionSession.
 */
class ZedVoiceInteractionService : VoiceInteractionService() {
  override fun onReady() {
    super.onReady()
    Log.i(TAG, "Zed is ready as the selected system assistant")
  }

  companion object {
    private const val TAG = "ZedVoiceAssistant"
  }
}
