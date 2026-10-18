package com.github.e0ipso.tokkl.settings

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.Rule
import org.junit.Test

/**
 * The settings screen offers wallpaper colours only from Android 12 on. The test device runs a
 * newer Android, so this hosts the screen as it would render for an older one.
 */
class DynamicColourSwitchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchShowsFromAndroid12Only() {
        var sdkInt by mutableIntStateOf(Build.VERSION_CODES.R)
        composeRule.setContent {
            TokklTheme {
                SettingsContent(
                    settings = ThemeSettings(),
                    showDynamicColor = dynamicColorAvailable(sdkInt),
                    onModeChange = {},
                    onDynamicColorChange = {},
                    onBack = {},
                )
            }
        }
        val wallpaperColours = string(R.string.settings_dynamic_colour)

        composeRule.onNodeWithText(string(R.string.settings_theme_system)).assertIsDisplayed()
        composeRule.onNodeWithText(wallpaperColours).assertDoesNotExist()

        sdkInt = Build.VERSION_CODES.S
        composeRule.onNodeWithText(wallpaperColours).assertIsDisplayed()
    }
}
