package dev.zed.assistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import dev.zed.assistant.R
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * Zed's floating presence, in the spirit of Gemini's live overlay: a small glass orb that
 * waits above every app, wakes on the voice, and unfolds into a live caption while Zed
 * talks. Tap to talk, tap while Zed is speaking to stop, drag to park it anywhere.
 */
class FloatingAssistant(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onStop: () -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var manager: WindowManager? = null
    private var card: ZedOverlayCard? = null
    private var params: WindowManager.LayoutParams? = null
    private var collapseJob: Job? = null
    private var showing = false

    val isShowing: Boolean get() = showing

    fun show(expanded: Boolean = false) {
        if (showing) {
            if (expanded) expand() else collapse()
            return
        }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return
        manager = windowManager

        val view = ZedOverlayCard(context).apply {
            onTapAction = {
                if (isSpeaking) onStop() else onTap()
            }
            onSizeChangedAction = { width, height ->
                params?.let { layout ->
                    layout.width = width
                    layout.height = height
                    runCatching { manager?.updateViewLayout(this, layout) }
                }
            }
            onDrag = { x, y ->
                params?.let { layout ->
                    val screen = context.resources.displayMetrics
                    layout.x = x.coerceIn(0, (screen.widthPixels - layout.width).coerceAtLeast(0))
                    layout.y = y.coerceIn(0, (screen.heightPixels - layout.height).coerceAtLeast(0))
                    runCatching { manager?.updateViewLayout(this, layout) }
                }
            }
        }

        val density = context.resources.displayMetrics.density
        val collapsed = (ZedOverlayCard.COLLAPSED_DP * density).toInt()
        val metrics = context.resources.displayMetrics

        val layout = WindowManager.LayoutParams(
            collapsed,
            collapsed,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - collapsed) / 2
            y = metrics.heightPixels - (170 * density).toInt()
        }

        try {
            windowManager.addView(view, layout)
        } catch (t: Throwable) {
            ZedState.log("BUBBLE", "Could not add overlay: ${t.message}")
            return
        }

        card = view
        params = layout
        showing = true

        scope.launch { ZedState.status.collect { view.statusText = it } }
        scope.launch { ZedState.partialText.collect { view.transcript = it } }
        scope.launch {
            ZedState.listening.collect {
                view.isListening = it
                if (it) expand() else scheduleCollapse()
            }
        }
        scope.launch {
            ZedState.speaking.collect {
                view.isSpeaking = it
                if (it) expand() else scheduleCollapse()
            }
        }
        scope.launch { ZedState.thinking.collect { view.isThinking = it } }

        if (expanded) expand()
    }

    fun expand() {
        collapseJob?.cancel()
        card?.expand()
    }

    fun collapse() {
        card?.collapse()
    }

    /** Folds away a few seconds after the conversation ends. */
    private fun scheduleCollapse() {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            delay(4200)
            if (!ZedState.speaking.value && !ZedState.listening.value) collapse()
        }
    }

    fun hide() {
        collapseJob?.cancel()
        val view = card
        val windowManager = manager
        if (view != null && windowManager != null) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
        card = null
        showing = false
        scope.cancel()
    }
}

/**
 * The overlay itself. Drawn by hand so the orb can morph into a caption card without any
 * layout pass fighting the animation: the window grows while the artwork scales with it.
 */
@SuppressLint("ViewConstructor")
private class ZedOverlayCard(context: Context) : FrameLayout(context) {

    var onTapAction: (() -> Unit)? = null
    var onSizeChangedAction: ((Int, Int) -> Unit)? = null

    var statusText: String = "Zed"
        set(value) { field = value; postInvalidateOnAnimation() }
    var transcript: String = ""
        set(value) { field = value; postInvalidateOnAnimation() }
    var isListening: Boolean = false
        set(value) { field = value; postInvalidateOnAnimation() }
    var isSpeaking: Boolean = false
        set(value) { field = value; postInvalidateOnAnimation() }
    var isThinking: Boolean = false
        set(value) { field = value; postInvalidateOnAnimation() }

