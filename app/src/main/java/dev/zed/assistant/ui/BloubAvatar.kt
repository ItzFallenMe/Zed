package dev.zed.assistant.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zed's floating face.
 *
 * The shape language is adapted from [bloub](https://github.com/jeremy-prt/bloub) (MIT), a
 * measured recreation of the x.ai bot: one filled ball with a wobbling radial silhouette and
 * two capsule eyes that live on its surface. It is drawn straight onto a Canvas so the
 * overlay can morph it without a bitmap, a GIF or a layout pass.
 *
 * The ball is white on Zed's black glass, with dark eyes — the inverse of the original, so it
 * belongs to this app's monochrome identity.
 */
object BloubAvatar {

    enum class Mood { IDLE, LISTENING, SPEAKING, THINKING, ALERT, SLEEPY }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val eyeRect = RectF()

    /**
     * Draws the avatar centred on ([cx], [cy]) with the given [radius]. [time] is in seconds
     * and drives every motion, so the image is a pure function of it.
     */
    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        mood: Mood,
        time: Float,
        bodyColor: Int = Color.WHITE,
        eyeColor: Int = Color.BLACK,
        haloColor: Int = Color.TRANSPARENT
    ) {
        if (radius <= 0f) return

        // Idle breathing, and a livelier wobble when it has something to say.
        val wobble = when (mood) {
            Mood.SPEAKING -> 0.052f
            Mood.LISTENING -> 0.026f
            Mood.THINKING -> 0.018f
            Mood.ALERT -> 0.03f
            Mood.SLEEPY -> 0.008f
            Mood.IDLE -> 0.014f
        }
        val speed = when (mood) {
            Mood.SPEAKING -> 7.2f
            Mood.LISTENING -> 2.4f
            Mood.ALERT -> 3.2f
            Mood.THINKING -> 1.6f
            else -> 1.1f
        }
        val breathe = 1f + 0.018f * sin(time * 1.25f)
        val bob = if (mood == Mood.SPEAKING) sin(time * 5.4f) * radius * 0.03f else 0f

        if (haloColor != Color.TRANSPARENT) {
            haloPaint.color = haloColor
            haloPaint.strokeWidth = radius * 0.09f
            canvas.drawCircle(cx, cy + bob, radius * 1.06f, haloPaint)
        }

        buildBody(cx, cy + bob, radius, wobble, speed, breathe, time)
        bodyPaint.color = bodyColor
        canvas.drawPath(path, bodyPaint)

        drawEyes(canvas, cx, cy + bob, radius, mood, time, eyeColor)
    }

    // ------------------------------------------------------------------ body

    private fun buildBody(
        cx: Float,
        cy: Float,
        radius: Float,
        wobble: Float,
        speed: Float,
        breathe: Float,
        time: Float
    ) {
        val steps = 64
        path.reset()
        var firstX = 0f
        var firstY = 0f
        var prevX = 0f
        var prevY = 0f

        for (i in 0..steps) {
            val angle = (i.toFloat() / steps) * TWO_PI
            val r = radius * (
                1f +
                    wobble * sin(angle * 3f + time * speed) +
                    wobble * 0.45f * sin(angle * 5f - time * speed * 1.25f)
                )
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r * breathe

            when (i) {
                0 -> {
                    firstX = x
                    firstY = y
                    path.moveTo(x, y)
                }
                else -> {
                    // Quadratic smoothing through the midpoints keeps the outline a circle
                    // that breathes instead of a visible polygon.
                    val midX = (prevX + x) / 2f
                    val midY = (prevY + y) / 2f
                    path.quadTo(prevX, prevY, midX, midY)
                }
            }
            prevX = x
            prevY = y
        }
        path.quadTo(prevX, prevY, firstX, firstY)
        path.close()
    }

    // ------------------------------------------------------------------ eyes

    private fun drawEyes(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        mood: Mood,
        time: Float,
        color: Int
    ) {
        val split = when (mood) {
            Mood.ALERT -> 0.37f
            Mood.LISTENING -> 0.36f
            Mood.THINKING -> 0.33f
            else -> 0.34f
        } * radius

        val eyeWidth = when (mood) {
            Mood.ALERT -> 0.32f
            Mood.LISTENING -> 0.31f
            Mood.SPEAKING -> 0.29f
            else -> 0.30f
        } * radius

        val baseHeight = when (mood) {
            Mood.ALERT -> 0.56f
            Mood.LISTENING -> 0.5f
            Mood.SPEAKING -> 0.42f
            Mood.THINKING -> 0.38f
            Mood.SLEEPY -> 0.2f
            Mood.IDLE -> 0.45f
        } * radius

        // A blink every few seconds; quicker when it is animated.
        val blinkEvery = if (mood == Mood.SPEAKING) 3.1f else 4.4f
        val blinkPhase = time % blinkEvery
        val blink = if (blinkPhase < 0.13f) {
            (1f - sin((blinkPhase / 0.13f) * PI.toFloat())).coerceIn(0f, 1f)
        } else {
            1f
        }

        // Eyes sit on a sphere: the gaze is a direction, not an offset.
        val wander = when (mood) {
            Mood.LISTENING -> 0.05f
            Mood.THINKING -> 0.02f
            Mood.SLEEPY -> 0.01f
            else -> 0.13f
        }
        val yaw = sin(time * 0.43f) * wander * radius
        val pitch = (cos(time * 0.31f + 1.1f) * 0.7f * wander + when (mood) {
            Mood.THINKING -> -0.08f
            Mood.SLEEPY -> 0.05f
            else -> 0f
        }) * radius

        // 0 keeps the eyes upright; the hook is here for future expressions.
        val tilt = 0f
        val openFactor = when (mood) {
            Mood.SLEEPY -> 0.12f
            Mood.THINKING -> 0.6f
            else -> 1f
        }

        eyePaint.color = color
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * split + yaw
            val ey = cy + pitch
            val h = (baseHeight * blink * openFactor).coerceAtLeast(eyeWidth * 0.35f)
            eyeRect.set(ex - eyeWidth / 2f, ey - h / 2f, ex + eyeWidth / 2f, ey + h / 2f)
            canvas.save()
            if (tilt != 0f) canvas.rotate(tilt * side, ex, ey)
            canvas.drawRoundRect(eyeRect, eyeWidth / 2f, eyeWidth / 2f, eyePaint)
            canvas.restore()
        }
    }

    private const val TWO_PI = (2.0 * PI).toFloat()
}
