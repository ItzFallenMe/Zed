package com.zed.assistant.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.GestureDetector
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zed.assistant.model.AssistantState
import kotlinx.coroutines.delay

/**
 * The signature Zed particle orb.
 *
 * A local, network-free WebView renders a Three.js particle volume. The
 * orchestrator owns all assistant state; this view only paints it. Touch
 * gestures are detected natively so a tap still starts a session even while
 * the orb canvas is being dragged.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ZedOrbView(
  state: AssistantState,
  level: Float,
  themeKey: String,
  onTap: () -> Unit,
  onLongPress: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var webView by remember { mutableStateOf<WebView?>(null) }
  var pageReady by remember { mutableStateOf(false) }

  val currentOnTap by rememberUpdatedState(onTap)
  val currentOnLongPress by rememberUpdatedState(onLongPress)
  val currentLevel by rememberUpdatedState(level)
  val gestureDetector = remember(context) {
    GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(event: android.view.MotionEvent): Boolean {
          currentOnTap()
          return true
        }

        override fun onLongPress(event: android.view.MotionEvent) {
          currentOnLongPress()
        }
      }
    )
  }

  Box(
    modifier = modifier.semantics {
      contentDescription = "Zed orb. Tap to start or stop voice. Long press to interrupt."
    }
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { viewContext ->
        WebView(viewContext).apply {
          setBackgroundColor(AndroidColor.TRANSPARENT)
          setLayerType(View.LAYER_TYPE_HARDWARE, null)
          isVerticalScrollBarEnabled = false
          isHorizontalScrollBarEnabled = false
          overScrollMode = View.OVER_SCROLL_NEVER
          isLongClickable = false
          setOnLongClickListener { true }

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = true
          }

          webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              pageReady = true
            }

            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?
            ): Boolean = request?.url?.scheme != "file"
          }

          setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)
            view.onTouchEvent(event)
          }

          loadUrl("file:///android_asset/orb.html?embedded=1")
          webView = this
        }
      },
      onRelease = { view ->
        view.setOnTouchListener(null)
        view.destroy()
        if (webView === view) webView = null
      }
    )
  }

  DisposableEffect(lifecycle, webView) {
    val view = webView
    if (view == null) {
      onDispose { }
    } else {
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_RESUME -> {
            view.onResume()
            view.resumeTimers()
          }
          Lifecycle.Event.ON_PAUSE -> {
            view.onPause()
            view.pauseTimers()
          }
          else -> Unit
        }
      }
      lifecycle.addObserver(observer)
      onDispose {
        lifecycle.removeObserver(observer)
        view.onPause()
        view.pauseTimers()
      }
    }
  }

  LaunchedEffect(state, pageReady, themeKey) {
    if (!pageReady) return@LaunchedEffect
    val stateName = when (state) {
      AssistantState.IDLE, AssistantState.ERROR -> "idle"
      AssistantState.CONNECTING -> "thinking"
      AssistantState.LISTENING -> "listening"
      AssistantState.SPEAKING -> "speaking"
    }
    webView?.evaluateJavascript(
      "window.ZedOrb && window.ZedOrb.setTheme('$themeKey');" +
        "window.ZedOrb && window.ZedOrb.setState('$stateName');",
      null
    )
  }

  LaunchedEffect(webView, pageReady) {
    if (!pageReady) return@LaunchedEffect
    while (true) {
      delay(80)
      val amplitude = currentLevel.coerceIn(0f, 1f)
      webView?.evaluateJavascript(
        "window.ZedOrb && window.ZedOrb.setExternalAudio(" +
          "$amplitude,${amplitude * 0.85f},${amplitude * 0.55f});",
        null
      )
    }
  }
}
