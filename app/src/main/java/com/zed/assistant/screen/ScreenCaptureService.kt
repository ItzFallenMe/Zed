package com.zed.assistant.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zed.assistant.MainActivity
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.data.ZedPreferences
import java.io.ByteArrayOutputStream

/**
 * MediaProjection screen sharing. Every active capture shows a foreground
 * notification and stops when the user stops it, when the session ends, or when
 * Android revokes the projection. Frames are downscaled JPEGs sent only to the
 * active Gemini Live session.
 */
class ScreenCaptureService : Service() {

  private var projection: MediaProjection? = null
  private var reader: ImageReader? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var thread: HandlerThread? = null
  private var handler: Handler? = null
  private var lastFrameAt = 0L

  override fun onCreate() {
    super.onCreate()
    isRunning = true
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      // A user stop is a preference change, not a temporary projection failure:
      // do not let the accessibility fallback silently take over.
      ZedPreferences(this).screenContextEnabled = false
      stopSelf()
      return START_NOT_STICKY
    }
    createChannel()
    startForegroundWithType()
    if (projection != null) return START_STICKY

    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
    val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent?.getParcelableExtra(EXTRA_DATA)
    }
    if (data == null) {
      Log.w(TAG, "No MediaProjection consent data")
      stopSelf()
      return START_NOT_STICKY
    }
    startProjection(resultCode, data)
    return START_STICKY
  }

  private fun startForegroundWithType() {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun startProjection(resultCode: Int, data: Intent) {
    try {
      val manager = getSystemService(MediaProjectionManager::class.java)
      val captured = manager.getMediaProjection(resultCode, data)
        ?: return stopSelf()
      projection = captured
      captured.registerCallback(
        object : MediaProjection.Callback() {
          override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by the system")
            release()
            stopSelf()
          }
        },
        Handler(mainLooper)
      )

      val metrics = resources.displayMetrics
      val maxEdge = 640f
      val scale = minOf(1f, maxEdge / maxOf(metrics.widthPixels, metrics.heightPixels))
      val width = ((metrics.widthPixels * scale).toInt() and 0xFFFFFFFE.toInt()).coerceAtLeast(2)
      val height = ((metrics.heightPixels * scale).toInt() and 0xFFFFFFFE.toInt()).coerceAtLeast(2)

      val captureThread = HandlerThread("ZedScreenCapture").also { it.start() }
      thread = captureThread
      val captureHandler = Handler(captureThread.looper)
      handler = captureHandler

      val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
      reader = imageReader
      virtualDisplay = captured.createVirtualDisplay(
        "Zed screen",
        width,
        height,
        metrics.densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface,
        null,
        captureHandler
      )
      imageReader.setOnImageAvailableListener({ source ->
        val image = source.acquireLatestImage()
        if (image == null) return@setOnImageAvailableListener
        try {
          val now = SystemClock.elapsedRealtime()
          if (now - lastFrameAt < FRAME_INTERVAL_MS) return@setOnImageAvailableListener
          lastFrameAt = now
          val jpeg = image.toJpeg() ?: return@setOnImageAvailableListener
          ZedEngine.sendScreenFrame(jpeg)
        } catch (error: Throwable) {
          Log.w(TAG, "Could not process a screen frame", error)
        } finally {
          image.close()
        }
      }, captureHandler)
      Log.i(TAG, "Screen sharing active at ${width}x$height")
    } catch (error: Throwable) {
      Log.e(TAG, "Could not start screen sharing", error)
      release()
      stopSelf()
    }
  }

  private fun Image.toJpeg(): ByteArray? {
    val plane = planes.firstOrNull() ?: return null
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    if (pixelStride <= 0) return null
    val paddedWidth = width + (rowStride - pixelStride * width) / pixelStride
    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    padded.copyPixelsFromBuffer(plane.buffer)
    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
    val output = ByteArrayOutputStream()
    cropped.compress(Bitmap.CompressFormat.JPEG, 68, output)
    padded.recycle()
    cropped.recycle()
    return output.toByteArray()
  }

  private fun buildNotification(): Notification {
    val open = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val stop = PendingIntent.getService(
      this,
      1,
      Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_view)
      .setContentTitle("Zed is sharing your screen")
      .setContentText("A downscaled frame is sent about every 2 seconds while talking.")
      .setContentIntent(open)
      .setOngoing(true)
      .setSilent(true)
      .addAction(0, "Stop sharing", stop)
      .build()
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Screen sharing",
      NotificationManager.IMPORTANCE_LOW
    ).apply { description = "Visible while Zed can see your screen" }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun release() {
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { reader?.close() }
    reader = null
    runCatching { projection?.stop() }
    projection = null
    thread?.quitSafely()
    thread = null
    handler = null
    isRunning = false
  }

  override fun onDestroy() {
    release()
    ZedEngine.onScreenProjectionStopped()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?) = null

  companion object {
    private const val TAG = "ZedScreen"
    private const val CHANNEL_ID = "zed_screen_share"
    private const val NOTIFICATION_ID = 42
    private const val EXTRA_RESULT_CODE = "result_code"
    private const val EXTRA_DATA = "result_data"
    private const val ACTION_STOP = "com.zed.assistant.STOP_SCREEN_SHARE"
    private const val FRAME_INTERVAL_MS = 2_000L

    @Volatile
    var isRunning = false
      private set

    fun start(context: Context, resultCode: Int, data: Intent) {
      val intent = Intent(context, ScreenCaptureService::class.java)
        .setAction("com.zed.assistant.START_SCREEN_SHARE")
        .putExtra(EXTRA_RESULT_CODE, resultCode)
        .putExtra(EXTRA_DATA, data)
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, ScreenCaptureService::class.java))
    }
  }
}
