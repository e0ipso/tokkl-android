package com.github.e0ipso.tokkl.pomodoro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Device/UI acceptance source; no screenshot or device execution in this sitting. */
@SignedOut
class PomodoroAlarmContentTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(SystemFontScale.LARGEST)).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val at = Instant.parse("2026-10-24T09:00:00Z")
    private var state by mutableStateOf(PomodoroAlarmState(session = session()))
    private var nexts = 0
    private var ends = 0

    @Test fun largestTextShowsEveryTransitionAndExactlyTwoLargeLabelledActions() {
        show()
        for ((kind, completed, label) in listOf(
            Triple("work", 0.0, R.string.pomodoro_fullscreen_work_short),
            Triple("work", 3.0, R.string.pomodoro_fullscreen_work_long),
            Triple("shortRest", 1.0, R.string.pomodoro_fullscreen_short_work),
            Triple("longRest", 4.0, R.string.pomodoro_fullscreen_long_work),
        )) {
            compose.runOnIdle { state = state.copy(session = session().copy(phaseKind = kind, completed = completed)) }
            val transition = compose.onNodeWithText(context.getString(label))
            transition.performScrollTo().assertIsDisplayed()
            readable(transition)
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            for (action in listOf(R.string.pomodoro_alarm_next, R.string.pomodoro_alarm_end)) {
                button(action).performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(64.dp)
                readable(compose.onNodeWithText(context.getString(action), useUnmergedTree = true))
            }
        }
        button(R.string.pomodoro_alarm_next).performScrollTo().performClick()
        button(R.string.pomodoro_alarm_end).performScrollTo().performClick()
        assertEquals(1, nexts)
        assertEquals(1, ends)
    }

    @Test fun clockTicksLocallyAcrossTheDeadlineAndExposesOvertimeOnDemand() {
        var now = at.plusSeconds(59)
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
        }
        compose.mainClock.autoAdvance = false
        show(clock)
        compose.mainClock.advanceTimeBy(32)
        compose.onNode(hasContentDescription(context.getString(R.string.pomodoro_remaining_clock, "0:01")))
            .performScrollTo().assertIsDisplayed()
        now = at.plusSeconds(125)
        compose.mainClock.advanceTimeBy(1100)
        compose.onNode(hasContentDescription(context.getString(R.string.pomodoro_overtime_clock, "1:05")))
            .performScrollTo().assertIsDisplayed()
        assertEquals(0, nexts)
        assertEquals(0, ends)
    }

    @Test fun bothPermissionDenialsCoexistAndGrantRemovesOnlyTheFullscreenNotice() {
        var page by mutableStateOf(PomodoroPageState(loading = false, alarmDenied = true, fullScreenDenied = true))
        var settings = 0
        edgeToEdge()
        compose.setContent {
            TokklTheme {
                PomodoroContent(page, onLengths = {}, onFields = {}, onStart = {}, onNext = {}, onEnd = {}, onReload = {},
                    onFullScreenSettings = { settings++ })
            }
        }
        for (label in listOf(R.string.pomodoro_alarm_denied, R.string.pomodoro_fullscreen_denied)) {
            val notice = compose.onNodeWithText(context.getString(label))
            notice.performScrollTo().assertIsDisplayed()
            readable(notice)
        }
        button(R.string.pomodoro_fullscreen_settings).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, settings)
        compose.runOnIdle { page = page.copy(fullScreenDenied = false) }
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_denied)).assertDoesNotExist()
        button(R.string.pomodoro_fullscreen_settings).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.pomodoro_alarm_denied)).performScrollTo().assertIsDisplayed()
    }

    @Test fun unavailableSystemSettingsHasReadableFallbackInstructions() {
        edgeToEdge()
        compose.setContent {
            TokklTheme {
                PomodoroContent(PomodoroPageState(loading = false, fullScreenDenied = true),
                    onLengths = {}, onFields = {}, onStart = {}, onNext = {}, onEnd = {}, onReload = {},
                    fullScreenSettingsUnavailable = true)
            }
        }
        val fallback = compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_settings_unavailable))
        fallback.performScrollTo().assertIsDisplayed()
        readable(fallback)
    }

    private fun show(clock: Clock = Clock.fixed(at.plusSeconds(125), ZoneOffset.UTC)) {
        edgeToEdge()
        compose.setContent { TokklTheme { PomodoroAlarmContent(state, { nexts++ }, { ends++ }, clock) } }
    }

    private fun edgeToEdge() {
        compose.activityRule.scenario.onActivity { WindowCompat.enableEdgeToEdge(it.window) }
    }

    private fun button(label: Int) = compose.onNode(hasText(context.getString(label)) and hasClickAction())

    private fun readable(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("text had no layout", layouts.isNotEmpty())
        layouts.forEach { assertFalse("text clipped vertically", it.didOverflowHeight) }
    }

    private fun session() = PomodoroSession(
        workspaceId = 1, workMinutes = 1.0, shortRestMinutes = 1.0, longRestMinutes = 1.0, longRestEvery = 4.0,
        phaseKind = "work", phaseStartedAt = at, pomodoroStartedAt = at, completed = 0.0, fields = EntryFields(),
    )
}
