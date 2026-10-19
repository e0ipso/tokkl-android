package com.github.e0ipso.tokkl.settings

import android.app.Activity
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.awaitText
import java.io.File
import org.junit.rules.ExternalResource

private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

/**
 * Puts the theme settings back to their defaults before and after each test, and hands the
 * night mode back to the system, which the app gives Android on Android 12 and later and Android
 * keeps until it is changed. Order it before the rule that starts the activity, which reads the
 * settings once per process.
 */
class DefaultThemeSettings : ExternalResource() {
    override fun before() = reset()

    override fun after() = reset()

    private fun reset() {
        ThemeSettingsStore(targetContext).clear()
        followTheSystemNightMode()
    }
}

/** Hands the app's night mode back to the system, on Android 12 and later where it exists. */
fun followTheSystemNightMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        targetContext.getSystemService(UiModeManager::class.java).setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
    }
}

fun string(id: Int): String = targetContext.getString(id)

/** The label of each theme mode on the settings screen. */
val THEME_MODE_LABELS = mapOf(
    ThemeMode.SYSTEM to R.string.settings_theme_system,
    ThemeMode.LIGHT to R.string.settings_theme_light,
    ThemeMode.DARK to R.string.settings_theme_dark,
)

/** Opens the settings screen from the bottom bar on the Timer page. */
fun ComposeTestRule.openSettings() {
    awaitText(string(R.string.settings))
    onNodeWithText(string(R.string.settings)).performClick()
    awaitText(string(R.string.settings_theme))
}

/**
 * What the screen shows: the colour of the content at the left edge, half way down, where every
 * screen shows only its Scaffold's background; the window's background; and whether the system
 * bars draw dark icons for a light background.
 */
data class ShownTheme(val content: String, val window: String?, val lightStatusBar: Boolean)

fun ComposeTestRule.shownTheme(activity: Activity): ShownTheme {
    val pixels = onRoot().captureToImage().toPixelMap()
    val content = pixels[0, pixels.height / 2].toArgb()
    var window: Int? = null
    var lightStatusBar = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        window = (activity.window.decorView.background as? ColorDrawable)?.color
        lightStatusBar = WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars
    }
    return ShownTheme(hex(content), window?.let(::hex), lightStatusBar)
}

/** The theme a scheme [background] should produce, as [shownTheme] reports it. */
fun expectedTheme(background: Color, dark: Boolean): ShownTheme =
    hex(background.toArgb()).let { ShownTheme(content = it, window = it, lightStatusBar = !dark) }

/**
 * Whether [activity] runs in night mode. While the app follows the system, as every test starts,
 * that is the system's mode. On Android 12 and later a light or dark setting replaces it.
 */
fun systemIsDark(activity: Activity): Boolean =
    (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * Waits until [activity] runs in the night mode Android gives the app for [mode]. On Android 12
 * and later that is the chosen mode, and the configuration change arrives a moment after the
 * choice. Below that it is the system's.
 */
fun ComposeTestRule.awaitNightModeFor(mode: ThemeMode, activity: Activity, systemDark: Boolean) {
    val dark = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mode.isDark(systemDark) else systemDark
    waitUntil("the activity runs in the night mode for $mode", 5_000) { systemIsDark(activity) == dark }
}

/**
 * Waits until the preferences file on disk holds [mode], so a test that ends now leaves the
 * choice for the next process to read.
 */
fun awaitModeOnDisk(mode: ThemeMode, timeoutMillis: Long = 5_000) {
    val file = File(targetContext.dataDir, "shared_prefs/${ThemeSettingsStore.FILE_NAME}.xml")
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (!(file.exists() && file.readText().contains(">${mode.name}<"))) {
        check(System.nanoTime() < deadline) { "$file does not hold $mode" }
        Thread.sleep(50)
    }
}

private fun hex(argb: Int): String = "#%08X".format(argb)
