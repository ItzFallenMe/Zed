package dev.zed.assistant.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.zed.assistant.core.ZedState

/**
 * Reads notifications locally so Zed can speak them. Nothing is uploaded:
 * the buffer lives in memory and is capped at 80 entries.
 */
class ZedNotificationListener : NotificationListenerService() {

    data class NotifItem(
        val pkg: String,
        val app: String,
        val title: String,
        val text: String,
        val postedAt: Long,
        val isOngoing: Boolean
    ) {
        fun speakable(): String {
            val head = when {
                title.isBlank() -> app
                app.equals(title, ignoreCase = true) -> title
                else -> "$app, $title"
            }
            return if (text.isBlank()) head else "$head: $text"
        }
    }

    override fun onListenerConnected() {
        instance = this
        ZedState.log("NOTIF", "Notification access connected")
        refreshActive()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val item = sbn.toItem(this, notification) ?: return
        synchronized(buffer) {
            buffer.add(0, item)
            while (buffer.size > MAX_ITEMS) buffer.removeAt(buffer.size - 1)
        }
        ZedState.log("NOTIF", item.speakable().take(120))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        synchronized(removedKeys) {
            removedKeys.add(key)
        }
    }

    fun refreshActive() {
        try {
            activeNotifications?.forEach { sbn ->
                val item = sbn.toItem(this, sbn.notification) ?: return@forEach
                synchronized(buffer) {
                    if (buffer.none { it.postedAt == item.postedAt && it.text == item.text }) {
                        buffer.add(item)
                    }
                    while (buffer.size > MAX_ITEMS) buffer.removeAt(buffer.size - 1)
                }
            }
        } catch (t: Throwable) {
            ZedState.log("NOTIF", "Could not refresh notifications: ${t.message}")
        }
    }

    private fun StatusBarNotification.toItem(
        service: NotificationListenerService,
        notification: Notification
    ): NotifItem? {
        if (packageName == service.packageName) return null
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            )?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val app = try {
            val pm = service.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (t: Throwable) {
            packageName
        }
        return NotifItem(
            pkg = packageName,
            app = app,
            title = title,
            text = text,
            postedAt = postTime,
            isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        )
    }

    companion object {
        private const val MAX_ITEMS = 80
        private val buffer = ArrayList<NotifItem>()
        private val removedKeys = HashSet<String>()

        @Volatile
        var instance: ZedNotificationListener? = null

        fun recent(limit: Int = 20, includeOngoing: Boolean = false): List<NotifItem> =
            synchronized(buffer) {
                buffer.filter { includeOngoing || !it.isOngoing }.take(limit)
            }

        fun unreadSummary(limit: Int = 6): String = synchronized(buffer) {
            buffer.filterNot { it.isOngoing }
                .distinctBy { "${it.pkg}:${it.title}:${it.text}" }
                .take(limit)
                .joinToString("\n") { it.speakable() }
        }

        fun fromWhatsApp(limit: Int = 10): List<NotifItem> =
            recent(limit = limit).filter { it.pkg.contains("whatsapp", ignoreCase = true) }

        fun clear() {
            synchronized(buffer) { buffer.clear() }
            synchronized(removedKeys) { removedKeys.clear() }
        }
    }
}
