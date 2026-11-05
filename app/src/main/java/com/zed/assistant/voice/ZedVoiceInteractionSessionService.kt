package com.zed.assistant.voice

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class ZedVoiceInteractionSessionService : VoiceInteractionSessionService() {
  override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession =
    ZedVoiceInteractionSession(this)
}
