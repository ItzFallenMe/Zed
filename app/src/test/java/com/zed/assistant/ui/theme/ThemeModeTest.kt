package com.zed.assistant.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

  @Test
  fun fromKeyIsCaseInsensitiveAndFallsBackToPearl() {
    assertEquals(ZedThemeMode.DARK, ZedThemeMode.fromKey("dark"))
    assertEquals(ZedThemeMode.SUNSET, ZedThemeMode.fromKey("SUNSET"))
    assertEquals(ZedThemeMode.LIGHT, ZedThemeMode.fromKey("unknown"))
    assertEquals(ZedThemeMode.LIGHT, ZedThemeMode.fromKey(null))
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
  fun orbThemeKeysMatchPaletteModes() {
    assertEquals("light", ZedThemeMode.LIGHT.orbThemeKey)
    assertEquals("dark", ZedThemeMode.DARK.orbThemeKey)
    assertEquals("red", ZedThemeMode.RED.orbThemeKey)
    assertEquals("sunset", ZedThemeMode.SUNSET.orbThemeKey)
  }
}
