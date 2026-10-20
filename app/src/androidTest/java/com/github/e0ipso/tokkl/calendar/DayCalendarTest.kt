package com.github.e0ipso.tokkl.calendar

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The calendar's two layouts (docs/PRD.md K1). On this upright phone, a compact width, it shows
 * the chosen day under a strip of the seven days of its week, each with its total. A tap on a day
 * of the strip, the arrows and a swipe change the day, into the next or the last week too. A
 * window of medium width, an unfolded foldable or a tablet, shows the week with its day heads.
 * LandscapeCalendarTest holds the phone sideways, where the week shows too.
 */
class DayCalendarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun anUprightPhoneShowsTheChosenDayUnderTheStripOfItsWeek() {
        show(THURSDAY)

        composeRule.onNode(blockOf(STAND_UP)).assertExists()
        composeRule.onNode(blockOf(MURAL)).assertDoesNotExist()
        composeRule.onAllNodes(stripDay).assertCountEquals(7)
        stripDay(spoken(string(R.string.history_today), 30)).assertIsSelected()
        stripDay(spoken("Mon, 12 Oct", 210)).assertIsNotSelected()
        stripDay("Tue, 13 Oct").assertIsNotSelected()
        stripDay(spoken("Sun, 18 Oct", 15)).assertIsNotSelected()
        composeRule.onNode(isHeading() and hasContentDescription("Mon, 12 Oct", substring = true)).assertDoesNotExist()
        composeRule.onNode(arrow(R.string.calendar_previous_day)).assertExists()
        composeRule.onNode(arrow(R.string.calendar_next_day)).assertExists()
    }

    @Test
    fun aTapOnADayOfTheStripShowsIt() {
        val shown = show(THURSDAY)

        stripDay("Mon, 12 Oct").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(MONDAY, shown.selected)
        composeRule.onNode(blockOf(MURAL)).assertExists()
        composeRule.onNode(blockOf(STAND_UP)).assertDoesNotExist()
        stripDay("Mon, 12 Oct").assertIsSelected()
        stripDay(string(R.string.history_today)).assertIsNotSelected()
    }

    @Test
    fun theArrowsAndSwipesCrossIntoTheWeekBeforeAndTheWeekAfter() {
        val shown = show(MONDAY)

        composeRule.onNode(arrow(R.string.calendar_previous_day)).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(MONDAY.minusDays(1), shown.selected)
        composeRule.onNode(blockOf(SUNDAY_REVIEW)).assertExists()
        stripDay(spoken("Sun, 11 Oct", 60)).assertIsSelected()
        stripDay("Mon, 5 Oct").assertIsNotSelected()

        composeRule.onNode(arrow(R.string.calendar_next_day)).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(MONDAY, shown.selected)
        stripDay("Mon, 12 Oct").assertIsSelected()

        stripDay("Sun, 18 Oct").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(blockOf(WEEKEND_NOTES)).performTouchInput { swipeLeft() }
        assertEquals(NEXT_MONDAY, shown.selected)
        composeRule.onNode(blockOf(NEXT_WEEK_PLANNING)).assertExists()
        stripDay(spoken("Mon, 19 Oct", 60)).assertIsSelected()
        stripDay("Sun, 25 Oct").assertIsNotSelected()

        composeRule.onNode(blockOf(NEXT_WEEK_PLANNING)).performTouchInput { swipeRight() }
        assertEquals(NEXT_MONDAY.minusDays(1), shown.selected)
        composeRule.onNode(blockOf(WEEKEND_NOTES)).assertExists()
    }

    @Test
    fun aWindowOfMediumWidthShowsTheWeekWithItsDayHeads() {
        val shown = show(THURSDAY)
        for (window in listOf(UNFOLDED_FOLDABLE, TABLET)) {
            shown.window = window
            composeRule.waitForIdle()

            composeRule.onAllNodes(stripDay).assertCountEquals(0)
            composeRule.onNode(isHeading() and hasContentDescription(spoken("Mon, 12 Oct", 210))).assertExists()
            composeRule.onNode(isHeading() and hasContentDescription(spoken(string(R.string.history_today), 30))).assertExists()
            composeRule.onNode(isHeading() and hasContentDescription(spoken("Sun, 18 Oct", 15))).assertExists()
            composeRule.onNode(blockOf(MURAL)).assertExists()
            composeRule.onNode(blockOf(STAND_UP)).assertExists()
            composeRule.onNode(arrow(R.string.calendar_previous_week)).assertExists()
            composeRule.onNode(arrow(R.string.calendar_next_week)).assertExists()
        }
    }

    /** The chosen day sits in Tokkl's frame, which the other days lack, so colour is not its only sign. */
    @Test
    fun theChosenDayIsFramed() {
        show(THURSDAY)

        assertTrue("the chosen day has a frame", framed(stripDay(string(R.string.history_today))))
        assertTrue("another day has none", !framed(stripDay("Mon, 12 Oct")))
    }

    /** The next day slides in from the end as it fades in, and settles in place. */
    @Test
    fun theNextDaySlidesIn() = withAnimatorDurationScale(1f) {
        show(WEDNESDAY)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNode(arrow(R.string.calendar_next_day)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()
        val moving = composeRule.onNode(blockOf(STAND_UP)).getBoundsInRoot().left
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        val settled = composeRule.onNode(blockOf(STAND_UP)).getBoundsInRoot().left

        assertTrue("the day starts on its way in: $moving, in place at $settled", moving - settled > 15.dp)
    }

    /** With Remove animations on, the next day shows in place from its first frame. */
    @Test
    fun reducedMotionChangesTheDayAtOnce() = withAnimatorDurationScale(0f) {
        show(WEDNESDAY)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNode(arrow(R.string.calendar_next_day)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()
        val first = composeRule.onNode(blockOf(STAND_UP)).getBoundsInRoot().left
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        val settled = composeRule.onNode(blockOf(STAND_UP)).getBoundsInRoot().left

        assertTrue("the day shows in place at once: $first, in place at $settled", abs((first - settled).value) < 1f)
    }

    /** The calendar from [start], in this phone's own window until the test gives it another. */
    private fun show(start: LocalDate): Shown {
        val shown = Shown(start)
        composeRule.setContent {
            TokklTheme(darkTheme = false) {
                WindowOfSize(shown.window) {
                    CalendarContent(
                        state = state(shown.selected),
                        now = NOON,
                        onStep = { shown.selected = shown.selected.plusDays(it) },
                        onSelect = { shown.selected = it },
                        onToday = { shown.selected = THURSDAY },
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return shown
    }

    /** Whether [day] draws Tokkl's frame along its start side. */
    private fun framed(day: SemanticsNodeInteraction): Boolean {
        val pixels = day.captureToImage().toPixelMap()
        val edge = pixels[FRAME_PIXEL, pixels.height / 2]
        return close(edge, TokklLightColorScheme.outline)
    }

    private fun close(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < COLOUR_TOLERANCE && abs(a.green - b.green) < COLOUR_TOLERANCE && abs(a.blue - b.blue) < COLOUR_TOLERANCE

    private fun stripDay(spoken: String): SemanticsNodeInteraction =
        composeRule.onNode(stripDay and hasContentDescription(spoken, substring = true))

    private fun arrow(@StringRes label: Int): SemanticsMatcher = hasContentDescription(string(label)) and hasClickAction()

    /** What TalkBack reads for the day [label] with [minutes] in total. */
    private fun spoken(label: String, minutes: Long): String =
        context.getString(R.string.history_day_spoken, label, spokenDuration(context.resources, minutes * 60))

    /** Runs [steps] with the system's animator duration scale at [scale], and puts it back after. */
    private fun withAnimatorDurationScale(scale: Float, steps: () -> Unit) {
        val previous = DeviceShell.run("settings get global animator_duration_scale").trim()
        DeviceShell.run("settings put global animator_duration_scale $scale")
        try {
            steps()
        } finally {
            if (previous == NOT_SET) {
                DeviceShell.run("settings delete global animator_duration_scale")
            } else {
                DeviceShell.run("settings put global animator_duration_scale $previous")
            }
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    /** The chosen day, which the calendar's callbacks move, and the window, null for the phone's own. */
    private class Shown(start: LocalDate) {
        var selected: LocalDate by mutableStateOf(start)
        var window: DpSize? by mutableStateOf(null)
    }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 10, 12)
        val WEDNESDAY: LocalDate = LocalDate.of(2026, 10, 14)
        val THURSDAY: LocalDate = LocalDate.of(2026, 10, 15)
        val NEXT_MONDAY: LocalDate = LocalDate.of(2026, 10, 19)
        val NOON: Instant = Instant.parse("2026-10-15T12:00:00Z")
        val SETTINGS = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)

        const val MURAL = "Paint the mural"
        const val STAND_UP = "Stand-up"
        const val SUNDAY_REVIEW = "Sunday review"
        const val WEEKEND_NOTES = "Weekend notes"
        const val NEXT_WEEK_PLANNING = "Next week's planning"

        /** Monday 12 October to Sunday 18 October, with an entry on the Sunday before and the Monday after. */
        val ENTRIES = listOf(
            entry(1, SUNDAY_REVIEW, "2026-10-11T10:00:00Z", "2026-10-11T11:00:00Z"),
            entry(2, MURAL, "2026-10-12T09:00:00Z", "2026-10-12T12:30:00Z"),
            entry(3, STAND_UP, "2026-10-15T08:00:00Z", "2026-10-15T08:30:00Z"),
            entry(4, WEEKEND_NOTES, "2026-10-18T10:00:00Z", "2026-10-18T10:15:00Z"),
            entry(5, NEXT_WEEK_PLANNING, "2026-10-19T09:00:00Z", "2026-10-19T10:00:00Z"),
        )

        /** A Pixel Fold's inner display, unfolded and upright, and a tablet held sideways. */
        val UNFOLDED_FOLDABLE = DpSize(673.dp, 841.dp)
        val TABLET = DpSize(1280.dp, 800.dp)

        /** A day of the week's strip, which TalkBack reads as a tab. */
        val stripDay: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

        /** Long enough for any slide to end. */
        const val SETTLE_MS = 1_000L

        /** A pixel inside the 2dp frame, which is about five pixels wide at 420 dpi. */
        const val FRAME_PIXEL = 2
        const val COLOUR_TOLERANCE = 0.06f

        /** What `settings get` prints for a setting that was never written. */
        const val NOT_SET = "null"

        fun state(selected: LocalDate) = CalendarState.Shown(
            selected = selected,
            weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            today = THURSDAY,
            zone = ZoneOffset.UTC,
            settings = SETTINGS,
            entries = ENTRIES,
            week = WeekSource.Cached,
        )

        fun entry(id: Long, description: String, start: String, stop: String) = CalendarEntry(
            id = id,
            description = description,
            project = null,
            projectColor = null,
            task = null,
            start = Instant.parse(start),
            stop = Instant.parse(stop),
            unsent = false,
        )

        /** The block of the entry described [description], which TalkBack reads first. */
        fun blockOf(description: String): SemanticsMatcher = hasContentDescription("$description, ", substring = true)
    }
}

/** [content] in a window of [size], as a device of that size lays it out, or in the real window. */
@Composable
private fun WindowOfSize(size: DpSize?, content: @Composable () -> Unit) {
    if (size == null) {
        content()
        return
    }
    val real = LocalWindowInfo.current
    val pixels = with(LocalDensity.current) { IntSize(size.width.roundToPx(), size.height.roundToPx()) }
    val window = object : WindowInfo by real {
        override val containerSize: IntSize = pixels
        override val containerDpSize: DpSize = size
    }
    CompositionLocalProvider(LocalWindowInfo provides window, content = content)
}
