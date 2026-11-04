package com.zed.assistant.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.zed.assistant.ai.ZedEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val LocalZedPalette = staticCompositionLocalOf { PearlPalette }

/**
 * Persists the selected appearance mode and exposes it as a single source of
 * truth. Initialised by [com.zed.assistant.viewmodel.MainViewModel].
 */
object ZedThemeRepository {
  private const val KEY_THEME = "app_theme"

  private val _mode = MutableStateFlow(ZedThemeMode.LIGHT)
  val mode: StateFlow<ZedThemeMode> = _mode

  fun initialize(context: Context) {
    val prefs = context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
    _mode.value = ZedThemeMode.fromKey(prefs.getString(KEY_THEME, null))
  }

  fun set(context: Context, mode: ZedThemeMode) {
    _mode.value = mode
    context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
      .edit { putString(KEY_THEME, mode.key) }
  }
}

@Composable
fun ZedTheme(
  mode: ZedThemeMode = ZedThemeMode.LIGHT,
  content: @Composable () -> Unit
) {
  val palette = mode.palette()
  val colorScheme = if (palette.isLight) {
    lightColorScheme(
      primary = palette.accent,
      onPrimary = palette.specular,
      secondary = palette.accentSecondary,
      onSecondary = palette.specular,
      tertiary = palette.accentTertiary,
      background = palette.background,
      onBackground = palette.textPrimary,
      surface = palette.card,
      onSurface = palette.textPrimary,
      surfaceVariant = palette.accentSoft,
      onSurfaceVariant = palette.textSecondary,
      outline = palette.cardBorder,
      error = palette.error
    )
  } else {
    darkColorScheme(
      primary = palette.accent,
      onPrimary = palette.specular,
      secondary = palette.accentSecondary,
      onSecondary = palette.specular,
      tertiary = palette.accentTertiary,
      background = palette.background,
      onBackground = palette.textPrimary,
      surface = palette.card,
      onSurface = palette.textPrimary,
      surfaceVariant = palette.accentSoft,
      onSurfaceVariant = palette.textSecondary,
      outline = palette.cardBorder,
      error = palette.error
    )
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      @Suppress("DEPRECATION")
      window.statusBarColor = palette.background.toArgb()
      @Suppress("DEPRECATION")
      window.navigationBarColor = palette.background.toArgb()
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = palette.isLight
        isAppearanceLightNavigationBars = palette.isLight
      }
    }
  }

  CompositionLocalProvider(LocalZedPalette provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
