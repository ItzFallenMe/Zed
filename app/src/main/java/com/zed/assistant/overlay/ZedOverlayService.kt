package com.zed.assistant.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.automation.AgentTaskEngine
import com.zed.assistant.data.ZedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Draggable floating robot head. Its face is driven by [ZedEngine.avatarState],
 * which mirrors what Zed is actually doing: listening, thinking, speaking,
 * searching, working, happy or error. The WebView is local-only.
 */
class ZedOverlayService : android.app.Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }

  private var root: FrameLayout? = null
  private var webView: WebView? = null
  private var caption: TextView? = null
  private var params: WindowManager.LayoutParams? = null

  private var avatarJob: Job? = null
  private var statusJob: Job? = null
  private var pageReady = false
  private var pendingState = "idle"
  private var attached = false

  override fun onCreate() {
    super.onCreate()
    if (!Settings.canDrawOverlays(this)) {
      Log.w(TAG, "Overlay permission missing")
      stopSelf()
      return
    }
    attach()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (!attached) attach()
    return START_STICKY
  }

  override fun onBind(intent: Intent?) = null

  override fun onDestroy() {
    avatarJob?.cancel()
    statusJob?.cancel()
    scope.cancel()
    detach()
    super.onDestroy()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun attach() {
    if (attached) return
    val preferences = ZedPreferences(this)
    val sizeDp = if (preferences.overlayToy == ROBOT) 172 else 118

    val container = FrameLayout(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      clipChildren = false
      clipToPadding = false
    }

    val web = WebView(this).apply {
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
        mediaPlaybackRequiresUserGesture = true
        builtInZoomControls = false
        displayZoomControls = false
        setSupportZoom(false)
      }
      webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          if (url?.startsWith("file:///android_asset/") != true) return
          view?.post {
            view.evaluateJavascript(
              "Boolean(window.__zedRobotReady && window.setRobotState)",
            ) { ready ->
              pageReady = ready == "true"
              if (pageReady) applyAvatarState(pendingState)
              else Log.w(TAG, "Overlay avatar page loaded without renderer readiness")
            }
          }
        }

        override fun onReceivedError(
          view: WebView?,
          request: android.webkit.WebResourceRequest?,
          error: android.webkit.WebResourceError?
        ) {
          super.onReceivedError(view, request, error)
          if (request?.isForMainFrame == true) {
            pageReady = false
            Log.e(TAG, "Overlay avatar asset failed: ${error?.description}")
          }
        }
      }
      loadUrl(avatarUrl(preferences.overlayToy))
    }

    val label = TextView(this).apply {
      setTextColor(Color.WHITE)
      textSize = 9.5f
      letterSpacing = 0.08f
      gravity = Gravity.CENTER
      maxLines = 1
      setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
      background = GradientDrawable().apply {
        setColor(Color.argb(210, 8, 8, 8))
        cornerRadius = dp(20f).toFloat()
        setStroke(dp(1f), Color.argb(48, 255, 255, 255))
      }
      text = "IDLE"
    }

    container.addView(
      web,
      FrameLayout.LayoutParams(dp(sizeDp.toFloat()), dp(sizeDp.toFloat()), Gravity.CENTER)
    )
    container.addView(
      label,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      ).apply { bottomMargin = dp(2f) }
    )

    val layout = WindowManager.LayoutParams(
      dp(sizeDp.toFloat()),
      dp(sizeDp + 24f),
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.END
      x = dp(10f)
      y = dp(120f)
    }

    val gestures = GestureDetector(
      this,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
          ZedEngine.toggle(applicationContext)
          return true
        }

        override fun onLongPress(event: MotionEvent) {
          if (AgentTaskEngine.isRunning) ZedEngine.cancelTask()
          else if (ZedEngine.isActive.value) ZedEngine.interrupt()
        }
      }
    )

    val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var startX = 0
    var startY = 0
    var dragging = false

    val touch = View.OnTouchListener { _, event ->
      gestures.onTouchEvent(event)
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = event.rawX
          downY = event.rawY
          startX = layout.x
          startY = layout.y
          dragging = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = event.rawX - downX
          val dy = event.rawY - downY
          if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
            dragging = true
          }
          if (dragging) {
            layout.x = (startX - dx.toInt()).coerceIn(0, screenWidth() - layout.width)
            layout.y = (startY + dy.toInt()).coerceIn(0, screenHeight() - layout.height)
            runCatching { windowManager.updateViewLayout(container, layout) }
          }
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
        else -> false
      }
    }
    container.setOnTouchListener(touch)
    web.setOnTouchListener { view, event ->
      if (event.actionMasked == MotionEvent.ACTION_DOWN) view.parent?.requestDisallowInterceptTouchEvent(false)
      touch.onTouch(view, event)
    }

    try {
      windowManager.addView(container, layout)
    } catch (error: Exception) {
      Log.e(TAG, "Could not attach overlay", error)
      web.destroy()
      stopSelf()
      return
    }

    root = container
    webView = web
    caption = label
    params = layout
    attached = true

    avatarJob = scope.launch {
      ZedEngine.avatarState.collectLatest { state ->
        pendingState = state
        applyAvatarState(state)
      }
    }
    statusJob = scope.launch {
      ZedEngine.status.collect { status ->
        caption?.text = status.uppercase().take(34)
      }
    }
  }

  private fun applyAvatarState(state: String) {
    val web = webView ?: return
    if (!pageReady) {
      pendingState = state
      return
    }
    val safeState = state.filter { it.isLetter() }.take(24)
    web.evaluateJavascript("window.setRobotState('$safeState');", null)
  }

  private fun avatarUrl(toy: String): String =
    if (toy == ROBOT) "file:///android_asset/robot-head/avatar.html"
    else "file:///android_asset/overlay-toys/$toy.html"

  private fun detach() {
    if (!attached) return
    attached = false
    pageReady = false
    runCatching { root?.let { windowManager.removeView(it) } }
    root = null
    webView?.destroy()
    webView = null
    caption = null
    params = null
  }

  private fun screenWidth(): Int = resources.displayMetrics.widthPixels

  private fun screenHeight(): Int = resources.displayMetrics.heightPixels

  private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

  companion object {
    private const val TAG = "ZedOverlay"
    private const val ACTION_STOP = "com.zed.assistant.STOP_OVERLAY"
    const val ROBOT = "robot-head"

    fun start(context: Context) {
      if (!Settings.canDrawOverlays(context)) return
      runCatching { context.startService(Intent(context, ZedOverlayService::class.java)) }
        .onFailure { Log.e(TAG, "Could not start overlay", it) }
    }

    fun stop(context: Context) {
      runCatching { context.stopService(Intent(context, ZedOverlayService::class.java)) }
    }

    fun sync(context: Context) {
      if (ZedPreferences(context).overlayEnabled) start(context) else stop(context)
    }
  }
}
