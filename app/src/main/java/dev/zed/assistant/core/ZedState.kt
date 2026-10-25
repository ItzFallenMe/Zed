package dev.zed.assistant.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide observable state shared between the services and the Compose UI.
 */
object ZedState {

    enum class Role { USER, ZED, SYSTEM }

    data class Message(
        val role: Role,
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        /** Stable identity for list keys: two identical lines are still two rows. */
        val id: Long = NEXT_ID.incrementAndGet()
    )

    data class LogLine(
        val tag: String,
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private const val MAX_MESSAGES = 250
    private const val MAX_LOGS = 250

    private val NEXT_ID = java.util.concurrent.atomic.AtomicLong(0L)

    val listening = MutableStateFlow(false)
    val speaking = MutableStateFlow(false)
    val thinking = MutableStateFlow(false)
    val wakeActive = MutableStateFlow(false)
    val status = MutableStateFlow("Standby")
    val partialText = MutableStateFlow("")
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val logs = MutableStateFlow<List<LogLine>>(emptyList())
    val batteryPercent = MutableStateFlow(0)
    val screenVisionActive = MutableStateFlow(false)
    val antiTheftArmed = MutableStateFlow(false)
    val sirenActive = MutableStateFlow(false)

    /** Name of the person recognised from their voiceprint, if any. */
    val speaker = MutableStateFlow("")

    /** True when the floating menu wants to appear but Android has not granted overlay. */
    val overlayBlocked = MutableStateFlow(false)

    /** True once the creator's identity code has been verified on this install. */
    val creatorVerified = MutableStateFlow(false)
    val enrolling = MutableStateFlow(false)

    fun addMessage(role: Role, text: String) {
        if (text.isBlank()) return
        messages.value = (messages.value + Message(role, text.trim())).takeLast(MAX_MESSAGES)
    }

    fun log(tag: String, text: String) {
        logs.value = (logs.value + LogLine(tag, text)).takeLast(MAX_LOGS)
    }

    fun clearChat() {
        messages.value = emptyList()
    }

    fun clearLogs() {
        logs.value = emptyList()
    }

    fun setStatus(value: String) {
        status.value = value
    }

    fun resetRuntime() {
        listening.value = false
        speaking.value = false
        thinking.value = false
        partialText.value = ""
        wakeActive.value = false
    }
}
