package dev.zed.assistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * The floating assistant, in the same spirit as the Gemini overlay: a small glass orb
 * that sits above every app, drags anywhere, and expands into a live caption while Zed
 * listens or speaks. Tap it to talk; tap again to stop.
 */
class FloatingAssistant(
    private val context: Context,
    private val onTap: () -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var root: LinearLayout? = null
    private var caption: TextView? = null
    private var orb: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var showing = false

    private var statusText: String = ""
    private var partialText: String = ""
    private var isListening = false
    private var isSpeaking = false

    fun show() {
        if (showing) return
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = manager

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val captionView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxWidth = dp(220)
            maxLines = 3
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.argb(190, 18, 18, 18))
                setStroke(dp(1), Color.argb(70, 255, 255, 255))
            }
            visibility = View.GONE
        }
        val orbView = BubbleView(context).apply {
            setOnTouchListener(dragListener())
        }

        container.addView(captionView)
        container.addView(
            orbView,
            LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(8) }
        )

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(280)
        }

        try {
            manager.addView(container, layoutParams)
        } catch (t: Throwable) {
            ZedState.log("BUBBLE", "Could not add overlay: ${t.message}")
            return
        }

        root = container
        caption = captionView
        orb = orbView
        params = layoutParams
        showing = true

        scope.launch {
            ZedState.status.collect {
                statusText = it
                renderCaption()
            }
        }
        scope.launch {
            ZedState.partialText.collect {
                partialText = it
                renderCaption()
            }
        }
        scope.launch {
            ZedState.listening.collect {
                isListening = it
                orbView.isActive = it || isSpeaking
                renderCaption()
            }
        }
        scope.launch {
            ZedState.speaking.collect {
                isSpeaking = it
                orbView.isActive = it || isListening
                renderCaption()
            }
        }
        renderCaption()
    }

    fun hide() {
        val manager = windowManager
        val view = root
        if (manager != null && view != null) {
            runCatching { manager.removeView(view) }
        }
        root = null
        caption = null
        orb = null
        showing = false
        scope.cancel()
    }

    val isShowing: Boolean get() = showing

    private fun renderCaption() {
        val text = partialText.ifBlank {
            when {
                isListening -> "Listening…"
                isSpeaking -> "Speaking…"
                statusText.isNotBlank() && statusText != "Standby" -> statusText
                else -> ""
            }
        }
        val view = caption ?: return
        if (text.isBlank()) {
            view.visibility = View.GONE
        } else {
            view.text = text
            view.visibility = View.VISIBLE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun dragListener(): View.OnTouchListener {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        return View.OnTouchListener { _, event ->
            val layout = params ?: return@OnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) dragged = true
                    layout.x = startX + dx
                    layout.y = startY + dy
                    runCatching { windowManager?.updateViewLayout(root, layout) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Glass orb with three breathing bars. */
    private class BubbleView(context: Context) : View(context) {

        private val density = context.resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(44, 255, 255, 255)
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = Color.argb(90, 255, 255, 255)
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

        private var phase = 0f

        var isActive: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                if (value) postOnAnimation(ticker) else invalidate()
            }

        private val ticker = object : Runnable {
            override fun run() {
                phase += 0.16f
                invalidate()
                if (isActive) postOnAnimation(this)
            }
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) / 2f - 2f * density
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, ringPaint)

            val barWidth = radius * 0.16f
            val gap = barWidth * 1.9f
            for (index in -1..1) {
                val wave = if (isActive) {
                    (sin((phase + index * 0.7f).toDouble()) + 1.0).toFloat() / 2f
                } else {
                    0.5f
                }
                val barHeight = radius * (0.55f + 0.7f * wave)
                val left = cx + index * gap - barWidth / 2f
                canvas.drawRoundRect(
                    left,
                    cy - barHeight / 2f,
                    left + barWidth,
                    cy + barHeight / 2f,
                    barWidth / 2f,
                    barWidth / 2f,
                    barPaint
                )
            }
        }
    }
}
