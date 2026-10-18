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

    /** API 31 is Android 12, the first with wallpaper colours. The app supports API 26 and up. */
    @Test
    fun wallpaperColoursAreOfferedFromAndroid12() {
        for (sdkInt in 26..30) assertFalse("API $sdkInt", dynamicColorAvailable(sdkInt))
        for (sdkInt in 31..37) assertTrue("API $sdkInt", dynamicColorAvailable(sdkInt))
    }
}
