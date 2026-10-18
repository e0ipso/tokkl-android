package com.github.e0ipso.tokkl.ui.theme

import android.app.UiModeManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Launch paints the window in the colour scheme's background before Compose draws its first
 * frame, so starting the app shows no flash of another colour, in light and in dark.
 *
 * Sets the app's own night mode, which Android keeps until it is changed, and hands the choice
 * back to the system afterwards. UiModeManager.setApplicationNightMode needs Android 12.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class WindowBackgroundTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val uiModeManager = InstrumentationRegistry.getInstrumentation().targetContext
        .getSystemService(UiModeManager::class.java)

    @After
    fun followTheSystemNightModeAgain() {
        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
    }

    @Test
    fun lightLaunchShowsTheLightSchemeBackground() {
        assertWindowAndContentBackground(UiModeManager.MODE_NIGHT_NO, TokklLightColorScheme.background)
    }

    @Test
    fun darkLaunchShowsTheDarkSchemeBackground() {
        assertWindowAndContentBackground(UiModeManager.MODE_NIGHT_YES, TokklDarkColorScheme.background)
    }

    private fun assertWindowAndContentBackground(nightMode: Int, schemeBackground: Color) {
        uiModeManager.setApplicationNightMode(nightMode)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var window: Int? = null
            scenario.onActivity { window = (it.window.decorView.background as? ColorDrawable)?.color }
            // The launch screen leaves its left edge empty, so the pixel there is the
            // Scaffold's container colour, which is the scheme background.
            val content = composeRule.onRoot().captureToImage().toPixelMap().let { it[0, it.height / 2] }

            assertEquals("window background", hex(schemeBackground.toArgb()), window?.let(::hex))
            assertEquals("content background", hex(schemeBackground.toArgb()), hex(content.toArgb()))
        }
    }

    private fun hex(argb: Int): String = "#%08X".format(argb)
}
