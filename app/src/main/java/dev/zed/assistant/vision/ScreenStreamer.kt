package dev.zed.assistant.vision

import android.graphics.Bitmap
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Hands-free screen awareness.
 *
 * Once the user consents to screen capture a single time, this pushes what is on screen
 * into the live conversation roughly once a second — no per-app picker, no repeated
 * dialogs, no manual "share this screen". Frames are scaled and JPEG compressed so the
 * stream costs a few kilobytes per second rather than megabytes.
 */
class ScreenStreamer(
    private val screen: ScreenCapture,
    private val scope: CoroutineScope,
    private val onFrame: (jpeg: ByteArray, width: Int, height: Int) -> Unit
) {

    private var job: Job? = null
    private var lastHash = 0

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        if (!screen.isRunning) {
            ZedState.log("VISION", "No capture session to stream")
            return
        }
        lastHash = 0
        job = scope.launch {
            ZedState.log("VISION", "Streaming screen to the live session")
            while (isActive) {
                val frame = screen.captureFrame(maxWaitMs = 900)
                if (frame != null) {
                    val encoded = encode(frame)
                    frame.recycle()
                    if (encoded != null && encoded.hashCode() != lastHash) {
                        lastHash = encoded.hashCode()
                        onFrame(encoded, STREAM_WIDTH, streamHeight)
                    }
                }
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    private var streamHeight = 0

    private fun encode(source: Bitmap): ByteArray? {
        return try {
            val scale = STREAM_WIDTH.toFloat() / source.width
            val height = (source.height * scale).toInt().coerceAtLeast(2)
            streamHeight = height
            val scaled = Bitmap.createScaledBitmap(source, STREAM_WIDTH, height, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (scaled !== source) scaled.recycle()
            out.toByteArray()
        } catch (t: Throwable) {
            ZedState.log("VISION", "Frame encode failed: ${t.message}")
            null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (screen.isRunning) ZedState.log("VISION", "Screen streaming paused")
    }

    companion object {
        private const val STREAM_WIDTH = 720
        private const val QUALITY = 50
        private const val FRAME_INTERVAL_MS = 1100L
    }
}
