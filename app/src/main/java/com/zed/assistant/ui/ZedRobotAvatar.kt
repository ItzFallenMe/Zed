package com.zed.assistant.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
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

/**
 * The robot-head avatar. A locally bundled canvas renderer (robot-heads, MIT)
 * shows the face for the current agent state: idle, thinking, listening,
 * speaking, searching, working, happy or error.
 *
 * The WebView cannot reach the network. Touch is detected natively so a tap
 * still starts a session while the canvas can also follow the pointer.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ZedRobotAvatar(
  state: String,
  onTap: () -> Unit,
  onLongPress: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var webView by remember { mutableStateOf<WebView?>(null) }
  var pageReady by remember { mutableStateOf(false) }
  var pageError by remember { mutableStateOf(false) }

  val currentOnTap by rememberUpdatedState(onTap)
  val currentOnLongPress by rememberUpdatedState(onLongPress)
  val gestureDetector = remember(context) {
    GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
          currentOnTap()
          return true
        }

        override fun onLongPress(event: MotionEvent) {
          currentOnLongPress()
        }
      }
    )
  }

  Box(
    modifier = modifier.semantics {
      contentDescription = "Zed robot avatar. Tap to start or stop voice. Long press to interrupt."
    }
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { viewContext ->
        WebView(viewContext).apply {
          setBackgroundColor(Color.TRANSPARENT)
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

            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: android.webkit.WebResourceError?
            ) {
              super.onReceivedError(view, request, error)
              if (request?.isForMainFrame == true) pageError = true
            }

            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?
            ): Boolean = request?.url?.scheme != "file"
          }
          setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
          }
          loadUrl("file:///android_asset/robot-head/avatar.html")
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

  LaunchedEffect(state, pageReady) {
    if (!pageReady) return@LaunchedEffect
    webView?.evaluateJavascript(
      "window.setRobotState && window.setRobotState('${state.filter { it.isLetter() }}');",
      null
    )
  }
}
