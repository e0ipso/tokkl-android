package com.github.e0ipso.tokkl.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {

    @Test
    fun defaultsFollowTheSystemWithTokklsPalette() {
        assertEquals(ThemeSettings(mode = ThemeMode.SYSTEM, dynamicColor = false), ThemeSettings())
    }

    @Test
    fun lightAndDarkIgnoreTheSystemAndSystemFollowsIt() {
        for (systemIsDark in listOf(false, true)) {
            assertFalse(ThemeMode.LIGHT.isDark(systemIsDark))
            assertTrue(ThemeMode.DARK.isDark(systemIsDark))
            assertEquals(systemIsDark, ThemeMode.SYSTEM.isDark(systemIsDark))
        }
    }
}
