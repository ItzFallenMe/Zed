package dev.zed.assistant.command

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import dev.zed.assistant.access.ZedAccessibilityService
import dev.zed.assistant.core.MemoryStore
import dev.zed.assistant.core.ZedPermissions
import dev.zed.assistant.core.ZedState
import dev.zed.assistant.device.HardwareController
import dev.zed.assistant.notify.ZedNotificationListener
import dev.zed.assistant.vision.OcrEngine
import dev.zed.assistant.vision.OnDeviceTranslator
import dev.zed.assistant.vision.ScreenCapture
import dev.zed.assistant.whatsapp.WhatsAppController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Turns a [ParsedCommand] (or a Gemini tool call) into real device actions and a
 * spoken reply. All heavy work is suspend-friendly so screen OCR never blocks the UI.
 */
class CommandExecutor(
    private val context: Context,
    private val hardware: HardwareController,
    private val whatsapp: WhatsAppController,
    private val screen: ScreenCapture,
    private val ocr: OcrEngine,
    private val translator: OnDeviceTranslator,
    private val memory: MemoryStore,
    private val scope: CoroutineScope,
    private val speak: (String) -> Unit,
    private val startSiren: () -> Unit,
    private val stopSiren: () -> Unit,
    private val setGuard: (Boolean) -> Unit
) {

    private var autoScrollJob: Job? = null

    suspend fun execute(command: ParsedCommand): String {
        val kind = command.kind
        ZedState.log("CMD", kind.name + command.target.takeIf { it.isNotBlank() }?.let { " -> $it" }.orEmpty())
        return when (kind) {
            ParsedCommand.Kind.TORCH_ON ->
                if (hardware.setTorch(true)) "Light on, sir." else "No flashlight available."
            ParsedCommand.Kind.TORCH_OFF ->
                if (hardware.setTorch(false)) "Light off." else "No flashlight available."
            ParsedCommand.Kind.TORCH_TOGGLE -> {
                val on = hardware.toggleTorch()
                if (on) "Light on, sir." else "Light off."
            }

            ParsedCommand.Kind.WIFI_ON -> setWifi(true)
            ParsedCommand.Kind.WIFI_OFF -> setWifi(false)
            ParsedCommand.Kind.BT_ON -> setBluetooth(true)
            ParsedCommand.Kind.BT_OFF -> setBluetooth(false)

            ParsedCommand.Kind.VOLUME_UP -> "Volume ${hardware.adjustVolume(10)} percent."
            ParsedCommand.Kind.VOLUME_DOWN -> "Volume ${hardware.adjustVolume(-10)} percent."
            ParsedCommand.Kind.VOLUME_SET -> "Volume ${hardware.setVolumePercent(command.number)} percent."
            ParsedCommand.Kind.MUTE -> hardware.muteAll()
            ParsedCommand.Kind.UNMUTE -> hardware.unmuteAll()

            ParsedCommand.Kind.BRIGHTNESS_UP ->
                if (hardware.adjustBrightness(15)) "Brightness up." else needWriteSettings()
            ParsedCommand.Kind.BRIGHTNESS_DOWN ->
                if (hardware.adjustBrightness(-15)) "Brightness down." else needWriteSettings()
            ParsedCommand.Kind.BRIGHTNESS_SET ->
                if (hardware.setBrightness(command.number)) {
                    "Brightness ${command.number.coerceIn(5, 100)} percent."
                } else {
                    needWriteSettings()
                }
            ParsedCommand.Kind.BRIGHTNESS_AUTO -> {
                dev.zed.assistant.core.ZedPermissions.openSettingsScreen(
                    context, dev.zed.assistant.core.ZedPermissions.Capability.WRITE_SETTINGS
                )
                "Adaptive brightness lives in display settings, sir; I opened the permission page."
            }

            ParsedCommand.Kind.BATTERY -> {
                val pct = hardware.batteryPercent()
                val charging = if (hardware.isCharging()) ", charging" else ""
                "Battery at $pct percent$charging."
            }

            ParsedCommand.Kind.STATUS_REPORT -> statusReport()

            ParsedCommand.Kind.REMEMBER -> {
                if (command.target.isBlank()) {
                    "What should I remember, sir?"
                } else if (memory.remember(command.target) != null) {
                    "Noted, sir. I will remember that."
                } else {
                    "That was too short to remember."
                }
            }
            ParsedCommand.Kind.RECALL -> {
                val hits = memory.recall(command.target)
                when {
                    hits.isEmpty() -> "I have nothing on that, sir."
                    hits.size == 1 -> hits.first()
                    else -> "Here is what I know: " + hits.take(6).joinToString(". ")
                }
            }
            ParsedCommand.Kind.FORGET -> {
                val removed = memory.forget(command.target)
                if (removed > 0) "Forgotten, sir." else "I had nothing matching that."
            }

            ParsedCommand.Kind.ALARM_SET -> {
                if (command.hour < 0) {
                    "What time should I set the alarm for?"
                } else if (hardware.setAlarm(command.hour, command.minute, "Zed alarm")) {
                    "Alarm set for ${formatTime(command.hour, command.minute)}, sir."
                } else {
                    "I could not set that alarm."
                }
            }
            ParsedCommand.Kind.ALARM_SHOW -> {
                hardware.showAlarms()
                "Here are your alarms."
            }
            ParsedCommand.Kind.TIMER_SET -> {
                if (command.number > 0 && hardware.startTimer(command.number, "Zed timer")) {
                    "Timer set for ${command.number} seconds."
                } else {
                    "How long should the timer run?"
                }
            }
            ParsedCommand.Kind.REMINDER -> {
                if (command.number <= 0) {
                    "When should I remind you?"
                } else {
                    val label = command.target.ifBlank { "Reminder from Zed" }
                    if (hardware.startTimer(command.number, label)) {
                        "I will remind you about $label in ${command.number / 60} minute(s), sir."
                    } else {
                        "I could not create that reminder."
                    }
                }
            }

            ParsedCommand.Kind.OPEN_APP -> {
                val target = command.target
                val opened = hardware.launchApp(target)
                if (opened != null && opened.contains(".")) {
                    "Opening $target."
                } else if (opened != null) {
                    opened
                } else {
                    "I could not find $target on this phone."
                }
            }
            ParsedCommand.Kind.OPEN_URL -> {
                if (hardware.openUrl(command.target)) "Opening it." else "I could not open that link."
            }
            ParsedCommand.Kind.WEB_SEARCH -> {
                hardware.openWebSearch(command.target)
                "Searching for ${command.target}."
            }

            ParsedCommand.Kind.CALL_CONTACT -> {
                if (hardware.callByName(command.target)) {
                    "Calling ${command.target}, sir."
                } else {
                    "I could not find ${command.target} in your contacts."
                }
            }
            ParsedCommand.Kind.CALL_NUMBER -> {
                if (hardware.callNumber(command.target)) {
                    "Dialling ${command.target}."
                } else {
                    "The call was blocked by the system."
                }
            }

            ParsedCommand.Kind.WHATSAPP_SEND -> {
                if (command.target.isBlank()) {
                    "Who should I message?"
                } else if (command.message.isBlank()) {
                    "What should I say to ${command.target}?"
                } else {
                    whatsapp.sendMessage(command.target, command.message) { reply -> speak(reply) }
                    "Sending to ${command.target}, sir."
                }
            }
            ParsedCommand.Kind.WHATSAPP_VOICE_NOTE -> {
                val seconds = if (command.number in 2..30) command.number else 5
                whatsapp.sendVoiceNote(command.target, seconds) { reply -> speak(reply) }
                "Recording a voice note for ${command.target}."
            }
            ParsedCommand.Kind.WHATSAPP_CALL -> {
                whatsapp.startCall(command.target, video = false) { reply -> speak(reply) }
                "Calling ${command.target} on WhatsApp."
            }
            ParsedCommand.Kind.WHATSAPP_VIDEO_CALL -> {
                whatsapp.startCall(command.target, video = true) { reply -> speak(reply) }
                "Video calling ${command.target}."
            }
            ParsedCommand.Kind.WHATSAPP_SEARCH -> {
                whatsapp.searchChat(command.target) { reply -> speak(reply) }
                "Searching WhatsApp."
            }

            ParsedCommand.Kind.READ_SCREEN -> readScreenAloud()
            ParsedCommand.Kind.READ_NOTIFICATIONS -> {
                val summary = ZedNotificationListener.unreadSummary()
                if (summary.isBlank()) {
                    "No unread notifications, sir."
                } else {
                    "Here is what came in: $summary"
                }
            }
            ParsedCommand.Kind.TRANSLATE_SCREEN -> {
                val text = readScreenText()
                if (text.isBlank()) {
                    "There is nothing readable on screen."
                } else {
                    val source = if (command.target == "hi") "en" else "hi"
                    val translated = translator.translate(text.take(1200), source, command.target)
                    translated.ifBlank { "Translation failed, sir." }
                }
            }
            ParsedCommand.Kind.SCREENSHOT -> {
                val file = screen.saveScreenshot()
                if (file != null) "Screenshot saved as ${file.name}." else "Screen vision is not running."
            }

            ParsedCommand.Kind.SCROLL_UP -> {
                val ok = ZedAccessibilityService.instance?.scrollScreen(false) == true
                if (ok) "Scrolled up." else accessMissing()
            }
            ParsedCommand.Kind.SCROLL_DOWN -> {
                val ok = ZedAccessibilityService.instance?.scrollScreen(true) == true
                if (ok) "Scrolled down." else accessMissing()
            }
            ParsedCommand.Kind.SWIPE_LEFT -> performSwipe("left")
            ParsedCommand.Kind.SWIPE_RIGHT -> performSwipe("right")
            ParsedCommand.Kind.SWIPE_UP -> performSwipe("up")
            ParsedCommand.Kind.SWIPE_DOWN -> performSwipe("down")
            ParsedCommand.Kind.SETTINGS_PAGE -> openSettingsPage(command.target)
            ParsedCommand.Kind.TAP_TEXT -> {
                val a11y = ZedAccessibilityService.instance
                if (a11y == null) {
                    accessMissing()
                } else if (a11y.clickText(command.target)) {
                    "Tapped ${command.target}."
                } else {
                    "I could not find \"${command.target}\" on screen."
                }
            }
            ParsedCommand.Kind.TYPE_TEXT -> {
                val a11y = ZedAccessibilityService.instance
                if (a11y?.typeText(command.target) == true) "Typed it." else accessMissing()
            }

            ParsedCommand.Kind.BACK -> nav { it.back() } ?: "Back."
            ParsedCommand.Kind.HOME -> nav { it.home() } ?: "Home."
            ParsedCommand.Kind.RECENTS -> nav { it.recents() } ?: "Recent apps."
            ParsedCommand.Kind.LOCK_SCREEN -> nav { it.lockScreen() } ?: "Locked."
            ParsedCommand.Kind.QUICK_SETTINGS -> {
                val ok = ZedAccessibilityService.instance?.expandQuickSettings() == true
                if (ok) "Control panel open." else accessMissing()
            }
            ParsedCommand.Kind.NOTIFICATIONS_PANEL -> {
                val ok = ZedAccessibilityService.instance?.showNotifications() == true
                if (ok) "Notifications open." else accessMissing()
            }
            ParsedCommand.Kind.POWER_MENU -> {
                val ok = ZedAccessibilityService.instance?.powerDialog() == true
                if (ok) "Power menu open." else accessMissing()
            }

            ParsedCommand.Kind.MEDIA_TOGGLE -> {
                hardware.mediaAction("toggle")
                "Done."
            }
            ParsedCommand.Kind.MEDIA_NEXT -> {
                hardware.mediaAction("next")
                "Next."
            }
            ParsedCommand.Kind.MEDIA_PREV -> {
                hardware.mediaAction("prev")
                "Previous."
            }

            ParsedCommand.Kind.TIME -> "It is ${hardware.currentTime()}, sir."
            ParsedCommand.Kind.DATE -> "Today is ${hardware.currentDate()}."

            ParsedCommand.Kind.SIREN_ON -> {
                startSiren()
                "Siren armed. That will be loud."
            }
            ParsedCommand.Kind.SIREN_OFF -> {
                stopSiren()
                "Siren stopped."
            }
            ParsedCommand.Kind.ARM_GUARD -> {
                setGuard(true)
                "Anti-theft guard armed. I am watching motion and the charger."
            }
            ParsedCommand.Kind.DISARM_GUARD -> {
                setGuard(false)
                "Guard disarmed."
            }

            ParsedCommand.Kind.AUTO_SCROLL_START -> startAutoScroll()
            ParsedCommand.Kind.AUTO_SCROLL_STOP -> {
                autoScrollJob?.cancel()
                autoScrollJob = null
                "Auto scroll stopped."
            }

            ParsedCommand.Kind.HELP -> helpText()

            ParsedCommand.Kind.UNKNOWN -> ""
        }
    }

    suspend fun runTool(name: String, args: JSONObject): String {
        val command = when (name) {
            "flashlight" -> ParsedCommand(
                if (args.optBoolean("on", true)) ParsedCommand.Kind.TORCH_ON else ParsedCommand.Kind.TORCH_OFF
            )
            "wifi" -> ParsedCommand(
                if (args.optBoolean("on", true)) ParsedCommand.Kind.WIFI_ON else ParsedCommand.Kind.WIFI_OFF
            )
            "bluetooth" -> ParsedCommand(
                if (args.optBoolean("on", true)) ParsedCommand.Kind.BT_ON else ParsedCommand.Kind.BT_OFF
            )
            "volume" -> when (args.optString("action", "up")) {
                "set" -> ParsedCommand(ParsedCommand.Kind.VOLUME_SET, number = args.optInt("level", 50))
                "down" -> ParsedCommand(ParsedCommand.Kind.VOLUME_DOWN)
                "mute" -> ParsedCommand(ParsedCommand.Kind.MUTE)
                "unmute" -> ParsedCommand(ParsedCommand.Kind.UNMUTE)
                else -> ParsedCommand(ParsedCommand.Kind.VOLUME_UP)
            }
            "brightness" -> when (args.optString("action", "up")) {
                "set" -> ParsedCommand(ParsedCommand.Kind.BRIGHTNESS_SET, number = args.optInt("level", 60))
                "down" -> ParsedCommand(ParsedCommand.Kind.BRIGHTNESS_DOWN)
                else -> ParsedCommand(ParsedCommand.Kind.BRIGHTNESS_UP)
            }
            "battery_status" -> ParsedCommand(ParsedCommand.Kind.BATTERY)
            "status_report" -> ParsedCommand(ParsedCommand.Kind.STATUS_REPORT)
            "remember" -> ParsedCommand(
                ParsedCommand.Kind.REMEMBER, target = args.optString("fact")
            )
            "recall" -> ParsedCommand(
                ParsedCommand.Kind.RECALL, target = args.optString("query")
            )
            "set_reminder" -> ParsedCommand(
                ParsedCommand.Kind.REMINDER,
                target = args.optString("note"),
                number = args.optInt("minutes", 5) * 60
            )
            "set_alarm" -> ParsedCommand(
                ParsedCommand.Kind.ALARM_SET,
                hour = args.optInt("hour", -1),
                minute = args.optInt("minute", 0)
            )
            "set_timer" -> ParsedCommand(
                ParsedCommand.Kind.TIMER_SET, number = args.optInt("seconds", 60)
            )
            "open_app" -> ParsedCommand(ParsedCommand.Kind.OPEN_APP, target = args.optString("name"))
            "search_web" -> ParsedCommand(ParsedCommand.Kind.WEB_SEARCH, target = args.optString("query"))
            "call_contact" -> {
                val name = args.optString("name")
                if (args.optBoolean("whatsapp", false)) {
                    ParsedCommand(
                        if (args.optBoolean("video", false)) ParsedCommand.Kind.WHATSAPP_VIDEO_CALL
                        else ParsedCommand.Kind.WHATSAPP_CALL,
                        target = name
                    )
                } else {
                    ParsedCommand(ParsedCommand.Kind.CALL_CONTACT, target = name)
                }
            }
            "whatsapp_send" -> ParsedCommand(
                ParsedCommand.Kind.WHATSAPP_SEND,
                target = args.optString("recipient"),
                message = args.optString("message")
            )
            "whatsapp_voice_note" -> ParsedCommand(
                ParsedCommand.Kind.WHATSAPP_VOICE_NOTE,
                target = args.optString("recipient"),
                number = args.optInt("seconds", 5)
            )
            "read_screen" -> ParsedCommand(ParsedCommand.Kind.READ_SCREEN)
            "read_notifications" -> ParsedCommand(ParsedCommand.Kind.READ_NOTIFICATIONS)
            "translate_screen" -> ParsedCommand(
                ParsedCommand.Kind.TRANSLATE_SCREEN,
                target = args.optString("target_language", "hi")
            )
            "scroll" -> ParsedCommand(
                if (args.optString("direction", "down") == "up") ParsedCommand.Kind.SCROLL_UP
                else ParsedCommand.Kind.SCROLL_DOWN
            )
            "tap_text" -> ParsedCommand(ParsedCommand.Kind.TAP_TEXT, target = args.optString("text"))
            "type_text" -> ParsedCommand(ParsedCommand.Kind.TYPE_TEXT, target = args.optString("text"))
            "navigate" -> when (args.optString("action", "back")) {
                "home" -> ParsedCommand(ParsedCommand.Kind.HOME)
                "recents" -> ParsedCommand(ParsedCommand.Kind.RECENTS)
                "lock" -> ParsedCommand(ParsedCommand.Kind.LOCK_SCREEN)
                "quick_settings" -> ParsedCommand(ParsedCommand.Kind.QUICK_SETTINGS)
                "notifications" -> ParsedCommand(ParsedCommand.Kind.NOTIFICATIONS_PANEL)
                "power" -> ParsedCommand(ParsedCommand.Kind.POWER_MENU)
                else -> ParsedCommand(ParsedCommand.Kind.BACK)
            }
            "media_control" -> when (args.optString("action", "toggle")) {
                "next" -> ParsedCommand(ParsedCommand.Kind.MEDIA_NEXT)
                "previous" -> ParsedCommand(ParsedCommand.Kind.MEDIA_PREV)
                else -> ParsedCommand(ParsedCommand.Kind.MEDIA_TOGGLE)
            }
            "take_screenshot" -> ParsedCommand(ParsedCommand.Kind.SCREENSHOT)
            "swipe" -> ParsedCommand(
                when (args.optString("direction", "up")) {
                    "left" -> ParsedCommand.Kind.SWIPE_LEFT
                    "right" -> ParsedCommand.Kind.SWIPE_RIGHT
                    "down" -> ParsedCommand.Kind.SWIPE_DOWN
                    else -> ParsedCommand.Kind.SWIPE_UP
                }
            )
            "open_settings_page" -> ParsedCommand(
                ParsedCommand.Kind.SETTINGS_PAGE,
                target = args.optString("page", "main")
            )
            "toggle_quick_setting" -> {
                val label = args.optString("name")
                val ok = toggleTile(label)
                return if (ok) "Toggled $label." else "Could not toggle $label, sir."
            }
            "guard" -> ParsedCommand(
                if (args.optBoolean("enabled", true)) ParsedCommand.Kind.ARM_GUARD
                else ParsedCommand.Kind.DISARM_GUARD
            )
            "siren" -> ParsedCommand(
                if (args.optBoolean("on", true)) ParsedCommand.Kind.SIREN_ON
                else ParsedCommand.Kind.SIREN_OFF
            )
            "time_now" -> ParsedCommand(ParsedCommand.Kind.TIME)
            "auto_scroll" -> ParsedCommand(ParsedCommand.Kind.AUTO_SCROLL_START)
            else -> return "Unknown tool: $name"
        }
        val reply = execute(command)
        return reply.ifBlank { "Done." }
    }

    // -------------------------------------------------------------- helpers

    private suspend fun setWifi(on: Boolean): String {
        val already = isWifiEnabled()
        if (already == on) return if (on) "Wi-Fi is already on, sir." else "Wi-Fi is already off."
        if (toggleTile("Wi-Fi", "Wifi")) {
            return if (on) "Wi-Fi on." else "Wi-Fi off."
        }
        hardware.openWifiPanel()
        return if (on) "Wi-Fi panel open; tap the switch, sir."
        else "Wi-Fi panel open; tap the switch, sir."
    }

    private suspend fun setBluetooth(on: Boolean): String {
        if (isBluetoothEnabled() == on) {
            return if (on) "Bluetooth is already on." else "Bluetooth is already off."
        }
        if (toggleTile("Bluetooth", "ब्लूटूथ")) {
            return if (on) "Bluetooth on." else "Bluetooth off."
        }
        hardware.openBluetoothSettings()
        return "Bluetooth settings open, sir."
    }

    private suspend fun toggleTile(vararg labels: String): Boolean {
        val a11y = ZedAccessibilityService.instance ?: return false
        if (!a11y.expandQuickSettings()) return false
        delay(700)
        val clicked = a11y.performClickByDescription(labels.toList())
        if (clicked) {
            delay(250)
            a11y.back()
        }
        return clicked
    }

    private fun isWifiEnabled(): Boolean = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.isWifiEnabled
    } catch (t: Throwable) {
        false
    }

    private fun isBluetoothEnabled(): Boolean = try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter?.isEnabled == true
    } catch (t: Throwable) {
        false
    }

    private suspend fun readScreenText(): String {
        val a11y = ZedAccessibilityService.instance?.screenText().orEmpty()
        if (a11y.length >= 60) return a11y
        if (!screen.isRunning) {
            return a11y.ifBlank { "" }
        }
        val bitmap = screen.captureFrame() ?: return a11y
        val result = ocr.recognize(bitmap)
        bitmap.recycle()
        return result.fullText.ifBlank { a11y }
    }

    private suspend fun readScreenAloud(): String {
        val text = readScreenText()
        return when {
            text.isBlank() -> "Screen vision is not running, sir. Start it from the dashboard."
            else -> text
        }
    }

    private fun performSwipe(direction: String): String {
        val a11y = ZedAccessibilityService.instance ?: return accessMissing()
        val (w, h) = a11y.screenSize()
        val wf = w.toFloat()
        val hf = h.toFloat()
        val swiped = when (direction) {
            "left" -> a11y.swipe(wf * 0.85f, hf * 0.5f, wf * 0.15f, hf * 0.5f, 260L)
            "right" -> a11y.swipe(wf * 0.15f, hf * 0.5f, wf * 0.85f, hf * 0.5f, 260L)
            "up" -> a11y.swipe(wf * 0.5f, hf * 0.75f, wf * 0.5f, hf * 0.25f, 260L)
            else -> a11y.swipe(wf * 0.5f, hf * 0.33f, wf * 0.5f, hf * 0.75f, 260L)
        }
        return if (swiped) "Swiped $direction." else "That gesture was blocked, sir."
    }

    private fun openSettingsPage(page: String): String {
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications" -> "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        ZedPermissions.launchSafely(context, Intent(action))
        return "Opening $page settings."
    }

    private fun statusReport(): String {
        val pct = hardware.batteryPercent()
        val power = if (hardware.isCharging()) "charging" else "on battery"
        val unread = ZedNotificationListener.recent(limit = 20).size
        val app = currentAppLabel()
        val vision = if (screen.isRunning) "screen vision active" else "screen vision off"
        val guard = if (ZedState.antiTheftArmed.value) "guard armed" else "guard disarmed"
        val wake = if (ZedState.wakeActive.value) "wake word listening" else "wake word off"
        return buildString {
            append("It is ${hardware.currentTime()}. Battery $pct percent, $power. ")
            if (unread > 0) append("$unread recent notifications. ")
            if (app.isNotBlank()) append("You are in $app. ")
            append("$vision, $guard, $wake, sir.")
        }
    }

    private fun currentAppLabel(): String {
        val pkg = ZedAccessibilityService.instance?.currentPackage ?: return ""
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (t: Throwable) {
            ""
        }
    }

    private fun startAutoScroll(): String {
        val a11y = ZedAccessibilityService.instance ?: return accessMissing()
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            repeat(120) {
                a11y.scrollScreen(true)
                delay(2600)
            }
        }
        ZedState.log("SCROLL", "Auto scroll started")
        return "Auto scrolling, sir."
    }

    private fun nav(action: (ZedAccessibilityService) -> Boolean): String? {
        val a11y = ZedAccessibilityService.instance ?: return accessMissing()
        action(a11y)
        return null
    }

    private fun accessMissing(): String {
        ZedState.log("ACCESS", "Accessibility service not connected")
        return "I need device control permission for that, sir."
    }

    private fun needWriteSettings(): String {
        dev.zed.assistant.core.ZedPermissions.openSettingsScreen(
            context, dev.zed.assistant.core.ZedPermissions.Capability.WRITE_SETTINGS
        )
        return "Allow \"Modify system settings\" so I can change brightness, sir."
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val normalized = ((hour % 24) + 24) % 24
        val suffix = if (normalized < 12) "AM" else "PM"
        val display = when {
            normalized == 0 -> 12
            normalized > 12 -> normalized - 12
            else -> normalized
        }
        return "%d:%02d %s".format(display, minute, suffix)
    }

    private fun helpText(): String = buildString {
        append("At your service, sir. I can control the flashlight, Wi-Fi, Bluetooth, ")
        append("volume, brightness, alarms, timers and any installed app. ")
        append("I can read the screen aloud, translate it, scroll, tap, type, navigate, ")
        append("play or pause media, and drive WhatsApp: messages, voice notes, search and calls. ")
        append("I also read notifications aloud and run the anti-theft siren.")
    }

    fun stopAll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
}
