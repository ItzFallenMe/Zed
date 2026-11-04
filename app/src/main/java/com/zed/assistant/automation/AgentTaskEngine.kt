package com.zed.assistant.automation

import com.zed.assistant.ai.DeviceActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A deliberately small, sequential task runner for multi-step phone requests.
 *
 * Unlike a free-form loop, every step is declared up front by Gemini, is
 * bounded, and stops at the first failure. Steps act through the same
 * accessibility/device helpers as single tool calls, so the permission and
 * honesty rules stay in one place.
 */
object AgentTaskEngine {

  const val MAX_STEPS = 12

  data class Step(val op: String, val args: JSONObject) {
    companion object {
      fun from(json: JSONObject): Step = Step(
        op = json.optString("op").trim().lowercase(),
        args = json
      )
    }
  }

  data class Progress(
    val goal: String,
    val index: Int,
    val total: Int,
    val label: String,
    val running: Boolean
  )

  private val _progress = MutableStateFlow<Progress?>(null)
  val progress: StateFlow<Progress?> = _progress
  val isRunning: Boolean get() = running

  @Volatile private var running = false
  @Volatile private var cancelled = false

  fun cancel() {
    if (running) cancelled = true
  }

  suspend fun run(
    goal: String,
    steps: List<Step>,
    executor: DeviceActions,
    onProgress: (String) -> Unit = {},
    onFrame: (ByteArray) -> Unit = {}
  ): JSONObject {
    if (running) return failure(goal, "task_already_running")
    if (steps.isEmpty()) return failure(goal, "empty_task")
    if (steps.size > MAX_STEPS) return failure(goal, "too_many_steps")
    running = true
    cancelled = false
    val results = JSONArray()
    var completed = 0

    try {
      for ((index, step) in steps.withIndex()) {
        if (cancelled) return stopped(goal, results, completed, "cancelled")
        val label = labelFor(step, index + 1)
        onProgress("Task ${index + 1}/${steps.size} · $label")
        _progress.value = Progress(goal, index + 1, steps.size, label, true)

        val result = execute(step, executor, onFrame)
        results.put(
          JSONObject()
            .put("step", index + 1)
            .put("op", step.op)
            .put("label", label)
            .put("result", result)
        )
        if (!result.optBoolean("success")) {
          return stopped(
            goal,
            results,
            completed,
            result.optString("error", "step_failed"),
            failedAt = index + 1
          )
        }
        completed += 1

        val expected = step.args.optString("expect").trim()
        if (expected.isNotBlank()) {
          delay(450L)
          val screen = ZedAccessibilityService.screenText()
          if (!screen.contains(expected, ignoreCase = true)) {
            return stopped(
              goal,
              results,
              completed,
              "verification_failed",
              failedAt = index + 1
            )
          }
        }
        val waitMs = step.args.optLong("waitMs", 750L).coerceIn(0L, 5_000L)
        if (waitMs > 0) delay(waitMs)
      }
      return JSONObject()
        .put("success", true)
        .put("goal", goal)
        .put("completedSteps", completed)
        .put("results", results)
    } finally {
      running = false
      _progress.value = null
    }
  }

  private suspend fun execute(
    step: Step,
    executor: DeviceActions,
    onFrame: (ByteArray) -> Unit
  ): JSONObject {
    val args = step.args
    return when (step.op) {
      "open_app" -> executor.openAnyApp(args.optString("appName"))
      "open_url" -> executor.openUrl(args.optString("url"))
      "whatsapp" -> executor.sendWhatsAppMessage(
        args.optString("contactName"),
        args.optString("message"),
        sendNow = args.optBoolean("sendNow", false)
      )
      "youtube" -> executor.playYouTube(args.optString("query"))
      "media" -> executor.controlMedia(args.optString("mediaAction"))
      "volume" -> executor.setVolume(args.optInt("percent", 50))
      "brightness" -> executor.setBrightness(args.optInt("percent", 50))
      "flashlight" -> executor.setFlashlight(args.optBoolean("on", true))
      "call" -> {
        val contact = args.optString("contactName").trim()
        if (contact.isNotBlank()) executor.callContact(contact)
        else executor.makeCall(args.optString("phoneNumber"))
      }
      "tap" -> ZedAccessibilityService.click(args.optString("text"))
      "type" -> ZedAccessibilityService.type(
        args.optString("text"),
        args.optString("targetHint").takeIf { it.isNotBlank() }
      )
      "scroll" -> ZedAccessibilityService.scroll(args.optString("direction", "down"))
      "press" -> ZedAccessibilityService.press(args.optString("key", "back"))
      "inspect" -> ZedAccessibilityService.screenSnapshot()
      "look" -> {
        val jpeg = ZedAccessibilityService.screenshotJpeg()
        if (jpeg == null) {
          JSONObject().put("success", false).put("error", "screenshot_unavailable")
        } else {
          onFrame(jpeg)
          JSONObject().put("success", true).put("bytes", jpeg.size)
        }
      }
      "wait" -> JSONObject().put("success", true).put("waitedMs", args.optInt("waitMs", 1000))
      else -> JSONObject()
        .put("success", false)
        .put("error", "unsupported_step")
        .put("op", step.op)
    }
  }

  private fun labelFor(step: Step, index: Int): String {
    val target = when (step.op) {
      "open_app" -> step.args.optString("appName")
      "tap" -> step.args.optString("text")
      "type" -> "typing"
      "whatsapp" -> step.args.optString("contactName")
      "youtube" -> step.args.optString("query")
      "open_url" -> step.args.optString("url")
      else -> step.op.replace('_', ' ')
    }
    return listOf("step $index", target).filter { it.isNotBlank() }.joinToString(": ")
      .take(42)
  }

  private fun stopped(
    goal: String,
    results: JSONArray,
    completed: Int,
    error: String,
    failedAt: Int = 0
  ): JSONObject = JSONObject()
    .put("success", false)
    .put("goal", goal)
    .put("error", error)
    .put("completedSteps", completed)
    .put("failedStep", failedAt)
    .put("results", results)

  private fun failure(goal: String, error: String): JSONObject = JSONObject()
    .put("success", false)
    .put("goal", goal)
    .put("error", error)
    .put("completedSteps", 0)
}
