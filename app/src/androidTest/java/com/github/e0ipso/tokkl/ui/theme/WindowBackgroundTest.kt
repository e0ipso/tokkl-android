package com.github.e0ipso.tokkl.ui.theme

import android.app.Activity
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
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.settings.DefaultThemeSettings
import com.github.e0ipso.tokkl.settings.ThemeMode
import com.github.e0ipso.tokkl.settings.ThemeSettings
import com.github.e0ipso.tokkl.settings.ThemeSettingsStore
import com.github.e0ipso.tokkl.settings.systemIsDark
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Launch shows the chosen scheme's background from the starting window on, so starting the app
 * shows no flash of another colour. That holds pinned to light, pinned to dark and following the
 * system.
 *
 * Android draws the starting window before the app runs, from the manifest theme and the night
 * mode it keeps for the app, which the app sets from the theme setting. That needs Android 12.
 * So each test launches twice. The first launch hands the mode to Android. The second has to
 * start in it, in the same configuration Android draws a cold start's starting window in.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class WindowBackgroundTest {
    /** Signed in, so the Timer page shows. */
    @get:Rule(order = 0)
    val session = SessionRule()

    /** Also hands the night mode back to the system before and after each test. */
    @get:Rule(order = 1)
    val themeSettings = DefaultThemeSettings()

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun lightLaunchShowsTheLightSchemeBackground() {
        assertLaunches(ThemeMode.LIGHT, dark = false)
    }

    @Test
    fun darkLaunchShowsTheDarkSchemeBackground() {
        assertLaunches(ThemeMode.DARK, dark = true)
    }

    @Test
    fun followingTheSystemLaunchShowsTheSystemsSchemeBackground() {
        assertLaunches(ThemeMode.SYSTEM, dark = systemSettingIsDark())
    }

    private fun assertLaunches(mode: ThemeMode, dark: Boolean) {
        ThemeSettingsStore(context).save(ThemeSettings(mode = mode))
        val background = if (dark) TokklDarkColorScheme.background else TokklLightColorScheme.background

        launch { activity ->
            composeRule.waitUntil("the app hands $mode to Android", TIMEOUT_MS) { systemIsDark(activity) == dark }
            assertBackgrounds("first launch", activity, background)
        }
        launch { activity ->
            assertEquals("night mode of the next launch", dark, systemIsDark(activity))
            assertBackgrounds("next launch", activity, background)
        }
    }

    /** Starts the app on the Timer page and runs [check] with its activity. */
    private fun launch(check: (Activity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.awaitTimerPage()
            lateinit var activity: Activity
            scenario.onActivity { activity = it }
            check(activity)
        }
    }

    private fun assertBackgrounds(launch: String, activity: Activity, schemeBackground: Color) {
        // The Timer page leaves its left edge empty, so the pixel there is the
        // Scaffold's container colour, which is the scheme background.
        val content = composeRule.onRoot().captureToImage().toPixelMap().let { it[0, it.height / 2] }
        var window: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            window = (activity.window.decorView.background as? ColorDrawable)?.color
        }

        assertEquals("$launch: window background", hex(schemeBackground.toArgb()), window?.let(::hex))
        assertEquals("$launch: content background", hex(schemeBackground.toArgb()), hex(content.toArgb()))
    }

    /**
     * Whether the system's own setting is dark. The app's night mode does not change it, so it
     * tells whether following the system really follows it. A schedule has no fixed answer.
     */
    private fun systemSettingIsDark(): Boolean {
        val nightMode = context.getSystemService(UiModeManager::class.java).nightMode
        val fixed = nightMode == UiModeManager.MODE_NIGHT_YES || nightMode == UiModeManager.MODE_NIGHT_NO
        assumeTrue("the system's night mode follows a schedule", fixed)
        return nightMode == UiModeManager.MODE_NIGHT_YES
    }

    private fun hex(argb: Int): String = "#%08X".format(argb)

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
