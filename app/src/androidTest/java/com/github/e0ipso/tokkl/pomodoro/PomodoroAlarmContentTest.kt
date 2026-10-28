package com.github.e0ipso.tokkl.pomodoro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
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
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
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

/** The alarm and Pomodoro page content at the largest font scale, with no server or ring. */
@SignedOut
class PomodoroAlarmContentTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(SystemFontScale.LARGEST)).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val at = Instant.parse("2026-10-24T09:00:00Z")
    private var state by mutableStateOf(PomodoroAlarmState(ring = ring(PhaseTransition.WORK_TO_SHORT_REST)))
    private var nexts = 0
    private var ends = 0

    @Test fun largestTextShowsEveryServerTransitionItsNumberAndExactlyTwoLargeLabelledActions() {
        show()
        for ((transition, label) in listOf(
            PhaseTransition.WORK_TO_SHORT_REST to R.string.pomodoro_fullscreen_work_short,
            PhaseTransition.WORK_TO_LONG_REST to R.string.pomodoro_fullscreen_work_long,
            PhaseTransition.SHORT_REST_TO_WORK to R.string.pomodoro_fullscreen_short_work,
            PhaseTransition.LONG_REST_TO_WORK to R.string.pomodoro_fullscreen_long_work,
        )) {
            compose.runOnIdle { state = state.copy(ring = ring(transition)) }
            val shown = compose.onNodeWithText(context.getString(label))
            shown.performScrollTo().assertIsDisplayed()
            readable(shown)
            val number = compose.onNodeWithText(context.getString(R.string.pomodoro_number, 3, 4))
            number.performScrollTo().assertIsDisplayed()
            readable(number)
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
        // The phone's time of the planned end is one minute after [at].
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
                PomodoroContent(page, onLengths = {}, onFields = {}, onDescription = {}, onDescriptionDone = {}, onStart = {}, onNext = {}, onEnd = {},
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

    @Test fun aFailedActionShowsTheStandardMessageAndKeepsBothActions() {
        show()
        compose.runOnIdle { state = state.copy(problem = TimerActionResult.Offline) }
        val message = compose.onNodeWithText(context.getString(R.string.timer_action_offline))
        message.performScrollTo().assertIsDisplayed()
        readable(message)
        button(R.string.pomodoro_alarm_next).performScrollTo().assertIsEnabled()
        button(R.string.pomodoro_alarm_end).performScrollTo().assertIsEnabled()
    }

    @Test fun unavailableSystemSettingsHasReadableFallbackInstructions() {
        edgeToEdge()
        compose.setContent {
            TokklTheme {
                PomodoroContent(PomodoroPageState(loading = false, fullScreenDenied = true),
                    onLengths = {}, onFields = {}, onDescription = {}, onDescriptionDone = {}, onStart = {}, onNext = {}, onEnd = {},
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

    /** A [transition] phase that started at [at], third of four, whose planned end is a minute later. */
    private fun ring(transition: PhaseTransition) = PomodoroRing(
        phase = PomodoroPhaseKey(at), kind = PhaseKind.WORK, sound = PomodoroSound.entries.single { it.transition == transition },
        number = 3, longRestEvery = 4, end = at.plusSeconds(60), revision = 7,
    )
}
