package dev.zed.assistant.security

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.zed.assistant.MainActivity
import dev.zed.assistant.R
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedPrefs
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Watches the accelerometer and the charger. When armed and disturbed it sounds a
 * loud siren, vibrates, locks the screen (if admin is granted) and captures a photo.
 */
class AntiTheftService : LifecycleService(), SensorEventListener {

    private lateinit var prefs: ZedPrefs
    private lateinit var siren: SirenPlayer
    private var sensorManager: SensorManager? = null
    private var lastTriggerAt = 0L
    private var motionHits = 0
    private var lastMotionAt = 0L
    private var gravity = FloatArray(3)

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                trigger("charger unplugged")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = ZedPrefs(this)
        siren = SirenPlayer(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_DISARM -> {
                disarmAndStop()
                return START_NOT_STICKY
            }
            ACTION_TEST -> {
                promoteToForeground()
                trigger("manual test")
                return START_NOT_STICKY
            }
            else -> arm()
        }
        return START_STICKY
    }

    private fun arm() {
        if (!ZedPermissions.hasCamera(this)) {
            ZedState.log("GUARD", "Camera permission missing; guard cannot arm")
            stopSelf()
            return
        }
        promoteToForeground()
        prefs.antiTheftArmed = true
        ZedState.antiTheftArmed.value = true
        registerSensors()
        registerPowerReceiver()
        ZedState.setStatus("Guard armed")
        ZedState.log("GUARD", "Armed: motion and charger watched")
    }

    private fun promoteToForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zed guard armed")
            .setContentText("Watching motion and the charger")
            .setSmallIcon(R.drawable.ic_zed_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .addAction(0, "Disarm", serviceIntent(ACTION_DISARM))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun registerSensors() {
        val manager = sensorManager ?: return
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        gravity = FloatArray(3)
        manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun registerPowerReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED))
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        val alpha = 0.8f
        for (i in 0..2) {
            gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]
        }
        val linearX = values[0] - gravity[0]
        val linearY = values[1] - gravity[1]
        val linearZ = values[2] - gravity[2]
        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        val threshold = when (prefs.motionSensitivity) {
            1 -> 3.4f
            3 -> 1.4f
            else -> 2.2f
        }
        val now = System.currentTimeMillis()
        if (magnitude > threshold) {
            if (now - lastMotionAt > 400) motionHits = 0
            motionHits++
            lastMotionAt = now
            if (motionHits >= 2) {
                motionHits = 0
                trigger("motion detected")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun trigger(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < 8_000) return
        lastTriggerAt = now
        ZedState.log("GUARD", "TRIGGERED: $reason")
        ZedState.addMessage(ZedState.Role.SYSTEM, "Intruder alert: $reason")
        siren.start()
        vibrate()
        lockScreen()
        captureSnapshot(reason)
        notifyIntruder(reason)
        lifecycleScope.launch {
            delay(SIREN_MAX_MS)
            siren.stop()
            ZedState.setStatus("Guard armed")
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = longArrayOf(0, 600, 220, 600, 220, 900)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun lockScreen() {
        if (!ZedPermissions.isDeviceAdminActive(this)) return
        runCatching {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
            ZedState.log("GUARD", "Screen locked")
        }
    }

    private fun captureSnapshot(reason: String) {
        if (!ZedPermissions.hasCamera(this)) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.bindToLifecycle(this, selector, capture)

                val dir = File(filesDir, "intruder").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val file = File(dir, "intruder-$stamp.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            ZedState.log("GUARD", "Intruder snapshot saved: ${file.name}")
                        }

                        override fun onError(exception: ImageCaptureException) {
                            ZedState.log("GUARD", "Snapshot failed: ${exception.message}")
                        }
                    }
                )
            } catch (t: Throwable) {
                ZedState.log("GUARD", "Camera unavailable: ${t.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun notifyIntruder(reason: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intruder alert")
            .setContentText(reason)
            .setSmallIcon(R.drawable.ic_zed_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Disarm siren", serviceIntent(ACTION_DISARM))
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID + 1, notification)
    }

    private fun disarmAndStop() {
        siren.stop()
        prefs.antiTheftArmed = false
        ZedState.antiTheftArmed.value = false
        ZedState.setStatus("Standby")
        ZedState.log("GUARD", "Disarmed")
        unregisterSafely()
        stopForegroundCompat()
        stopSelf()
    }

    private fun unregisterSafely() {
        runCatching { sensorManager?.unregisterListener(this) }
        runCatching { unregisterReceiver(powerReceiver) }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        siren.stop()
        unregisterSafely()
        ZedState.antiTheftArmed.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(this, AntiTheftService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anti-theft guard",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when the phone is moved or unplugged while armed"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_ARM = "dev.zed.assistant.guard.ARM"
        const val ACTION_DISARM = "dev.zed.assistant.guard.DISARM"
        const val ACTION_TEST = "dev.zed.assistant.guard.TEST"
        const val CHANNEL_ID = "zed_guard"
        const val NOTIF_ID = 4242
        private const val SIREN_MAX_MS = 120_000L

        fun arm(context: Context) {
            val intent = Intent(context, AntiTheftService::class.java).setAction(ACTION_ARM)
            ContextCompat.startForegroundService(context, intent)
        }

        fun disarm(context: Context) {
            val intent = Intent(context, AntiTheftService::class.java).setAction(ACTION_DISARM)
            runCatching { context.startService(intent) }
        }

        fun requestTest(context: Context) {
            val intent = Intent(context, AntiTheftService::class.java).setAction(ACTION_TEST)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
