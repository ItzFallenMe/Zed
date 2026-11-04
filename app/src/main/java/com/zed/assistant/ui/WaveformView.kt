package com.zed.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.zed.assistant.ui.theme.PrimaryRed
import com.zed.assistant.ui.theme.Purple
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun WaveformView(
  level: Float,
  active: Boolean,
  modifier: Modifier = Modifier
) {
  val barCount = 24
  var heights by remember { mutableStateOf(FloatArray(barCount) { 0.12f }) }
  val currentLevel by rememberUpdatedState(level)

  LaunchedEffect(active) {
    while (true) {
      val next = FloatArray(barCount)
      for (index in 0 until barCount) {
        val amplitude = currentLevel.coerceIn(0f, 1f)
        val target = if (active) {
          (0.18f + Random.nextFloat() * 0.82f * amplitude.coerceAtLeast(0.10f))
            .coerceIn(0.12f, 1f)
        } else {
          0.10f
        }
        next[index] = heights[index] + (target - heights[index]) * 0.35f
      }
      heights = next
      delay(55)
    }
  }

  val colors = listOf(
    PrimaryRed.copy(alpha = 0.45f),
    PrimaryRed.copy(alpha = 0.7f),
    Purple.copy(alpha = 0.85f),
    Purple,
    PrimaryRed,
    PrimaryRed.copy(alpha = 0.6f),
    Purple.copy(alpha = 0.9f),
    PrimaryRed.copy(alpha = 0.5f)
  )

  Canvas(modifier = modifier.width(260.dp).height(44.dp)) {
    val barWidth = 5.dp.toPx()
    val gap = (size.width - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
    val maxHeight = size.height

    for (index in 0 until barCount) {
      val height = heights[index] * maxHeight
      val x = index * (barWidth + gap) + barWidth / 2f
      drawLine(
        color = colors[index % colors.size],
        start = Offset(x, maxHeight),
        end = Offset(x, maxHeight - height.coerceAtLeast(barWidth)),
        strokeWidth = barWidth,
        cap = StrokeCap.Round
      )
    }
  }
}