    private val density = context.resources.displayMetrics.density
    private var progress = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(72, 255, 255, 255)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
    }
    private val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textSize = 11.5f * density
    }
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val invert = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    ).also { avatarPaint.colorFilter = it }

    private val movies = mutableMapOf<Int, Movie?>()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
    }

    fun expand() = animateProgress(1f)
    fun collapse() = animateProgress(0f)

    private fun animateProgress(target: Float) {
        if (progress == target) return
        val start = progress
        val startAt = SystemClock.uptimeMillis()
        val duration = 260f
        val runnable = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - startAt) / duration).coerceIn(0f, 1f)
                val eased = DecelerateInterpolator(1.4f).getInterpolation(t)
                progress = start + (target - start) * eased
                val width = lerp((COLLAPSED_DP * density), (CARD_DP * density), progress).toInt()
                val height = lerp((COLLAPSED_DP * density), (CARD_HEIGHT_DP * density), progress).toInt()
                onSizeChangedAction?.invoke(width, height)
                postInvalidateOnAnimation()
                if (t < 1f) postOnAnimation(this)
            }
        }
        postOnAnimation(runnable)
    }

    private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = lerp(h / 2f, 26f * density, progress)
        rect.set(0.5f * density, 0.5f * density, w - 0.5f * density, h - 0.5f * density)

        // Glass body: near-black with a white sheen that fades down the card.
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.argb(238, 10, 10, 10),
                Color.argb(232, 18, 18, 18),
                Color.argb(240, 6, 6, 6)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        // Top highlight, the signature of a glass surface.
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h * 0.6f,
            Color.argb(38, 255, 255, 255), Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        val active = isListening || isSpeaking || isThinking
        val time = SystemClock.uptimeMillis() / 1000f

        // Breathing halo while live.
        if (active && progress > 0.05f) {
            val pulse = (sin(time * 2.6f) + 1f) / 2f
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 1.5f * density
            glowPaint.color = Color.argb((40 + 70 * pulse).toInt(), 255, 255, 255)
            val inset = 3f * density
            rect.inset(inset, inset)
            val glowRadius = radius - inset
            canvas.drawRoundRect(rect, glowRadius, glowRadius, glowPaint)
            rect.inset(-inset, -inset)
        }

        val avatarSize = lerp(40f * density, 46f * density, progress)
        val avatarLeft = lerp((w - avatarSize) / 2f, 13f * density, progress)
        val avatarTop = (h - avatarSize) / 2f

        drawAvatar(canvas, avatarLeft, avatarTop, avatarSize, active, time)

        if (progress > 0.35f) {
            val alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val textLeft = avatarLeft + avatarSize + 12f * density
            val textRight = w - 14f * density
            val textWidth = (textRight - textLeft).toInt()
            if (textWidth > 0) {
                val title = if (statusText.isBlank()) "Zed" else statusText
                textPaint.alpha = (255 * alpha).toInt()
                textPaint.isFakeBoldText = true
                canvas.save()
                canvas.clipRect(textLeft, 0f, textRight, h)
                canvas.drawText(
                    TextUtils.ellipsize(title, textPaint, textWidth.toFloat(), TextUtils.TruncateAt.END)
                        .toString(),
                    textLeft,
                    h / 2f - 2f * density,
                    textPaint
                )
                textPaint.isFakeBoldText = false

                val caption = transcript.ifBlank {
                    when {
                        isSpeaking -> "Speaking — tap to stop"
                        isListening -> "Listening…"
                        isThinking -> "Working on it…"
                        else -> "Say \"zed\" to wake me"
                    }
                }
                mutedPaint.alpha = (235 * alpha).toInt()
                val layout = StaticLayout.Builder
                    .obtain(caption, 0, caption.length, mutedPaint, textWidth)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setLineSpacing(1f * density, 1f)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
                canvas.save()
                canvas.translate(textLeft, h / 2f + 3f * density)
                layout.draw(canvas)
                canvas.restore()
                canvas.restore()
            }
        }
    }

    private fun drawAvatar(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        active: Boolean,
        time: Float
    ) {
        val resources = context.resources
        val id = when {
            isThinking -> R.raw.zed_curious
            isSpeaking -> R.raw.zed_happy
            isListening -> R.raw.zed_attentive
            active -> R.raw.zed_excited
            else -> R.raw.zed_idle
        }
        val movie = movies.getOrPut(id) {
            runCatching { resources.openRawResource(id).use { Movie.decodeStream(it) } }.getOrNull()
        }

        val bubble = RectF(left, top, left + size, top + size)
        canvas.save()
        canvas.clipPath(
            android.graphics.Path().apply {
                addRoundRect(bubble, size * 0.34f, size * 0.34f, android.graphics.Path.Direction.CW)
            }
        )
        paint.shader = null
        paint.color = Color.BLACK
        canvas.drawRect(bubble, paint)
        if (movie != null) {
            val ms = ((time * 1000).toInt()) % movie.duration().coerceAtLeast(1)
            movie.setTime(ms)
            canvas.save()
            canvas.translate(left, top)
            val scale = size / movie.width().coerceAtLeast(1)
            canvas.scale(scale, scale)
            movie.draw(canvas, 0f, 0f, avatarPaint)
            canvas.restore()
        } else {
            textPaint.alpha = 255
            textPaint.isFakeBoldText = true
            textPaint.textSize = size * 0.45f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Z", bubble.centerX(), bubble.centerY() + size * 0.16f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.isFakeBoldText = false
            textPaint.textSize = 13f * density
        }
        canvas.restore()

        if (active) {
            borderPaint.alpha = 120 + (100 * ((sin(time * 3f) + 1f) / 2f)).toInt()
            postInvalidateOnAnimation()
        } else {
            borderPaint.alpha = 72
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = (layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                startY = (layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                dragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > 8f * density || abs(dy) > 8f * density) dragged = true
                if (dragged) onDrag?.invoke(startX + dx.toInt(), startY + dy.toInt())
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged) onTapAction?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    var onDrag: ((Int, Int) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragged = false

    companion object {
        const val COLLAPSED_DP = 64f
        const val CARD_DP = 300f
        const val CARD_HEIGHT_DP = 78f
    }
}

