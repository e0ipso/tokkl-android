package com.github.e0ipso.tokkl.pomodoro

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Dedicated page coverage, leaving the shared accessibility infrastructure unchanged. */
@SignedOut
class PomodoroPageFontScaleTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(SystemFontScale.LARGEST)).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val at = Instant.parse("2026-10-24T09:00:00Z")
    private var state by mutableStateOf(PomodoroPageState(
        loading = false,
        account = AccountSettings("europe/madrid", DayOfWeek.MONDAY, null, defaultWorkspaceId = 1),
    ))
    private var starts = 0
    private var nexts = 0
    private var ends = 0

    @Test
    fun allFourLabelledLengthInputsAndEntryControlsAreReachableWithTheKeyboard() {
        show()
        compose.onNode(hasText(context.getString(R.string.pomodoro_title)) and isHeading()).assertIsDisplayed()
        for ((label, value) in listOf(
            R.string.pomodoro_work_minutes to "180", R.string.pomodoro_short_rest_minutes to "60",
            R.string.pomodoro_long_rest_minutes to "60", R.string.pomodoro_long_rest_every to "12",
        )) {
            val field = compose.onNode(hasText(context.getString(label)) and hasSetTextAction())
            field.performScrollTo().assertIsDisplayed().assertIsEnabled().performTextReplacement(value)
            readable(compose.onNodeWithText(context.getString(label), useUnmergedTree = true))
        }
        val description = compose.onNode(hasText(context.getString(R.string.entry_description)) and hasSetTextAction())
        description.performScrollTo().performTextReplacement("Accessible entry")
        assertEquals("Accessible entry", state.fields.description)
        for (spoken in listOf(R.string.entry_no_project_spoken, R.string.entry_no_tags_spoken)) {
            compose.onNode(hasContentDescription(context.getString(spoken)) and hasClickAction())
                .performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        compose.onNode(hasText(context.getString(R.string.entry_billable)) and isToggleable())
            .performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(state.fields.billable)
        button(R.string.pomodoro_start).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assertIsEnabled().performClick()
        assertEquals(1, starts)
        assertEquals(PomodoroLengths("180", "60", "60", "12"), state.lengths)
    }

    @Test
    fun runningOvertimeHasASpokenClockAndBothActionsFitAtTheLargestFontScale() {
        state = state.copy(held = heldSession(phaseStart = at, description = "Current work"), fields = EntryFields(description = "Current work"))
        show(Clock.fixed(at.plusSeconds(25 * 60 + 65), ZoneOffset.UTC))
        compose.onNodeWithText(context.getString(R.string.pomodoro_overtime)).performScrollTo().assertIsDisplayed()
        val duration = state.account!!.formatDuration(65)
        compose.onNode(hasContentDescription(context.getString(R.string.pomodoro_overtime_clock, duration)))
            .performScrollTo().assertIsDisplayed()
        readable(compose.onNodeWithText(duration, useUnmergedTree = true))
        for (label in listOf(R.string.pomodoro_start_short_rest, R.string.pomodoro_end)) {
            val action = button(label)
            action.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            readable(compose.onNodeWithText(context.getString(label), useUnmergedTree = true))
        }
        assertEquals(1, nexts)
        assertEquals(1, ends)
    }

    @Test
    fun alarmDenialAndTheNoConnectionMessageAreReadableAndDoNotHideStart() {
        state = state.copy(
            alarmDenied = true, problem = PomodoroProblem(PomodoroAction.END, TimerActionResult.Offline), unsentCount = 2,
        )
        show()
        for (text in listOf(
            context.getString(R.string.pomodoro_alarm_denied),
            context.getString(TimerActionResult.Offline.message),
            context.resources.getQuantityString(R.plurals.pomodoro_unsent, 2, 2),
        )) {
            val notice = compose.onNodeWithText(text)
            notice.performScrollTo().assertIsDisplayed()
            readable(notice)
        }
        button(R.string.pomodoro_start).performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    private fun show(clock: Clock = Clock.fixed(at, ZoneOffset.UTC)) {
        compose.activityRule.scenario.onActivity { activity ->
            WindowCompat.enableEdgeToEdge(activity.window)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            TokklTheme {
                PomodoroContent(
                    state,
                    onLengths = { state = state.copy(lengths = it) },
                    onFields = { state = state.copy(fields = it) },
                    onDescription = { state = state.copy(fields = it) },
                    onDescriptionDone = {},
                    onStart = { starts++ }, onNext = { nexts++ }, onEnd = { ends++ }, clock = clock,
                )
            }
        }
    }

    private fun button(label: Int) = compose.onNode(hasText(context.getString(label)) and hasClickAction())

    private fun readable(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("text had no layout", layouts.isNotEmpty())
        layouts.forEach { assertFalse("text was clipped vertically", it.didOverflowHeight) }
    }
}
