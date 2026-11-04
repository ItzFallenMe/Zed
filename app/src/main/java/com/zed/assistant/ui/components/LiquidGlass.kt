package com.zed.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zed.assistant.ui.theme.LocalZedPalette

/** Page background with the soft liquid-glass glow fields from the reference design. */
@Composable
fun LiquidBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  val palette = LocalZedPalette.current
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(palette.background)
  ) {
    AmbientGlow(
      color = palette.glowOne,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .offset(x = 96.dp, y = (-86).dp)
        .size(290.dp)
    )
    AmbientGlow(
      color = palette.glowTwo,
      modifier = Modifier
        .align(Alignment.CenterStart)
        .offset(x = (-116).dp, y = (-20).dp)
        .size(280.dp)
    )
    AmbientGlow(
      color = palette.glowThree,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .offset(x = 92.dp, y = 108.dp)
        .size(300.dp)
    )
    content()
  }
}

@Composable
fun AmbientGlow(
  color: Color,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(CircleShape)
      .background(
        Brush.radialGradient(
          colors = listOf(color, color.copy(alpha = color.alpha * 0.35f), Color.Transparent)
        )
      )
  )
}

/**
 * Translucent card with a light-to-dark glass gradient, a hairline border and
 * a subtle specular highlight across the top edge.
 */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(20.dp),
  contentPadding: androidx.compose.foundation.layout.PaddingValues =
    androidx.compose.foundation.layout.PaddingValues(16.dp),
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val palette = LocalZedPalette.current
  val interaction = if (onClick != null) {
    Modifier.clickable(role = Role.Button, onClick = onClick)
  } else {
    Modifier
  }
  Column(
    modifier = modifier
      .shadow(10.dp, shape, clip = false)
      .clip(shape)
      .background(
        Brush.verticalGradient(listOf(palette.cardGlassStart, palette.cardGlassEnd))
      )
      .border(1.dp, palette.cardBorder.copy(alpha = 0.85f), shape)
      .then(interaction)
      .padding(contentPadding),
    content = content
  )
}

/** Compact icon button used in the top bars. */
@Composable
fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color? = null
) {
  val palette = LocalZedPalette.current
  Box(
    modifier = modifier
      .size(46.dp)
      .clip(RoundedCornerShape(15.dp))
      .background(
        Brush.verticalGradient(
          listOf(palette.cardGlassStart.copy(alpha = 0.92f), palette.cardGlassEnd.copy(alpha = 0.72f))
        )
      )
      .border(1.dp, palette.cardBorder.copy(alpha = 0.75f), RoundedCornerShape(15.dp))
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint ?: palette.textPrimary,
      modifier = Modifier.size(22.dp)
    )
  }
}

/** Small translucent chip used for metadata and status labels. */
@Composable
fun GlassChip(
  label: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  contentColor: Color? = null,
  background: Color? = null
) {
  val palette = LocalZedPalette.current
  val color = contentColor ?: palette.textSecondary
  Surface(
    modifier = modifier,
    color = background ?: palette.card.copy(alpha = 0.7f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
      }
      Text(
        text = label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
      )
    }
  }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  val palette = LocalZedPalette.current
  Text(
    text = text.uppercase(),
    modifier = modifier.padding(top = 6.dp, bottom = 2.dp),
    color = palette.textMuted,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.6.sp
  )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Int = 8) {
  Spacer(
    modifier = modifier
      .size(size.dp)
      .clip(CircleShape)
      .background(color)
  )
}

/** Gradient ZED wordmark used on Home and in the Settings profile card. */
@Composable
fun ZedWordmark(
  modifier: Modifier = Modifier,
  fontSize: Int = 27,
  letterSpacing: Int = 7
) {
  val palette = LocalZedPalette.current
  Text(
    text = "ZED",
    modifier = modifier,
    style = TextStyle(
      brush = Brush.linearGradient(
        listOf(palette.accent, palette.accentSecondary, palette.accentTertiary)
      ),
      fontSize = fontSize.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = letterSpacing.sp
    )
  )
}


