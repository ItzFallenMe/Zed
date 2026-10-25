package dev.zed.assistant.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.zed.assistant.core.ZedState
import kotlin.math.abs

/**
 * The hands of Zed. A single accessibility service provides:
 *  - screen reading (UI tree text)
 *  - tapping, swiping, long-pressing and scrolling with real gestures
 *  - typing into whatever field is focused
 *  - global navigation: back, home, recents, notifications, quick settings, lock, power
 */
class ZedAccessibilityService : AccessibilityService() {

    data class UiNode(
        val text: String,
        val description: String,
        val className: String,
        val viewId: String,
        val bounds: Rect,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean
    ) {
        val label: String get() = listOf(text, description).firstOrNull { it.isNotBlank() } ?: ""
        fun centerX(): Float = bounds.centerX().toFloat()
        fun centerY(): Float = bounds.centerY().toFloat()
    }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var currentPackage: String = ""
        private set

    @Volatile
    var lastEventAt: Long = 0L
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ZedState.log("ACCESS", "Device control service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isNotBlank() && pkg != "com.android.systemui") {
            currentPackage = pkg
        }
        lastEventAt = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        ZedState.log("ACCESS", "Accessibility interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // --------------------------------------------------------------- reading

    fun rootNode(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (t: Throwable) {
        null
    }

    /** Flat list of the visible UI tree, capped so huge lists cannot stall the assistant. */
    fun nodes(maxNodes: Int = 400): List<UiNode> {
        val root = rootNode() ?: return emptyList()
        val out = ArrayList<UiNode>(64)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < maxNodes) {
            val node = queue.removeFirst()
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (text.isNotBlank() || desc.isNotBlank() || node.isEditable) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (!rect.isEmpty) {
                    out.add(
                        UiNode(
                            text = text,
                            description = desc,
                            className = node.className?.toString().orEmpty(),
                            viewId = node.viewIdResourceName.orEmpty(),
                            bounds = rect,
                            clickable = node.isClickable,
                            editable = node.isEditable,
                            scrollable = node.isScrollable
                        )
                    )
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return out
    }

    fun screenText(): String {
        val labels = nodes().map { it.label }.filter { it.isNotBlank() }
        return labels.distinct().joinToString("\n")
    }

    fun describeScreen(): String {
        val items = nodes().filter { it.label.isNotBlank() }.take(60)
        if (items.isEmpty()) return ""
        return items.joinToString(" | ") { it.label }
    }

    fun findNode(vararg needles: String): UiNode? {
        val query = needles.filter { it.isNotBlank() }.map { it.lowercase() }
        if (query.isEmpty()) return null
        val all = nodes()
        for (needle in query) {
            all.firstOrNull { it.label.lowercase().trim() == needle }?.let { return it }
            all.firstOrNull { it.label.lowercase().contains(needle) }?.let { return it }
        }
        return null
    }

    fun findEditable(): UiNode? {
        val all = nodes()
        return all.firstOrNull { it.editable } ?: all.firstOrNull {
            it.className.contains("EditText", ignoreCase = true)
        }
    }

    // ------------------------------------------------------------- gestures

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, 0L, 60L)
    }

    fun longPress(x: Float, y: Float, durationMs: Long = 800L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, 0L, durationMs)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatch(path, 0L, durationMs)
    }

    private fun dispatch(path: Path, startMs: Long, durationMs: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val stroke = GestureDescription.StrokeDescription(path, startMs, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, handler)
        } catch (t: Throwable) {
            ZedState.log("GESTURE", "Gesture failed: ${t.message}")
            false
        }
    }

    fun clickNode(node: UiNode): Boolean = tap(node.centerX(), node.centerY())

    fun clickText(vararg needles: String): Boolean {
        val node = findNode(*needles) ?: return false
        ZedState.log("TAP", "Tapping \"${node.label}\"")
        return clickNode(node)
    }

    fun performClickByDescription(descriptions: List<String>): Boolean {
        val all = nodes()
        for (desc in descriptions) {
            val needle = desc.lowercase()
            val hit = all.firstOrNull { it.label.lowercase() == needle }
                ?: all.firstOrNull { it.label.lowercase().contains(needle) }
            if (hit != null) return clickNode(hit)
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = rootNode() ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = focused?.takeIf { it.isEditable } ?: findEditableNode(root)
        if (target == null) {
            ZedState.log("TYPE", "No editable field on screen")
            return false
        }
        return try {
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!ok) {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            ZedState.log("TYPE", "Typed ${text.length} characters")
            true
        } catch (t: Throwable) {
            ZedState.log("TYPE", "Typing failed: ${t.message}")
            false
        }
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun scrollScreen(forward: Boolean): Boolean {
        val root = rootNode()
        val scrollable = root?.let { findScrollable(it) }
        if (scrollable != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (scrollable.performAction(action)) return true
        }
        val (w, h) = screenSize()
        val startY = h * if (forward) 0.72f else 0.28f
        val endY = h * if (forward) 0.28f else 0.72f
        return swipe(w / 2f, startY, w / 2f, endY, 280L)
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 300) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun screenSize(): Pair<Int, Int> {
        return try {
            val wm = getSystemService(WindowManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val dm = resources.displayMetrics
                dm.widthPixels to dm.heightPixels
            }
        } catch (t: Throwable) {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    // ----------------------------------------------------------- navigation

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun showNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun expandQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun powerDialog(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        } else {
            false
        }
    }

    /** Returns true when the screen content changed between two snapshots. */
    fun screenChangedSince(signature: String): Boolean = screenText().hashCode().toString() != signature

    fun screenSignature(): String = screenText().hashCode().toString()

    fun looksLikeChatScreen(): Boolean {
        val all = nodes()
        return all.any { it.editable && (it.label.contains("message", true) || it.label.contains("संदेश")) } ||
            all.any { it.description.contains("Send", true) || it.label.contains("Send", true) }
    }

    fun distanceSwipe(dx: Float, dy: Float) {
        val (w, h) = screenSize()
        if (abs(dy) > abs(dx)) {
            swipe(w / 2f, h / 2f + dy, w / 2f, h / 2f - dy, 250L)
        } else {
            swipe(w / 2f - dx, h / 2f, w / 2f + dx, h / 2f, 250L)
        }
    }

    companion object {
        @Volatile
        var instance: ZedAccessibilityService? = null
    }
}
