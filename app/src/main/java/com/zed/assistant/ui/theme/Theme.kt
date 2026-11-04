package com.zed.assistant.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ZedColorScheme = darkColorScheme(
  primary = PrimaryRed,
  secondary = Purple,
  tertiary = Success,
  background = Background,
  surface = Cards,
  onPrimary = PrimaryText,
  onSecondary = PrimaryText,
  onTertiary = Background,
  onBackground = PrimaryText,
  onSurface = PrimaryText
)

@Composable
fun ZedTheme(content: @Composable () -> Unit) {
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      window.statusBarColor = Background.toArgb()
      window.navigationBarColor = Background.toArgb()
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
  }

  MaterialTheme(
    colorScheme = ZedColorScheme,
    typography = Typography,
    content = content
  )
}
