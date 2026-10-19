package com.github.e0ipso.tokkl.settings

import android.os.Build
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The settings screen in the real activity, reached from the launch screen. */
class SettingsScreenTest {
    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val themeSettings = DefaultThemeSettings()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun followsTheSystemWithTokklsPaletteByDefault() {
        composeRule.openSettings()

        mode(R.string.settings_theme_system).assertIsSelected()
        mode(R.string.settings_theme_light).assertIsNotSelected()
        mode(R.string.settings_theme_dark).assertIsNotSelected()
        if (dynamicColorAvailable()) wallpaperColours().assertIsDisplayed().assertIsOff()
        val activity = composeRule.activity
        assertEquals(tokklTheme(dark = systemIsDark(activity)), composeRule.shownTheme(activity))
    }

    /**
     * Every choice shows at once, in the same activity, without leaving the settings screen. Going
     * back to the system's mode on Android 12 and later waits for Android to hand it back.
     */
    @Test
    fun eachThemeModeAppliesAtOnce() {
        composeRule.openSettings()
        val activity = composeRule.activity
        val systemDark = systemIsDark(activity)
        // Each step changes the colours, whichever mode the system is in.
        val (same, opposite) = if (systemDark) ThemeMode.DARK to ThemeMode.LIGHT else ThemeMode.LIGHT to ThemeMode.DARK

        for (choice in listOf(opposite, same, opposite, ThemeMode.SYSTEM)) {
            mode(THEME_MODE_LABELS.getValue(choice)).performClick().assertIsSelected()
            composeRule.awaitNightModeFor(choice, activity, systemDark)

            assertEquals("$choice", tokklTheme(dark = choice.isDark(systemDark)), composeRule.shownTheme(activity))
            assertSame("the activity restarted for $choice", activity, composeRule.activity)
            settingsHeading().assertIsDisplayed()
        }
    }

    /** With the switch on, the chosen mode takes the wallpaper's colours instead of Tokkl's. */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    fun wallpaperColoursReplaceTokklsPaletteUntilTurnedOff() {
        composeRule.openSettings()
        val activity = composeRule.activity
        mode(R.string.settings_theme_light).performClick()

        wallpaperColours().performClick().assertIsOn()
        val wallpaperLight = dynamicLightColorScheme(activity).background
        assertNotEquals("the wallpaper's light background is Tokkl's", TokklLightColorScheme.background, wallpaperLight)
        assertEquals("light", expectedTheme(wallpaperLight, dark = false), composeRule.shownTheme(activity))

        mode(R.string.settings_theme_dark).performClick()
        val wallpaperDark = dynamicDarkColorScheme(activity).background
        assertEquals("dark", expectedTheme(wallpaperDark, dark = true), composeRule.shownTheme(activity))

        wallpaperColours().performClick().assertIsOff()
        assertEquals("Tokkl's dark", tokklTheme(dark = true), composeRule.shownTheme(activity))
    }

    /** TalkBack reads a name for each control, and each says what kind of control it is. */
    @Test
    fun everyControlHasATalkBackLabelAndRole() {
        composeRule.openSettings()

        val controls = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val labels = controls.map { node ->
            listOfNotNull(
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
            ).joinToString(" ")
        }
        assertTrue("a control without a label among $labels", labels.none { it.isBlank() })
        val switches = if (dynamicColorAvailable()) 1 else 0
        // Back, three theme modes, the wallpaper colours switch and Licences.
        assertEquals(labels.toString(), 1 + 3 + switches + 1, controls.size)
        assertEquals(3, composeRule.onAllNodes(hasRole(Role.RadioButton)).fetchSemanticsNodes().size)
        assertEquals(switches, composeRule.onAllNodes(hasRole(Role.Switch)).fetchSemanticsNodes().size)
    }

    /**
     * About's Licences row opens the Licences screen, and the screen's Back button and the system
     * Back key each return to Settings. TalkBack reads the row as a button named Licences, and the
     * row is at least 48dp tall, the smallest touch target Android's accessibility guidance allows.
     */
    @Test
    fun licencesRowOpensLicencesAndBackReturnsToSettings() {
        composeRule.openSettings()
        val row = licencesRow().performScrollTo()
        val config = row.fetchSemanticsNode().config
        assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role))
        assertEquals(listOf(string(R.string.licences)), config.getOrNull(SemanticsProperties.Text)?.map { it.text })
        val height = row.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("the Licences row is $height tall", height >= MIN_TOUCH_TARGET - SLACK)

        row.performClick()
        composeRule.awaitText(string(R.string.licence_anybody_title))
        composeRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        settingsHeading().assertIsDisplayed()

        licencesRow().performScrollTo().performClick()
        composeRule.awaitText(string(R.string.licence_anybody_title))
        composeRule.waitUntil("the app window has input focus", FOCUS_TIMEOUT_MS) {
            var focused = false
            composeRule.activityRule.scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settingsHeading().assertIsDisplayed()
        licencesRow().assertIsDisplayed()
    }

    /** All the text on the screen, read or shown, is one of the app's English string resources. */
    @Test
    fun everyTextComesFromStringResources() {
        composeRule.openSettings()

        val resources = R.string::class.java.fields.map { string(it.getInt(null)) }.toSet()
        val shown = composeRule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            }
        assertTrue("no text found", shown.isNotEmpty())
        assertEquals("text that is not a string resource", emptyList<String>(), shown.filterNot { it in resources })
    }

    private fun mode(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun wallpaperColours(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.settings_dynamic_colour)) and isToggleable())

    private fun settingsHeading(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.settings)) and isHeading())

    private fun licencesRow(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.licences)) and hasClickAction())

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun tokklTheme(dark: Boolean): ShownTheme =
        expectedTheme(if (dark) TokklDarkColorScheme.background else TokklLightColorScheme.background, dark)

    private companion object {
        const val FOCUS_TIMEOUT_MS = 5_000L

        val MIN_TOUCH_TARGET = 48.dp

        /** Less than a pixel, for bounds that touch after rounding. */
        val SLACK = 0.5.dp
    }
}
