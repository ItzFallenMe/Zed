package dev.zed.assistant.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.zed.assistant.access.ZedAccessibilityService
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives WhatsApp through intents plus accessibility gestures.
 *
 * WhatsApp has no public send API, so automation is done the way a human would:
 * open a chat, type the text, press send. Every step is best-effort and logged.
 */
class WhatsAppController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun isInstalled(): Boolean = launchIntent() != null

    private fun launchIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(PKG)

    private fun access(): ZedAccessibilityService? = ZedAccessibilityService.instance

    // ------------------------------------------------------------ messaging

    fun sendMessage(target: String, message: String, onResult: ((String) -> Unit)? = null) {
        if (!isInstalled()) {
            onResult?.invoke("WhatsApp is not installed.")
            return
        }
        val digits = digitsOnly(target)
        scope.launch {
            val opened = if (digits != null) {
                openChatByNumber(digits, message)
            } else {
                openChatByName(target)
            }
            if (!opened) {
                val msg = "I could not open a chat with $target."
                ZedState.log("WHATS", msg)
                onResult?.invoke(msg)
                return@launch
            }
            delay(1200)
            val sent = if (digits != null) pressSend() else typeAndSend(message)
            val msg = if (sent) {
                ZedState.addMessage(ZedState.Role.ZED, "WhatsApp message sent to $target.")
                "Sent to $target."
            } else {
                "WhatsApp is open with your message, but I could not press send."
            }
            ZedState.log("WHATS", msg)
            onResult?.invoke(msg)
        }
    }

    fun sendVoiceNote(target: String, seconds: Int = 5, onResult: ((String) -> Unit)? = null) {
        if (!isInstalled()) {
            onResult?.invoke("WhatsApp is not installed.")
            return
        }
        val digits = digitsOnly(target)
        scope.launch {
            val opened = if (digits != null) openChatByNumber(digits, null)
            else openChatByName(target)
            if (!opened) {
                onResult?.invoke("I could not open a chat with $target.")
                return@launch
            }
            delay(1400)
            val service = access()
            val mic = service?.findNode(
                "Voice message", "Record", "माइक", "Audio", "Voice note"
            )
            if (service == null || mic == null) {
                onResult?.invoke("I could not find the voice note button.")
                return@launch
            }
            val holdMs = (seconds.coerceIn(2, 30) * 1000L)
            service.longPress(mic.centerX(), mic.centerY(), holdMs)
            ZedState.log("WHATS", "Recorded ${seconds}s voice note")
            delay(holdMs + 1200)
            val sent = pressSend()
            onResult?.invoke(
                if (sent) "Voice note sent to $target."
                else "Voice note recorded. Send did not confirm."
            )
        }
    }

    fun startCall(target: String, video: Boolean = false, onResult: ((String) -> Unit)? = null) {
        if (!isInstalled()) {
            onResult?.invoke("WhatsApp is not installed.")
            return
        }
        val digits = digitsOnly(target)
        scope.launch {
            val opened = if (digits != null) openChatByNumber(digits, null)
            else openChatByName(target)
            if (!opened) {
                onResult?.invoke("I could not open a chat with $target.")
                return@launch
            }
            delay(1500)
            val service = access()
            val labels = if (video) {
                listOf("Video call", "वीडियो कॉल", "Video")
            } else {
                listOf("Voice call", "Call", "कॉल", "Voice")
            }
            val clicked = service?.performClickByDescription(labels) == true
            val msg = if (clicked) {
                (if (video) "Video" else "Voice") + " calling $target on WhatsApp."
            } else {
                "Chat opened. I could not find the call button."
            }
            ZedState.log("WHATS", msg)
            onResult?.invoke(msg)
        }
    }

    fun searchChat(query: String, onResult: ((String) -> Unit)? = null) {
        if (!isInstalled()) {
            onResult?.invoke("WhatsApp is not installed.")
            return
        }
        scope.launch {
            launchWhatsApp()
            delay(1200)
            val service = access()
            if (service?.performClickByDescription(listOf("Search", "खोजें", "Search chats")) != true) {
                onResult?.invoke("I could not open WhatsApp search.")
                return@launch
            }
            delay(700)
            service.typeText(query)
            delay(900)
            val picked = service.performClickByDescription(listOf(query))
            if (picked) delay(800)
            onResult?.invoke(
                if (picked) "Searched WhatsApp for $query."
                else "Typed $query in WhatsApp search."
            )
        }
    }

    fun openWhatsApp(): Boolean = try {
        val intent = launchIntent() ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        ZedState.log("WHATS", "Open failed: ${t.message}")
        false
    }

    // -------------------------------------------------------------- helpers

    private fun launchWhatsApp() {
        launchIntent()?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(it) }
        }
    }

    private fun openChatByNumber(digits: String, message: String?): Boolean {
        val url = buildString {
            append("https://wa.me/")
            append(digits)
            if (!message.isNullOrBlank()) {
                append("?text=")
                append(Uri.encode(message))
            }
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(PKG)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ZedState.log("WHATS", "Opened chat for $digits")
            true
        } catch (t: Throwable) {
            ZedState.log("WHATS", "wa.me failed: ${t.message}")
            sendViaSendIntent(digits, message)
        }
    }

    /** Last-resort text share intent that WhatsApp accepts. */
    private fun sendViaSendIntent(digits: String, message: String?): Boolean {
        if (message.isNullOrBlank()) {
            val sms = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$digits"))
            return runCatching {
                context.startActivity(sms.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(PKG)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ZedState.log("WHATS", "Message handed to WhatsApp share sheet")
            true
        } catch (t: Throwable) {
            false
        }
    }

    private suspend fun openChatByName(name: String): Boolean {
        launchWhatsApp()
        val service = access() ?: return false
        // WhatsApp home -> search
        delay(1100)
        if (!service.performClickByDescription(listOf("Search", "खोजें", "Search chats"))) {
            ZedState.log("WHATS", "Search button not found")
            return false
        }
        delay(650)
        if (!service.typeText(name)) {
            ZedState.log("WHATS", "Could not type contact name")
            return false
        }
        delay(1100)
        val result = service.findNode(name) ?: service.nodes().firstOrNull {
            it.label.isNotBlank() && it.clickable
        }
        if (result == null) return false
        service.clickNode(result)
        ZedState.log("WHATS", "Opened chat with $name")
        return true
    }

    private suspend fun typeAndSend(message: String): Boolean {
        val service = access() ?: return false
        val field = service.findNode("Type a message", "Message", "संदेश")
        if (field != null) {
            service.clickNode(field)
            delay(450)
        }
        if (!service.typeText(message)) return false
        delay(350)
        return pressSend()
    }

    private fun pressSend(): Boolean {
        val service = access() ?: return false
        return service.performClickByDescription(
            listOf("Send", "भेजें", "Send message", "Send voice message")
        ) || service.clickText("Send", "भेजें")
    }

    private fun digitsOnly(target: String): String? {
        val cleaned = target.filter { it.isDigit() || it == '+' }
        return if (cleaned.count { it.isDigit() } >= 8) cleaned.trimStart('+') else null
    }

    companion object {
        const val PKG = "com.whatsapp"
        const val PKG_BUSINESS = "com.whatsapp.w4b"
    }
}
