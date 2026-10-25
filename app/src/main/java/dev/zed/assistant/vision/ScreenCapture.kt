package dev.zed.assistant.vision

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.WindowManager
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the MediaProjection session. Frames are downscaled to at most 1080px wide
 * so OCR stays fast and memory bounded.
 */
class ScreenCapture(private val context: Context) {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var readerThread: HandlerThread? = null

    @Volatile
    private var latestFrame: Bitmap? = null

    @Volatile
    private var frameAt: Long = 0L

    @Volatile
    private var stopping = false

    var captureWidth: Int = 0
        private set
    var captureHeight: Int = 0
        private set

    val isRunning: Boolean
        get() = projection != null && virtualDisplay != null

    fun start(resultCode: Int, data: Intent): Boolean {
        if (isRunning) return true
        return try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
            val mediaProjection = manager.getMediaProjection(resultCode, data) ?: return false
            projection = mediaProjection

            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    ZedState.log("VISION", "Projection stopped by system")
                    stop()
                }
            }, Handler(context.mainLooper))

            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val scale = if (width > MAX_WIDTH) MAX_WIDTH.toFloat() / width else 1f
            captureWidth = (width * scale).toInt().coerceAtLeast(2)
            captureHeight = (height * scale).toInt().coerceAtLeast(2)

            val thread = HandlerThread("zed-vision").also { it.start() }
            readerThread = thread
            val handler = Handler(thread.looper)

            val reader = ImageReader.newInstance(
                captureWidth, captureHeight, PixelFormat.RGBA_8888, 3
            )
            imageReader = reader
            reader.setOnImageAvailableListener({ r ->
                try {
                    r.acquireLatestImage()?.use { image ->
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * image.width
                        val paddedWidth = image.width + rowPadding / pixelStride
                        val raw = Bitmap.createBitmap(
                            paddedWidth, image.height, Bitmap.Config.ARGB_8888
                        )
                        raw.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(raw, 0, 0, image.width, image.height)
                        if (cropped !== raw) raw.recycle()
                        val previous = latestFrame
                        latestFrame = cropped
                        frameAt = SystemClock.elapsedRealtime()
                        previous?.takeIf { it !== cropped && !it.isRecycled }?.recycle()
                    }
                } catch (t: Throwable) {
                    ZedState.log("VISION", "Frame error: ${t.message}")
                }
            }, handler)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "zed-screen",
                captureWidth,
                captureHeight,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            ZedState.screenVisionActive.value = true
            ZedState.log("VISION", "Screen vision active at ${captureWidth}x$captureHeight")
            true
        } catch (t: Throwable) {
            ZedState.log("VISION", "Could not start projection: ${t.message}")
            stop()
            false
        }
    }

    /** Waits briefly for a fresh frame, then returns a copy the caller owns. */
    suspend fun captureFrame(maxWaitMs: Long = 1200): Bitmap? {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val frame = latestFrame
            if (frame != null && !frame.isRecycled) {
                return runCatching { frame.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            }
            delay(80)
        }
        return null
    }

    fun saveScreenshot(): File? {
        val frame = latestFrame ?: return null
        return try {
            val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
            val file = File(dir, "zed-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                frame.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ZedState.log("VISION", "Screenshot saved: ${file.name}")
            file
        } catch (t: Throwable) {
            ZedState.log("VISION", "Screenshot failed: ${t.message}")
            null
        }
    }

    fun stop() {
        // The projection callback calls back into stop(); guard against recursion.
        if (stopping) return
        stopping = true
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection?.stop() }
        runCatching { readerThread?.quitSafely() }
        virtualDisplay = null
        imageReader = null
        projection = null
        readerThread = null
        latestFrame?.takeIf { !it.isRecycled }?.recycle()
        latestFrame = null
        ZedState.screenVisionActive.value = false
        stopping = false
    }

    private fun ImageReader.acquireLatestImageSafe(): android.media.Image? = try {
        acquireLatestImage()
    } catch (t: Throwable) {
        null
    }

    companion object {
        private const val MAX_WIDTH = 1080
    }
}
