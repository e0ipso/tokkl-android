package com.github.e0ipso.tokkl.settings

import android.app.UiModeManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** The night mode Android gets for each theme mode, which picks the starting window's colours on Android 12 and later. */
class ApplicationNightModeTest {

    @Test
    fun eachThemeModeGivesAndroidItsNightMode() {
        val nightModes = ThemeMode.entries.associateWith { it.applicationNightMode }

        assertEquals(
            mapOf(
                ThemeMode.LIGHT to UiModeManager.MODE_NIGHT_NO,
                ThemeMode.DARK to UiModeManager.MODE_NIGHT_YES,
                ThemeMode.SYSTEM to UiModeManager.MODE_NIGHT_AUTO,
            ),
            nightModes,
        )
    }
}
