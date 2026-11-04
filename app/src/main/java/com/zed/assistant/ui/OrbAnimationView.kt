package com.zed.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.zed.assistant.model.AssistantState
import com.zed.assistant.ui.theme.Background
import com.zed.assistant.ui.theme.Hint
import com.zed.assistant.ui.theme.PrimaryRed
import com.zed.assistant.ui.theme.Purple
import com.zed.assistant.ui.theme.Success
import com.zed.assistant.ui.theme.Warning

@Composable
fun OrbAnimationView(
  state: AssistantState,
  level: Float,
  modifier: Modifier = Modifier
) {
  val active = state == AssistantState.LISTENING || state == AssistantState.SPEAKING
  val transition = rememberInfiniteTransition(label = "orb")

  val rotation by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
    label = "rotation"
  )

  val pulse by transition.animateFloat(
    initialValue = 1f,
    targetValue = if (state == AssistantState.SPEAKING) 1.1f else if (active) 1.05f else 1f,
    animationSpec = infiniteRepeatable(
      tween(if (state == AssistantState.SPEAKING) 520 else 1300, easing = FastOutSlowInEasing),
      RepeatMode.Reverse
    ),
    label = "pulse"
  )

  val ripple by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(if (state == AssistantState.SPEAKING) 1100 else 2000, easing = LinearOutSlowInEasing),
      RepeatMode.Restart
    ),
    label = "ripple"
  )

  val accent = when (state) {
    AssistantState.SPEAKING -> Purple
    AssistantState.LISTENING -> PrimaryRed
    AssistantState.CONNECTING -> Warning
    AssistantState.ERROR -> PrimaryRed
    AssistantState.IDLE -> PrimaryRed
  }

  Box(modifier = modifier.size(240.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val baseRadius = size.minDimension / 2f * 0.70f
      val radius = baseRadius * pulse * (1f + level * 0.10f)

      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            accent.copy(alpha = if (active) 0.30f else 0.12f),
            Color.Transparent
          ),
          center = center,
          radius = radius * 1.7f
        ),
        radius = radius * 1.7f,
        center = center
      )

      drawCircle(
        color = accent.copy(alpha = 0.22f),
        radius = radius * 1.15f,
        center = center,
        style = Stroke(width = 1.dp.toPx())
      )

      rotate(degrees = if (active) rotation else 0f, pivot = center) {
        drawCircle(
          color = Purple.copy(alpha = 0.16f),
          radius = radius * 1.3f,
          center = center,
          style = Stroke(width = 1.dp.toPx())
        )
      }

      if (active) {
        val secondRipple = (ripple + 0.5f) % 1f
        drawCircle(
          color = accent.copy(alpha = (1f - ripple) * 0.35f),
          radius = radius * (1f + ripple * 0.7f),
          center = center,
          style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
          color = Purple.copy(alpha = (1f - secondRipple) * 0.25f),
          radius = radius * (1f + secondRipple * 0.7f),
          center = center,
          style = Stroke(width = 2.dp.toPx())
        )
      }

      val coreRadius = radius * 0.78f
      drawCircle(
        brush = Brush.linearGradient(listOf(accent, Purple, accent)),
        radius = coreRadius,
        center = center,
        style = Stroke(width = 4.dp.toPx())
      )
      drawCircle(color = Background, radius = coreRadius - 2.dp.toPx(), center = center)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(accent.copy(alpha = 0.42f), Color.Transparent),
          center = center,
          radius = coreRadius * 0.8f
        ),
        radius = coreRadius * 0.8f,
        center = center
      )
    }

    val dotColor = when (state) {
      AssistantState.CONNECTING -> Warning
      AssistantState.SPEAKING -> Purple
      AssistantState.LISTENING -> Success
      AssistantState.ERROR -> PrimaryRed
      AssistantState.IDLE -> Hint
    }

    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 22.dp, end = 22.dp)
        .size(8.dp)
        .clip(CircleShape)
        .background(dotColor)
    )
  }
}
