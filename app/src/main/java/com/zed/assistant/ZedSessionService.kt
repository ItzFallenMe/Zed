package com.zed.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Keeps the active microphone/audio session alive while another app is foregrounded. */
class ZedSessionService : Service() {

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, buildNotification())
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
    .setContentTitle(getString(R.string.app_name))
    .setContentText("Zed is ready for voice conversation")
    .setOngoing(true)
    .setCategory(NotificationCompat.CATEGORY_SERVICE)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .build()

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Active voice session",
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = "Keeps Zed's active Gemini Live voice session available while multitasking."
      setShowBadge(false)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  companion object {
    private const val CHANNEL_ID = "zed_voice_session"
    private const val NOTIFICATION_ID = 1001

    fun start(context: Context): Boolean {
      return runCatching {
        val intent = Intent(context, ZedSessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          ContextCompat.startForegroundService(context, intent)
        } else {
          context.startService(intent)
        }
      }.isSuccess
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, ZedSessionService::class.java))
    }
  }
}
