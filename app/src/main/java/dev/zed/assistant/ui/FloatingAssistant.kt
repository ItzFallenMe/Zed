package dev.zed.assistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The quick actions on the floating menu. */
enum class OverlayAction { TORCH, GUARD, SCREEN, APP }

/**
 * Zed's floating presence.
 *
 * Collapsed it is just the living avatar — a small ball that breathes and blinks wherever you
 * are. When the wake word (or the orb itself) starts a conversation it unfolds into a menu:
 * what Zed is doing, what it heard, and four one-tap actions it can actually perform. Drag it
 * anywhere; tap it to talk, or tap it while it is speaking to cut it off.
 */
class FloatingAssistant(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onStop: () -> Unit,
    private val onAction: (OverlayAction) -> Unit = {}
) {

    private var scope = newScope()
    private var manager: WindowManager? = null
    private var card: ZedOverlayCard? = null
    private var params: WindowManager.LayoutParams? = null
    private var collapseJob: Job? = null
    private var showing = false

    val isShowing: Boolean get() = showing

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(expanded: Boolean = false) {
        if (showing) {
            if (expanded) expand() else collapse()
            return
        }
        // hide() cancels the collectors' scope; rebuild it or the orb comes back mute.
        if (!scope.isActive) scope = newScope()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return
        manager = windowManager

        val view = ZedOverlayCard(context).apply {
            onTapAction = {
                if (isSpeaking) onStop() else onTap()
            }
            onActionSelected = { action -> onAction(action) }
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
        params = null
        showing = false
        scope.cancel()
    }
}

/**
 * The overlay itself. Drawn by hand so the ball can grow into a menu without a layout pass
 * fighting the animation: the window grows while the artwork scales with it.
 */
@SuppressLint("ViewConstructor")
private class ZedOverlayCard(context: Context) : FrameLayout(context) {

    var onTapAction: (() -> Unit)? = null
    var onActionSelected: ((OverlayAction) -> Unit)? = null
    var onSizeChangedAction: ((Int, Int) -> Unit)? = null
    var onDrag: ((Int, Int) -> Unit)? = null

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
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(26, 255, 255, 255)
    }
    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(60, 255, 255, 255)
    }
    private val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        textSize = 11f * density
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val pillRect = RectF()
    private val pillRects = ArrayList<Pair<OverlayAction, RectF>>(4)

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
        val duration = 300f
        val runnable = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - startAt) / duration).coerceIn(0f, 1f)
                val eased = DecelerateInterpolator(1.4f).getInterpolation(t)
                progress = start + (target - start) * eased
                val width = lerp(COLLAPSED_DP * density, CARD_DP * density, progress).toInt()
                val height = lerp(COLLAPSED_DP * density, CARD_HEIGHT_DP * density, progress).toInt()
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
        val mood = when {
            isThinking -> BloubAvatar.Mood.THINKING
            isSpeaking -> BloubAvatar.Mood.SPEAKING
            isListening -> BloubAvatar.Mood.LISTENING
            else -> BloubAvatar.Mood.IDLE
        }

        val avatarRadius = lerp(24f * density, 25f * density, progress)
        val avatarCx = lerp(w / 2f, 14f * density + avatarRadius, progress)
        val avatarCy = lerp(h / 2f, 40f * density, progress)

        val haloAlpha = if (active) {
            (26 + 34 * ((kotlin.math.sin(time * 2.6f) + 1f) / 2f)).toInt()
        } else {
            0
        }
        BloubAvatar.draw(
            canvas = canvas,
            cx = avatarCx,
            cy = avatarCy,
            radius = avatarRadius,
            mood = mood,
            time = time,
            bodyColor = Color.WHITE,
            eyeColor = Color.argb(255, 8, 8, 8),
            haloColor = if (active) Color.argb(haloAlpha, 255, 255, 255) else Color.TRANSPARENT
        )

        if (progress > 0.3f) {
            val alpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
            val textLeft = avatarCx + avatarRadius + 12f * density
            val textRight = w - 14f * density
            val textWidth = (textRight - textLeft).toInt()
            if (textWidth > 0) {
                val title = if (statusText.isBlank()) "Zed" else statusText
                textPaint.alpha = (255 * alpha).toInt()
                textPaint.isFakeBoldText = true
                canvas.save()
                canvas.clipRect(textLeft, 0f, textRight, h * 0.62f)
                canvas.drawText(
                    TextUtils.ellipsize(title, textPaint, textWidth.toFloat(), TextUtils.TruncateAt.END)
                        .toString(),
                    textLeft,
                    34f * density,
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
                canvas.translate(textLeft, 39f * density)
                layout.draw(canvas)
                canvas.restore()
                canvas.restore()
            }
        }

        if (progress > 0.55f) {
            drawActions(canvas, w, h, ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f))
        }

        // Keep the ball alive: slow frames when parked, full rate while it has company.
        if (isAttachedToWindow) {
            postInvalidateDelayed(if (active || progress > 0.02f) 16L else 90L)
        }
    }

    private fun drawActions(canvas: Canvas, w: Float, h: Float, alpha: Float) {
        val rects = actionRects(w, h)
        pillRects.clear()
        pillTextPaint.alpha = (255 * alpha).toInt()
        rects.forEach { (action, box) ->
            pillRects.add(action to RectF(box))
            pillPaint.alpha = (22 * alpha).toInt()
            pillBorderPaint.alpha = (60 * alpha).toInt()
            canvas.drawRoundRect(box, box.height() / 2f, box.height() / 2f, pillPaint)
            canvas.drawRoundRect(box, box.height() / 2f, box.height() / 2f, pillBorderPaint)
            val label = actionLabel(action)
            val textWidth = pillTextPaint.measureText(label)
            canvas.drawText(
                label,
                box.centerX() - textWidth / 2f,
                box.centerY() + pillTextPaint.textSize * 0.36f,
                pillTextPaint
            )
        }
    }

    private fun actionRects(w: Float, h: Float): List<Pair<OverlayAction, RectF>> {
        val actions = listOf(
            OverlayAction.TORCH to "Torch",
            OverlayAction.GUARD to "Guard",
            OverlayAction.SCREEN to "Screen",
            OverlayAction.APP to "App"
        )
        val gap = 6f * density
        val padding = 11f * density
        val height = 25f * density
        val top = h - height - 10f * density
        var x = 14f * density
        val out = ArrayList<Pair<OverlayAction, RectF>>(actions.size)
        actions.forEach { (action, label) ->
            val width = pillTextPaint.measureText(label) + padding * 2f
            // Shrink the last pill rather than drawing outside the card.
            val right = (x + width).coerceAtMost(w - 14f * density)
            out += action to RectF(x, top, right, top + height)
            x = right + gap
        }
        return out
    }

    private fun actionLabel(action: OverlayAction): String = when (action) {
        OverlayAction.TORCH -> "Torch"
        OverlayAction.GUARD -> "Guard"
        OverlayAction.SCREEN -> "Screen"
        OverlayAction.APP -> "App"
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
                pressedAction = if (progress > 0.6f) {
                    val local = localPoint(event)
                    if (local != null) actionAt(local.first, local.second) else null
                } else {
                    null
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > 8f * density || abs(dy) > 8f * density) {
                    dragged = true
                    pressedAction = null
                }
                if (dragged) onDrag?.invoke(startX + dx.toInt(), startY + dy.toInt())
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = pressedAction
                pressedAction = null
                if (!dragged) {
                    if (action != null) {
                        onActionSelected?.invoke(action)
                    } else {
                        onTapAction?.invoke()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedAction = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun localPoint(event: MotionEvent): Pair<Float, Float>? {
        // The window is not moved by the system during a drag here, so raw coordinates map
        // onto the view once the window offset is removed.
        val layout = layoutParams as? WindowManager.LayoutParams ?: return null
        return (event.rawX - layout.x) to (event.rawY - layout.y)
    }

    private fun actionAt(x: Float, y: Float): OverlayAction? =
        pillRects.firstOrNull { it.second.contains(x, y) }?.first

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragged = false
    private var pressedAction: OverlayAction? = null

    companion object {
        const val COLLAPSED_DP = 64f
        const val CARD_DP = 316f
        const val CARD_HEIGHT_DP = 128f
    }
}
