package com.zed.assistant.voice

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.zed.assistant.ai.ZedEngine

/**
 * System-owned assistant panel shown above the current app. Conversation audio
 * and Gemini Live remain owned by the existing application-scoped ZedEngine.
 */
class ZedVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
  private var root: LinearLayout? = null
  private var status: TextView? = null
  private var lastActive = false

  override fun onCreateContentView(): View {
    val panel = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(28, 24, 28, 24)
      setBackgroundColor(Color.rgb(16, 22, 34))
    }
    val title = TextView(context).apply {
      text = "Z E D"
      textSize = 22f
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
    }
    val subtitle = TextView(context).apply {
      text = "Your assistant"
      textSize = 13f
      setTextColor(Color.LTGRAY)
      gravity = Gravity.CENTER
      setPadding(0, 6, 0, 18)
    }
    status = TextView(context).apply {
      text = "Ready when you are"
      textSize = 16f
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      setPadding(0, 12, 0, 20)
    }
    val close = Button(context).apply {
      text = "End conversation"
      setOnClickListener {
        ZedEngine.stop()
        finish()
      }
    }
    panel.addView(title, LinearLayout.LayoutParams(-1, -2))
    panel.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
    panel.addView(status, LinearLayout.LayoutParams(-1, -2))
    panel.addView(close, LinearLayout.LayoutParams(-1, -2))
    root = panel
    return panel
  }

  override fun onShow(args: Bundle?, showFlags: Int) {
    super.onShow(args, showFlags)
    status?.text = "Connecting…"
    if (!ZedEngine.isActive.value) {
      ZedEngine.start(context, fromWakeWord = args?.getBoolean(ARG_WAKE_WORD, false) == true)
    }
    refreshStatus()
  }

  override fun onHide() {
    ZedEngine.stop()
    root = null
    status = null
    super.onHide()
  }

  private fun refreshStatus() {
    val current = ZedEngine.status.value
    status?.text = current
    status?.postDelayed({ if (root != null) refreshStatus() }, 700L)
  }

  companion object {
    const val ARG_WAKE_WORD = "zed_wake_word"
  }
}
