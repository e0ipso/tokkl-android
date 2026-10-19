package com.github.e0ipso.tokkl.licences

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.settings.openSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Licences screen, reached from Settings, About, in the real activity. */
class LicencesScreenTest {
    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aboutEntryOpensTheFullOflText() {
        openLicences()

        composeRule.onNodeWithText(string(R.string.licence_anybody_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.licence_anybody_licence)).assertIsDisplayed()
        val shown = composeRule.onNode(hasText(OFL_TITLE, substring = true))
            .assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString("") { it.text }
        val shipped = composeRule.activity.resources.openRawResource(R.raw.anybody_ofl)
            .bufferedReader().use { it.readText() }
        // lines() splits at CRLF and LF alike, so this compares content, not line ends.
        assertEquals(shipped.lines(), shown.lines())
    }

    @Test
    fun backButtonReturnsToSettings() {
        openLicences()

        composeRule.onNodeWithContentDescription(string(R.string.back)).performClick()

        settingsHeading().assertIsDisplayed()
    }

    /** System back goes to Settings, and from there to the Timer page, instead of closing the app. */
    @Test
    fun systemBackReturnsToSettingsThenTimer() {
        openLicences()

        composeRule.waitUntil("the app window has input focus", FOCUS_TIMEOUT_MS) {
            var focused = false
            composeRule.activityRule.scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settingsHeading().assertIsDisplayed()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.awaitTimerPage()
    }

    /** A rotation, theme or font change recreates the activity. */
    @Test
    fun licencesStayOpenWhenTheActivityIsRecreated() {
        openLicences()

        composeRule.activityRule.scenario.recreate()

        licencesHeading().assertIsDisplayed()
    }

    private fun openLicences() {
        composeRule.openSettings()
        composeRule.onNode(hasText(string(R.string.licences)) and hasClickAction()).performScrollTo().performClick()
        licencesHeading().assertIsDisplayed()
    }

    private fun licencesHeading() = composeRule.onNode(hasText(string(R.string.licences)) and isHeading())

    private fun settingsHeading() = composeRule.onNode(hasText(string(R.string.settings)) and isHeading())

    private fun string(@StringRes id: Int): String = composeRule.activity.getString(id)

    private companion object {
        const val OFL_TITLE = "SIL OPEN FONT LICENSE Version 1.1"
        const val FOCUS_TIMEOUT_MS = 10_000L
    }
}
