package com.zed.assistant.voice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.zed.assistant.ai.ZedEngine

/**
 * System-managed voice assistant panel. Keeps the user in the app they were
 * using while showing Zed's live avatar, status and latest conversation turns.
 */
class ZedVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
  private val handler = Handler(Looper.getMainLooper())
  private var root: LinearLayout? = null
  private var statusView: TextView? = null
  private var userView: TextView? = null
  private var zedView: TextView? = null
  private var avatarView: WebView? = null
  private var refreshTask: Runnable? = null
  private var refreshToken = 0

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreateContentView(): View {
    val panel = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(24, 22, 24, 20)
      setBackgroundColor(Color.rgb(13, 18, 30))
    }
    val header = TextView(context).apply {
      text = "ZED"
      textSize = 19f
      letterSpacing = 0.28f
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      setPadding(0, 0, 0, 4)
    }
    val hint = TextView(context).apply {
      text = "Your assistant · listening in the background"
      textSize = 12f
      setTextColor(Color.rgb(165, 180, 205))
      gravity = Gravity.CENTER
      setPadding(0, 0, 0, 10)
    }
    val avatar = WebView(context).apply {
      setBackgroundColor(Color.TRANSPARENT)
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = false
        allowFileAccess = true
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        allowContentAccess = false
        blockNetworkLoads = true
        cacheMode = WebSettings.LOAD_NO_CACHE
      }
      webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
          view?.evaluateJavascript(
            "window.setRobotOptions({shape:'rectangle',size:184,color:'#26384f',trimColor:'#8ca9c8',screenColor:'#dcecff',speed:1.0,interactive:true,floorShadow:true});window.setRobotState('listening');",
            null
          )
        }
      }
      loadUrl("file:///android_asset/robot-head/avatar.html")
    }
    statusView = TextView(context).apply {
      text = "Connecting…"
      textSize = 14f
      setTextColor(Color.rgb(190, 211, 241))
      gravity = Gravity.CENTER
      setPadding(0, 4, 0, 12)
    }
    userView = TextView(context).apply {
      textSize = 13f
      setTextColor(Color.rgb(165, 180, 205))
      gravity = Gravity.START
      maxLines = 3
      setPadding(0, 4, 0, 4)
    }
    zedView = TextView(context).apply {
      textSize = 15f
      setTextColor(Color.WHITE)
      gravity = Gravity.START
      maxLines = 5
      setPadding(0, 4, 0, 14)
    }
    val close = Button(context).apply {
      text = "End conversation"
      setOnClickListener {
        stopRefresh()
        ZedEngine.stop()
        finish()
      }
    }

    panel.addView(header, LinearLayout.LayoutParams(-1, -2))
    panel.addView(hint, LinearLayout.LayoutParams(-1, -2))
    panel.addView(avatar, LinearLayout.LayoutParams(-1, 188))
    panel.addView(statusView, LinearLayout.LayoutParams(-1, -2))
    panel.addView(userView, LinearLayout.LayoutParams(-1, -2))
    panel.addView(zedView, LinearLayout.LayoutParams(-1, -2))
    panel.addView(close, LinearLayout.LayoutParams(-1, -2))
    root = panel
    avatarView = avatar
    return panel
  }

  override fun onShow(args: Bundle?, showFlags: Int) {
    super.onShow(args, showFlags)
    if (!ZedEngine.isActive.value) {
      ZedEngine.start(context, fromWakeWord = args?.getBoolean(ARG_WAKE_WORD, false) == true)
    }
    startRefresh()
  }

  override fun onHide() {
    stopRefresh()
    ZedEngine.stop()
    avatarView?.apply {
      stopLoading()
      loadUrl("about:blank")
      destroy()
    }
    avatarView = null
    root = null
    statusView = null
    userView = null
    zedView = null
    super.onHide()
  }

  private fun startRefresh() {
    stopRefresh()
    val token = ++refreshToken
    val task = object : Runnable {
      override fun run() {
        if (token != refreshToken || root == null) return
        statusView?.text = ZedEngine.status.value
        val user = ZedEngine.userText.value
        val assistant = ZedEngine.zedText.value
        userView?.text = if (user.isBlank()) "" else "YOU  ·  $user"
        zedView?.text = if (assistant.isBlank()) "" else "ZED  ·  $assistant"
        val avatarState = ZedEngine.avatarState.value
          .filter { it.isLetter() }
          .take(24)
          .ifBlank { "listening" }
        avatarView?.evaluateJavascript("window.setRobotState('$avatarState');", null)
        handler.postDelayed(this, REFRESH_MS)
      }
    }
    refreshTask = task
    handler.post(task)
  }

  private fun stopRefresh() {
    refreshToken++
    refreshTask?.let(handler::removeCallbacks)
    refreshTask = null
  }

  companion object {
    const val ARG_WAKE_WORD = "zed_wake_word"
    private const val REFRESH_MS = 350L
  }
}
