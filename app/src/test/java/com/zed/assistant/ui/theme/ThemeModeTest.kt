package com.zed.assistant.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

  @Test
  fun fromKeyIsCaseInsensitiveAndFallsBackToObsidian() {
    assertEquals(ZedThemeMode.DARK, ZedThemeMode.fromKey("dark"))
    assertEquals(ZedThemeMode.SUNSET, ZedThemeMode.fromKey("SUNSET"))
    assertEquals(ZedThemeMode.DARK, ZedThemeMode.fromKey("unknown"))
    assertEquals(ZedThemeMode.DARK, ZedThemeMode.fromKey(null))
  }

  @Test
  fun everyModeMapsToACompleteDistinctPalette() {
    val accents = ZedThemeMode.entries.map { it.palette().accent }
    assertEquals(ZedThemeMode.entries.size, accents.toSet().size)
    ZedThemeMode.entries.forEach { mode ->
      val palette = mode.palette()
      assertEquals(mode.isLight, palette.isLight)
    }
    assertTrue(ZedThemeMode.LIGHT.palette().isLight)
    assertFalse(ZedThemeMode.DARK.palette().isLight)
  }

  @Test
  fun obsidianIsTrueBlack() {
    assertEquals(Color(0xFF000000), ZedThemeMode.DARK.palette().background)
  }
}
