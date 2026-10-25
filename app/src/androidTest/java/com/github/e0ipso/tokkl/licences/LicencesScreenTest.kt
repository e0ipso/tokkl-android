package com.github.e0ipso.tokkl.licences

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createAndroidComposeRule
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.settings.openSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Licences screen, reached from Settings, About, in the real activity. */
class LicencesScreenTest {
    @org.junit.After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }

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
    fun aboutEntryReachesTokklSoundCreditWithTheFullMitNotice() {
        openLicences()

        composeRule.onNode(
            hasText(string(R.string.licence_pomodoro_sounds_title)) and isHeading(),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.licence_pomodoro_sounds_licence))
            .performScrollTo().assertIsDisplayed()
        val notice = composeRule.onNode(hasText("Copyright (c) 2026 Tokkl contributors", substring = true))
            .performScrollTo().assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString("") { it.text }
        val shipped = composeRule.activity.resources.openRawResource(R.raw.pomodoro_sounds_mit)
            .bufferedReader().use { it.readText() }
        assertEquals(shipped, notice)
    }

    @Test
    fun backButtonReturnsToSettings() {
        openLicences()

        composeRule.onNodeWithContentDescription(string(R.string.back)).performClick()

        settingsHeading().assertIsDisplayed()
    }

    /** The top bar, with the title and the back button, stays put while the licence text scrolls under it. */
    @Test
    fun theBarStaysWhileTheTextScrolls() {
        openLicences()
        val text = composeRule.onNode(hasText(OFL_TITLE, substring = true))
        val textBefore = text.getUnclippedBoundsInRoot().top
        val headingBefore = licencesHeading().getBoundsInRoot()
        val backBefore = composeRule.onNodeWithContentDescription(string(R.string.back)).getBoundsInRoot()

        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        val textAfter = text.getUnclippedBoundsInRoot().top
        assertTrue("the text scrolls: its top moved from $textBefore to $textAfter", textAfter < textBefore - MIN_SCROLL)
        assertEquals(headingBefore, licencesHeading().assertIsDisplayed().getBoundsInRoot())
        assertEquals(backBefore, composeRule.onNodeWithContentDescription(string(R.string.back)).assertIsDisplayed().getBoundsInRoot())
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

        /** Far less than one swipe scrolls the text. */
        val MIN_SCROLL = 100.dp
    }
}
