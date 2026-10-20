package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.settings.SettingsContent
import com.github.e0ipso.tokkl.settings.SignOutModel
import com.github.e0ipso.tokkl.settings.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A screen's single column keeps the screen margin and stops at 600dp, centred, whatever the
 * window's width (docs/PRD.md, Visual direction, Spacing and type scale). Each test draws a
 * screen at a forced window size and measures, in that window's dp, something that spans the
 * column: a theme choice row on Settings, the licence text on Licences.
 */
class ScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var density: Density

    @Test
    fun aPhoneKeepsTheCompactMargin() {
        showSettings(width = 400)

        assertSpan(themeChoice(), left = 16f, right = 384f)
    }

    @Test
    fun aMediumWindowKeepsTheWiderMargin() {
        showSettings(width = 640)

        assertSpan(themeChoice(), left = 24f, right = 616f)
    }

    @Test
    fun aWideWindowCapsSettingsAt600dpAndCentresThem() {
        showSettings(width = 1000)

        assertSpan(themeChoice(), left = 200f, right = 800f)
    }

    @Test
    fun aWideWindowCentresTheLicences() {
        show(width = 1000) { LicencesScreen(onBack = {}) }

        assertSpan(composeRule.onNode(hasText(OFL_TITLE, substring = true)), left = 200f, right = 800f)
    }

    private fun showSettings(width: Int) = show(width) {
        SettingsContent(
            settings = ThemeSettings(),
            onModeChange = {},
            onDynamicColorChange = {},
            signOut = SignOutModel.State(),
            onSignOut = {},
            onConfirmSignOut = {},
            onCancelSignOut = {},
            onOpenLicences = {},
        )
    }

    private fun show(width: Int, content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, HEIGHT))) {
                density = LocalDensity.current
                Box(Modifier.testTag(WINDOW)) { TokklTheme(content = content) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun themeChoice(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.settings_theme_light)) and isSelectable())

    private fun assertSpan(node: SemanticsNodeInteraction, left: Float, right: Float) {
        val bounds = node.inWindow()
        assertEquals("left edge in dp", left, bounds.left, TOLERANCE_DP)
        assertEquals("right edge in dp", right, bounds.right, TOLERANCE_DP)
    }

    /** [this] node's bounds in the forced window, in that window's dp. */
    private fun SemanticsNodeInteraction.inWindow(): Rect {
        val window = composeRule.onNodeWithTag(WINDOW).fetchSemanticsNode().boundsInRoot
        val node = fetchSemanticsNode().boundsInRoot
        return Rect(
            left = (node.left - window.left) / density.density,
            top = (node.top - window.top) / density.density,
            right = (node.right - window.left) / density.density,
            bottom = (node.bottom - window.top) / density.density,
        )
    }

    private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        const val WINDOW = "window"
        const val OFL_TITLE = "SIL OPEN FONT LICENSE Version 1.1"
        val HEIGHT = 900.dp

        /** Pixel rounding at the forced density. */
        const val TOLERANCE_DP = 1f
    }
}
