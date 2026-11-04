package com.zed.assistant.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.zed.assistant.ai.ZedEngine
import com.zed.assistant.data.ZedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val LocalZedPalette = staticCompositionLocalOf { PearlPalette }

/** When false (the default), every shared surface uses plain Material 3. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/**
 * Persists the selected appearance mode and exposes it as a single source of
 * truth. Initialised by [com.zed.assistant.viewmodel.MainViewModel].
 */
object ZedThemeRepository {
  private const val KEY_THEME = "app_theme"

  private val _mode = MutableStateFlow(ZedThemeMode.DARK)
  val mode: StateFlow<ZedThemeMode> = _mode

  private val _liquidGlass = MutableStateFlow(false)
  val liquidGlass: StateFlow<Boolean> = _liquidGlass

  fun initialize(context: Context) {
    val prefs = context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
    _mode.value = ZedThemeMode.fromKey(prefs.getString(KEY_THEME, null))
    _liquidGlass.value = prefs.getBoolean(ZedPreferences.KEY_LIQUID_GLASS, false)
  }

  fun set(context: Context, mode: ZedThemeMode) {
    _mode.value = mode
    context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
      .edit { putString(KEY_THEME, mode.key) }
  }

  fun setLiquidGlass(context: Context, enabled: Boolean) {
    _liquidGlass.value = enabled
    context.getSharedPreferences(ZedEngine.PREFS, Context.MODE_PRIVATE)
      .edit { putBoolean(ZedPreferences.KEY_LIQUID_GLASS, enabled) }
  }
}

@Composable
fun ZedTheme(
  mode: ZedThemeMode = ZedThemeMode.DARK,
  content: @Composable () -> Unit
) {
  val palette = mode.palette()
  val liquidGlass by ZedThemeRepository.liquidGlass.collectAsState()
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

  CompositionLocalProvider(
    LocalZedPalette provides palette,
    LocalLiquidGlassEnabled provides liquidGlass
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
