package com.zed.assistant.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Zed's liquid-glass design tokens.
 *
 * The four modes mirror the reference design: Pearl (light), Obsidian (dark),
 * Ruby (red), and Sunset. Components read the active palette through
 * [LocalZedPalette] so a theme change recomposes the whole UI without touching
 * the assistant engine.
 */
enum class ZedThemeMode(val key: String, val label: String, val isLight: Boolean) {
  LIGHT("light", "Pearl light", true),
  DARK("dark", "Obsidian dark", false),
  RED("red", "Ruby red", false),
  SUNSET("sunset", "Sunset glass", true);

  val orbThemeKey: String
    get() = when (this) {
      LIGHT -> "light"
      DARK -> "dark"
      RED -> "red"
      SUNSET -> "sunset"
    }

  companion object {
    fun fromKey(key: String?): ZedThemeMode =
      entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: LIGHT
  }
}

data class ZedPalette(
  val isLight: Boolean,
  val background: Color,
  val backgroundAlt: Color,
  val card: Color,
  val cardGlassStart: Color,
  val cardGlassEnd: Color,
  val cardBorder: Color,
  val dockStart: Color,
  val dockEnd: Color,
  val dockBorder: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val accent: Color,
  val accentSecondary: Color,
  val accentTertiary: Color,
  val accentSoft: Color,
  val success: Color,
  val error: Color,
  val warning: Color,
  val glowOne: Color,
  val glowTwo: Color,
  val glowThree: Color,
  val specular: Color
)

val PearlPalette = ZedPalette(
  isLight = true,
  background = Color(0xFFEEF2F9),
  backgroundAlt = Color(0xFFF8FAFC),
  card = Color(0xB8FFFFFF),
  cardGlassStart = Color(0xF0FFFFFF),
  cardGlassEnd = Color(0xA6FFFFFF),
  cardBorder = Color(0xFFCBD5E1),
  dockStart = Color(0xFAFFFFFF),
  dockEnd = Color(0xB8FFFFFF),
  dockBorder = Color(0xFFCBD5E1),
  textPrimary = Color(0xFF0F172A),
  textSecondary = Color(0xFF475569),
  textMuted = Color(0xFF64748B),
  accent = Color(0xFF2F6BFF),
  accentSecondary = Color(0xFF8B5CF6),
  accentTertiary = Color(0xFF06B6D4),
  accentSoft = Color(0xFFE0E7FF),
  success = Color(0xFF10B981),
  error = Color(0xFFEF4444),
  warning = Color(0xFFF59E0B),
  glowOne = Color(0x332F6BFF),
  glowTwo = Color(0x268B5CF6),
  glowThree = Color(0x1F06B6D4),
  specular = Color(0xFFFFFFFF)
)

val ObsidianPalette = ZedPalette(
  isLight = false,
  background = Color(0xFF070914),
  backgroundAlt = Color(0xFF0D1126),
  card = Color(0xCC141B3D),
  cardGlassStart = Color(0xCC1B2654),
  cardGlassEnd = Color(0x990F1733),
  cardBorder = Color(0x665B8CFF),
  dockStart = Color(0xE6172048),
  dockEnd = Color(0xCC0B1028),
  dockBorder = Color(0x665B8CFF),
  textPrimary = Color(0xFFF8FAFC),
  textSecondary = Color(0xFF94A3B8),
  textMuted = Color(0xFF8B9AC4),
  accent = Color(0xFF5B8CFF),
  accentSecondary = Color(0xFFA78BFA),
  accentTertiary = Color(0xFF22D3EE),
  accentSoft = Color(0xFF1E2858),
  success = Color(0xFF34D399),
  error = Color(0xFFFB7185),
  warning = Color(0xFFFBBF24),
  glowOne = Color(0x335B8CFF),
  glowTwo = Color(0x33A78BFA),
  glowThree = Color(0x2622D3EE),
  specular = Color(0xFFA5C8FF)
)

val RubyPalette = ZedPalette(
  isLight = false,
  background = Color(0xFF0E0307),
  backgroundAlt = Color(0xFF1A060D),
  card = Color(0xCC2D0A13),
  cardGlassStart = Color(0xCC3A0F1A),
  cardGlassEnd = Color(0x991A050D),
  cardBorder = Color(0x66FF4D5E),
  dockStart = Color(0xE6300A14),
  dockEnd = Color(0xCC170409),
  dockBorder = Color(0x66FF4D5E),
  textPrimary = Color(0xFFFFF1F2),
  textSecondary = Color(0xFFFDA4AF),
  textMuted = Color(0xFFBE727E),
  accent = Color(0xFFFF4D5E),
  accentSecondary = Color(0xFFFB7185),
  accentTertiary = Color(0xFFF97316),
  accentSoft = Color(0xFF3A0C16),
  success = Color(0xFF34D399),
  error = Color(0xFFFF6B7A),
  warning = Color(0xFFFBBF24),
  glowOne = Color(0x40FF4D5E),
  glowTwo = Color(0x33FB7185),
  glowThree = Color(0x26F97316),
  specular = Color(0xFFFFA6B0)
)

val SunsetPalette = ZedPalette(
  isLight = true,
  background = Color(0xFFFFF0EA),
  backgroundAlt = Color(0xFFFFF7F3),
  card = Color(0xCCFFF2EC),
  cardGlassStart = Color(0xF5FFFFFF),
  cardGlassEnd = Color(0xB3FFE2D7),
  cardBorder = Color(0x402E1020),
  dockStart = Color(0xFAFFFFFF),
  dockEnd = Color(0xCCFFE8DF),
  dockBorder = Color(0x402E1020),
  textPrimary = Color(0xFF2E1020),
  textSecondary = Color(0xFF783A5B),
  textMuted = Color(0xFF9C6282),
  accent = Color(0xFFFF6A3D),
  accentSecondary = Color(0xFFF43F5E),
  accentTertiary = Color(0xFF8B5CF6),
  accentSoft = Color(0xFFFFE6DD),
  success = Color(0xFF10B981),
  error = Color(0xFFE11D48),
  warning = Color(0xFFF59E0B),
  glowOne = Color(0x33FF6A3D),
  glowTwo = Color(0x2EF43F5E),
  glowThree = Color(0x268B5CF6),
  specular = Color(0xFFFFFFFF)
)

fun ZedThemeMode.palette(): ZedPalette = when (this) {
  ZedThemeMode.LIGHT -> PearlPalette
  ZedThemeMode.DARK -> ObsidianPalette
  ZedThemeMode.RED -> RubyPalette
  ZedThemeMode.SUNSET -> SunsetPalette
}
