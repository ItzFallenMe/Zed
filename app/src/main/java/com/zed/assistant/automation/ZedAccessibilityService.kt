package com.zed.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * The only component allowed to read the screen or act inside other apps.
 * Everything is user-initiated through a Gemini tool call; the service never
 * runs its own automation. Results are always truthful: a dispatched gesture
 * is reported as dispatched, not as a verified click.
 */
class ZedAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    instance = this
    Log.i(TAG, "Accessibility service connected")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    if (instance === this) instance = null
    super.onDestroy()
  }

  // ------------------------------------------------------------------ inspect

  fun snapshot(limit: Int = 120): JSONObject {
    val root = rootInActiveWindow
      ?: return JSONObject().put("success", false).put("error", "no_active_window")
    val nodes = JSONArray()
    val counter = intArrayOf(0)
    collect(root, nodes, 0, counter, limit)
    return JSONObject()
      .put("success", true)
      .put("app", root.packageName?.toString().orEmpty())
      .put("nodeCount", nodes.length())
      .put("nodes", nodes)
  }

  fun readText(): String {
    val root = rootInActiveWindow ?: return ""
    val out = StringBuilder()
    val seen = HashSet<String>()
    walk(root, 200) { node ->
      val text = node.text?.toString()?.trim().orEmpty()
      val description = node.contentDescription?.toString()?.trim().orEmpty()
      val value = text.ifBlank { description }
      if (value.isNotBlank() && seen.add(value)) out.append(value).append('\n')
      true
    }
    return out.toString().trim()
  }

  // ------------------------------------------------------------------- action

  fun clickElement(query: String): JSONObject {
    val match = findBestNode(query, editable = false)
      ?: return failure("clickElement", "element_not_found")
    var clickable: AccessibilityNodeInfo? = match
    while (clickable != null && !clickable.isClickable) clickable = clickable.parent

    if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
      return success("clickElement") { put("target", describe(match)) }
    }

    val bounds = Rect()
    match.getBoundsInScreen(bounds)
    if (bounds.isEmpty) return failure("clickElement", "element_has_no_bounds")
    val dispatched = tap(bounds.exactCenterX(), bounds.exactCenterY())
    return if (dispatched) {
      success("clickElement") {
        put("target", describe(match))
        put("mode", "gesture")
      }
    } else {
      failure("clickElement", "gesture_rejected")
    }
  }

  /** Click Send in WhatsApp's compose screen after a deep-link launch. */
  fun clickWhatsAppSend(): JSONObject {
    val candidates = listOf("send", "send message", "send now")
    for (candidate in candidates) {
      val result = clickElement(candidate)
      if (result.optBoolean("success")) return result
    }
    return failure("clickElement", "send_button_not_found")
  }

  /** Pick the first plausible video result on a YouTube search screen. */
  fun clickYouTubeResult(query: String): JSONObject {
    val root = rootInActiveWindow
      ?: return failure("clickElement", "no_active_window")
    val tokens = normalize(query).split(' ').filter { it.length >= 3 }
    var best: AccessibilityNodeInfo? = null
    var bestScore = 0
    walk(root, 280) { node ->
      val text = listOf(node.text, node.contentDescription)
        .mapNotNull { it?.toString() }
        .joinToString(" ")
      if (text.isBlank()) return@walk true
      val normalized = normalize(text)
      if (normalized.startsWith("search") || normalized == "search") return@walk true
      val hasDuration = Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(text)
      val hasMetadata = normalized.contains("view") || normalized.contains("ago")
      if (!hasDuration && !hasMetadata) return@walk true
      val titleHits = tokens.count { normalized.contains(it) }
      val bounds = Rect()
      node.getBoundsInScreen(bounds)
      if (bounds.top < resources.displayMetrics.heightPixels * 0.12f) return@walk true
      val score = (if (hasDuration) 20 else 0) + titleHits * 10 +
        (if (node.isClickable) 8 else 0) + (bounds.top / 200).coerceAtMost(6)
      if (score > bestScore) {
        bestScore = score
        best = node
      }
      true
    }
    val match = best ?: return failure("clickElement", "video_result_not_found")
    var clickable: AccessibilityNodeInfo? = match
    while (clickable != null && !clickable.isClickable) clickable = clickable.parent
    if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
      return success("clickElement") { put("target", describe(match)).put("mode", "video_result") }
    }
    val bounds = Rect()
    match.getBoundsInScreen(bounds)
    return if (tap(bounds.exactCenterX(), bounds.exactCenterY())) {
      success("clickElement") { put("target", describe(match)).put("mode", "gesture") }
    } else {
      failure("clickElement", "video_click_rejected")
    }
  }

  fun typeText(text: String, targetHint: String? = null): JSONObject {
    val target = if (!targetHint.isNullOrBlank()) {
      findBestNode(targetHint, editable = true)
    } else {
      rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    } ?: return failure("typeText", "no_editable_field")

    val arguments = Bundle().apply {
      putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    }
    return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
      success("typeText") { put("field", describe(target)) }
    } else {
      failure("typeText", "field_rejected_text")
    }
  }

  fun scroll(direction: String): JSONObject {
    val forward = direction.lowercase() !in setOf("up", "back", "backward", "top")
    var scrollable: AccessibilityNodeInfo? = findScrollable()
    val action = if (forward) {
      AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
    } else {
      AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
    }
    if (scrollable?.performAction(action) == true) {
      return success("scrollScreen") { put("direction", direction) }
    }
    val height = resources.displayMetrics.heightPixels.toFloat()
    val width = resources.displayMetrics.widthPixels.toFloat()
    val fromY = if (forward) height * 0.72f else height * 0.28f
    val toY = if (forward) height * 0.28f else height * 0.72f
    val dispatched = swipe(width / 2f, fromY, width / 2f, toY)
    return if (dispatched) {
      success("scrollScreen") {
        put("direction", direction)
        put("mode", "gesture")
      }
    } else {
      failure("scrollScreen", "scroll_not_available")
    }
  }

  fun pressKey(key: String): JSONObject {
    val action = when (key.lowercase()) {
      "back" -> GLOBAL_ACTION_BACK
      "home" -> GLOBAL_ACTION_HOME
      "recents", "recent", "overview" -> GLOBAL_ACTION_RECENTS
      "notifications", "notification_shade" -> GLOBAL_ACTION_NOTIFICATIONS
      "quick_settings", "settings" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GLOBAL_ACTION_QUICK_SETTINGS
      } else {
        return failure("pressKey", "android_version_too_old")
      }
      "lock_screen", "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        GLOBAL_ACTION_LOCK_SCREEN
      } else {
        return failure("pressKey", "android_version_too_old")
      }
      else -> return failure("pressKey", "unsupported_key")
    }
    return if (performGlobalAction(action)) {
      success("pressKey") { put("key", key) }
    } else {
      failure("pressKey", "global_action_rejected")
    }
  }

  // ------------------------------------------------------------------- vision

  @RequiresApi(Build.VERSION_CODES.R)
  fun captureJpeg(maxDimension: Int = 1024, quality: Int = 72): ByteArray? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val bitmap = captureBitmap() ?: return null
    return try {
      val scale = (maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
      val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
          bitmap,
          (bitmap.width * scale).toInt().coerceAtLeast(1),
          (bitmap.height * scale).toInt().coerceAtLeast(1),
          true
        )
      } else {
        bitmap
      }
      ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
      }
    } finally {
      bitmap.recycle()
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private fun captureBitmap(): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val latch = CountDownLatch(1)
    var bitmap: Bitmap? = null
    takeScreenshot(
      Display.DEFAULT_DISPLAY,
      mainExecutor,
      object : TakeScreenshotCallback {
        override fun onSuccess(screenshot: ScreenshotResult) {
          try {
            val buffer = screenshot.hardwareBuffer
            bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
              ?.copy(Bitmap.Config.ARGB_8888, false)
            buffer.close()
          } catch (error: Throwable) {
            Log.e(TAG, "Could not read screenshot buffer", error)
          } finally {
            latch.countDown()
          }
        }

        override fun onFailure(errorCode: Int) {
          Log.w(TAG, "Screenshot failed: $errorCode")
          latch.countDown()
        }
      }
    )
    latch.await(3, TimeUnit.SECONDS)
    return bitmap
  }

  // ------------------------------------------------------------------ helpers

  private fun findScrollable(): AccessibilityNodeInfo? {
    val root = rootInActiveWindow ?: return null
    var result: AccessibilityNodeInfo? = null
    walk(root, 200) { node ->
      if (node.isScrollable) {
        result = node
        false
      } else {
        true
      }
    }
    return result
  }

  private fun findBestNode(query: String, editable: Boolean): AccessibilityNodeInfo? {
    val needle = normalize(query)
    if (needle.isBlank()) return null
    val root = rootInActiveWindow ?: return null
    var best: AccessibilityNodeInfo? = null
    var bestScore = 0

    walk(root, 250) { node ->
      if (editable && !node.isEditable) return@walk true
      val fields = listOf(
        node.text?.toString(),
        node.contentDescription?.toString(),
        node.viewIdResourceName
      ).mapNotNull { it?.let(::normalize) }.filter { it.isNotBlank() }

      val score = fields.maxOfOrNull { field ->
        when {
          field == needle -> 100
          field.startsWith(needle) -> 70
          field.contains(needle) -> 40
          needle.contains(field) && field.length >= 3 -> 20
          else -> 0
        }
      } ?: 0

      // Prefer actionable and editable nodes when scores tie.
      val adjusted = score + if (node.isClickable || node.isEditable) 5 else 0
      if (adjusted > bestScore) {
        bestScore = adjusted
        best = node
      }
      true
    }
    return bestScore.takeIf { it > 0 }?.let { best }
  }

  private fun walk(
    root: AccessibilityNodeInfo,
    limit: Int,
    visit: (AccessibilityNodeInfo) -> Boolean
  ) {
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    var visited = 0
    while (queue.isNotEmpty() && visited < limit) {
      val node = queue.removeFirst()
      visited++
      if (!visit(node)) return
      for (index in 0 until node.childCount) {
        node.getChild(index)?.let(queue::add)
      }
    }
  }

  private fun collect(
    node: AccessibilityNodeInfo,
    output: JSONArray,
    depth: Int,
    counter: IntArray,
    limit: Int
  ) {
    if (counter[0] >= limit || depth > 18) return
    counter[0]++
    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    output.put(
      JSONObject()
        .put("text", node.text?.toString().orEmpty().take(180))
        .put("desc", node.contentDescription?.toString().orEmpty().take(180))
        .put("id", node.viewIdResourceName.orEmpty())
        .put("class", node.className?.toString().orEmpty().substringAfterLast('.'))
        .put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
        .put("clickable", node.isClickable)
        .put("editable", node.isEditable)
        .put("scrollable", node.isScrollable)
        .put("focused", node.isFocused)
    )
    for (index in 0 until node.childCount) {
      node.getChild(index)?.let { collect(it, output, depth + 1, counter, limit) }
    }
  }

  private fun describe(node: AccessibilityNodeInfo): String =
    node.text?.toString()?.take(60)
      ?: node.contentDescription?.toString()?.take(60)
      ?: node.viewIdResourceName?.substringAfterLast('/')
      ?: "element"

  private fun normalize(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

  @SuppressLint("Recycle")
  private fun tap(x: Float, y: Float): Boolean {
    val path = Path().apply { moveTo(x, y) }
    val gesture = GestureDescription.Builder()
      .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
      .build()
    return dispatchGesture(gesture, null, null)
  }

  private fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
    val path = Path().apply {
      moveTo(fromX, fromY)
      lineTo(toX, toY)
    }
    val gesture = GestureDescription.Builder()
      .addStroke(GestureDescription.StrokeDescription(path, 0, 320))
      .build()
    return dispatchGesture(gesture, null, null)
  }

  private fun success(action: String, extra: JSONObject.() -> Unit = {}): JSONObject =
    JSONObject().put("success", true).put("action", action).apply(extra)

  private fun failure(action: String, error: String): JSONObject =
    JSONObject().put("success", false).put("action", action).put("error", error)

  companion object {
    private const val TAG = "ZedAccessibility"

    @Volatile
    var instance: ZedAccessibilityService? = null
      private set

    fun isConnected(): Boolean = instance != null

    fun screenSnapshot(): JSONObject = instance?.snapshot()
      ?: JSONObject().put("success", false).put("error", "accessibility_service_disabled")

    fun screenText(): String = instance?.readText().orEmpty()

    fun click(query: String): JSONObject = instance?.clickElement(query)
      ?: JSONObject().put("success", false).put("error", "accessibility_service_disabled")

    fun clickSend(): JSONObject = instance?.clickWhatsAppSend()
      ?: JSONObject().put("success", false).put("error", "accessibility_service_disabled")

    fun clickYouTubeResult(query: String): JSONObject = instance?.clickYouTubeResult(query)
      ?: JSONObject().put("success", false).put("error", "accessibility_service_disabled")

    fun type(text: String, targetHint: String?): JSONObject = instance?.typeText(text, targetHint)
      ?: JSONObject().put("success", false).put("error", "accessibility_service_disabled")

    fun scroll(direction: String): JSONObject = instance?.scroll(direction)
      ?: JSONObject().put("success", false).put("error", "accessibility_service_disabled")

    fun press(key: String): JSONObject = instance?.pressKey(key)
      ?: JSONObject().put("success", false).put("error", "accessibility_service_disabled")

    fun screenshotJpeg(): ByteArray? {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
      return instance?.captureJpeg()
    }
  }
}
