package dev.zed.assistant.device

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import dev.zed.assistant.access.ZedAccessibilityService
import dev.zed.assistant.core.ZedState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything Zed can do to the phone itself: torch, audio, display, alarms,
 * battery, app launching, telephony and media transport.
 */
class HardwareController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // ---------------------------------------------------------------- torch

    private var torchCameraId: String? = null
    private var torchOn = false

    fun isTorchAvailable(): Boolean = findTorchCamera() != null

    fun torchState(): Boolean {
        val id = findTorchCamera() ?: return torchOn
        return try {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && torchOn
        } catch (t: Throwable) {
            torchOn
        }
    }

    fun setTorch(on: Boolean): Boolean {
        val id = findTorchCamera() ?: return false
        return try {
            cameraManager.setTorchMode(id, on)
            torchOn = on
            ZedState.log("TORCH", if (on) "Flashlight on" else "Flashlight off")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Torch failed", t)
            ZedState.log("TORCH", "Flashlight failed: ${t.message}")
            false
        }
    }

    fun toggleTorch(): Boolean = setTorch(!torchState())

    private fun findTorchCamera(): String? {
        torchCameraId?.let { return it }
        return try {
            val id = cameraManager.cameraIdList.firstOrNull { candidate ->
                val chars = cameraManager.getCameraCharacteristics(candidate)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull { candidate ->
                cameraManager.getCameraCharacteristics(candidate)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            torchCameraId = id
            id
        } catch (t: Throwable) {
            Log.w(TAG, "No torch camera", t)
            null
        }
    }

    // --------------------------------------------------------------- volume

    fun mediaVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current * 100) / max
    }

    fun adjustVolume(deltaPercent: Int, stream: Int = AudioManager.STREAM_MUSIC): Int {
        val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val step = (max / 20).coerceAtLeast(1)
        val steps = if (deltaPercent >= 0) {
            (deltaPercent / 5).coerceAtLeast(1)
        } else {
            (deltaPercent / 5).coerceAtMost(-1)
        }
        repeat(kotlin.math.abs(steps)) {
            audioManager.adjustStreamVolume(
                stream,
                if (deltaPercent >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
        val percent = (audioManager.getStreamVolume(stream) * 100) / max
        ZedState.log("VOLUME", "Media volume $percent% (step=$step)")
        return percent
    }

    fun setVolumePercent(percent: Int, stream: Int = AudioManager.STREAM_MUSIC): Int {
        val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val target = (percent.coerceIn(0, 100) * max) / 100
        return try {
            audioManager.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
            (target * 100) / max
        } catch (t: Throwable) {
            ZedState.log("VOLUME", "Volume refused: ${t.message}")
            mediaVolumePercent()
        }
    }

    fun muteAll(): String {
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI
            )
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0
            )
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0
            )
        }
        return "Audio muted, sir."
    }

    fun unmuteAll(): String {
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI
            )
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0
            )
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0
            )
        }
        return "Audio restored."
    }

    // ----------------------------------------------------------- brightness

    fun brightnessPercent(): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
            ) * 100 / 255
        } catch (t: Throwable) {
            50
        }
    }

    fun isAutoBrightness(): Boolean {
        return try {
            Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (t: Throwable) {
            false
        }
    }

    fun setBrightness(percent: Int): Boolean {
        if (!Settings.System.canWrite(context)) {
            ZedState.log("BRIGHT", "WRITE_SETTINGS not granted")
            return false
        }
        return try {
            val raw = (percent.coerceIn(5, 100) * 255) / 100
            Settings.System.putInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw
            )
            ZedState.log("BRIGHT", "Brightness $percent%")
            true
        } catch (t: Throwable) {
            ZedState.log("BRIGHT", "Brightness failed: ${t.message}")
            false
        }
    }

    fun adjustBrightness(deltaPercent: Int): Boolean =
        setBrightness((brightnessPercent() + deltaPercent).coerceIn(5, 100))

    // -------------------------------------------------------------- battery

    fun batteryPercent(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level in 0..100) {
            ZedState.batteryPercent.value = level
            return level
        }
        val intent = context.registerReceiver(
            null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val pct = intent?.let {
            val lvl = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (lvl >= 0 && scale > 0) (lvl * 100) / scale else -1
        } ?: -1
        if (pct >= 0) ZedState.batteryPercent.value = pct
        return pct
    }

    fun isCharging(): Boolean {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return manager?.isCharging == true
    }

    // --------------------------------------------------------------- alarms

    fun setAlarm(hour: Int, minute: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ZedState.log("ALARM", "Alarm set for $hour:$minute")
            true
        } catch (t: Throwable) {
            ZedState.log("ALARM", "Alarm failed: ${t.message}")
            false
        }
    }

    fun showAlarms(): Boolean {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun startTimer(seconds: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    // --------------------------------------------------------------- clock

    fun currentTime(): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

    fun currentDate(): String =
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())

    // ----------------------------------------------------------- launching

    fun launchApp(query: String): String? {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return null

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)

        val exactPackage = apps.firstOrNull {
            it.activityInfo.packageName.equals(normalized, ignoreCase = true)
        }
        if (exactPackage != null) return startApp(exactPackage.activityInfo.packageName, exactPackage.activityInfo.name)

        val byLabel = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase() == normalized
        } ?: apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(normalized)
        } ?: apps.firstOrNull {
            it.activityInfo.packageName.lowercase().contains(normalized)
        }

        if (byLabel != null) {
            return startApp(byLabel.activityInfo.packageName, byLabel.activityInfo.name)
        }

        // Fall back to a web search if it is not an installed app.
        return openWebSearch(query)
    }

    private fun startApp(pkg: String, activity: String): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(pkg, activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(intent)
            ZedState.log("LAUNCH", "Opened $pkg")
            pkg
        } catch (t: Throwable) {
            ZedState.log("LAUNCH", "Could not open $pkg: ${t.message}")
            null
        }
    }

    fun installedAppNames(limit: Int = 40): List<String> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0)
            .map { it.loadLabel(pm).toString() }
            .distinct()
            .sorted()
            .take(limit)
    }

    fun openWebSearch(query: String): String? {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Searching for $query"
        } catch (t: Throwable) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Searching for $query"
            } catch (inner: Throwable) {
                null
            }
        }
    }

    fun openUrl(url: String): Boolean = try {
        val fixed = if (url.startsWith("http")) url else "https://$url"
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(fixed)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        false
    }

    // ------------------------------------------------------------ telephony

    fun callNumber(number: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ZedState.log("CALL", "Calling $number")
            true
        } catch (t: Throwable) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (inner: Throwable) {
                ZedState.log("CALL", "Call failed: ${inner.message}")
                false
            }
        }
    }

    fun findContactNumber(name: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name)
        )
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (t: Throwable) {
            ZedState.log("CONTACTS", "Lookup failed: ${t.message}")
            null
        }
    }

    fun callByName(name: String): Boolean {
        val number = findContactNumber(name)
        if (number == null) {
            ZedState.log("CALL", "No contact matched \"$name\"")
            return false
        }
        return callNumber(number)
    }

    // ------------------------------------------------------------ media

    fun mediaAction(action: String): Boolean {
        val key = when (action) {
            "play", "pause", "toggle" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "prev", "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_STOP
            else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        return try {
            audioManager.dispatchMediaKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key)
            )
            audioManager.dispatchMediaKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key)
            )
            true
        } catch (t: Throwable) {
            ZedState.log("MEDIA", "Media key failed: ${t.message}")
            false
        }
    }

    // ---------------------------------------------------------- app panels

    fun openWifiPanel(): Boolean = panel(Settings.Panel.ACTION_WIFI)

    fun openInternetPanel(): Boolean = panel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)

    fun openVolumePanel(): Boolean = panel(Settings.Panel.ACTION_VOLUME)

    fun openNfcPanel(): Boolean = try {
        panel(Settings.Panel.ACTION_NFC)
    } catch (t: Throwable) {
        false
    }

    private fun panel(action: String): Boolean = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (t: Throwable) {
        ZedState.log("PANEL", "Panel unavailable: ${t.message}")
        false
    }

    fun openBluetoothSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        ZedState.log("BT", "Bluetooth settings unavailable: ${t.message}")
        false
    }

    fun openSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        false
    }

    fun openQuickSettingsViaAccessibility(): Boolean {
        val service = ZedAccessibilityService.instance ?: return false
        val expanded = service.expandQuickSettings()
        ZedState.log("QS", if (expanded) "Quick settings expanded" else "Could not expand")
        return expanded
    }

    /** Toggle a quick-settings tile by label through the accessibility service. */
    fun toggleQuickTile(label: String): Boolean {
        val service = ZedAccessibilityService.instance ?: return false
        service.expandQuickSettings()
        service.performClickByDescription(listOf(label, label.replace(" ", "")))
        ZedState.log("QS", "Toggled tile \"$label\"")
        return true
    }

    companion object {
        private const val TAG = "ZedHardware"
    }
}
