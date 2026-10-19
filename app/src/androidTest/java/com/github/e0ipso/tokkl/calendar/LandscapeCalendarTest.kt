package com.github.e0ipso.tokkl.calendar

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedInFrame
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timer.TimerBarContent
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerState
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Timer page's Calendar view on a phone held sideways, a window of compact height (docs/PRD.md
 * K1), or upright at the largest font scale, where the height is compact for the text it holds.
 * Once the calendar scrolls on, the page title, the timer, the view switch and the toolbar have
 * scrolled away, and the grid shows at least [hourRows] whole hour rows under the day heads,
 * which stay in the window. The view switch and every toolbar control scroll back into the window
 * whole, as TalkBack scrolls to them.
 */
abstract class LandscapeCalendarTest(
    private val fontScale: Float,
    private val hourRows: Int,
    private val landscape: Boolean = true,
) {
    private val composeRule = createEmptyComposeRule()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(fontScale))
        .around(composeRule)

    /** A phone held sideways shows the week, and one held upright the chosen day, today. */
    private val thisWeek = if (landscape) R.string.calendar_this_week else R.string.calendar_today
    private val arrowLabels = if (landscape) {
        listOf(R.string.calendar_previous_week, R.string.calendar_next_week)
    } else {
        listOf(R.string.calendar_previous_day, R.string.calendar_next_day)
    }

    /** How TalkBack reads a day head: Monday's, a day that is not today, or today's alone. */
    private val headLabel get() = if (landscape) MONDAY else string(R.string.history_today)

    @SignedOut
    @Test
    fun theGridGetsTheRoom() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val (requested, orientation) = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT
            }
            scenario.onActivity { it.requestedOrientation = requested }
            composeRule.waitUntil("the activity runs in the orientation", TIMEOUT_MS) {
                var turned = false
                scenario.onActivity { turned = it.resources.configuration.orientation == orientation }
                turned
            }
            scenario.onActivity { activity ->
                WindowCompat.enableEdgeToEdge(activity.window)
                activity.setContent { TokklTheme(darkTheme = false) { CalendarPage() } }
            }
            composeRule.awaitText(string(thisWeek))
            val window = composeRule.onRoot().getBoundsInRoot()
            assertTrue(
                "the window has a compact height for its text: ${window.height()} at font scale $fontScale",
                TokklSpacing.isCompactHeight(window.height() / fontScale),
            )

            val grid = composeRule.onNode(
                hasScrollAction() and hasAnyDescendant(hasContentDescription(STAND_UP, substring = true)) and
                    !hasAnyDescendant(isHeading()),
            )
            val before = grid.getBoundsInRoot()
            // The page's column, which holds the view switch, ends right above the bottom bar.
            val page = composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText(string(R.string.timer_view_list)) and isSelectable()))
            val pageBottom = page.fetchSemanticsNode().boundsInRoot.bottom
            // From the bottom of the calendar up the window, as a thumb scrolls on through the day.
            composeRule.onRoot().performTouchInput {
                swipe(Offset(centerX, pageBottom - 1f), Offset(centerX, 1f), SWIPE_MS)
            }
            composeRule.waitForIdle()

            val shown = grid.getBoundsInRoot()
            assertTrue(
                "the grid shows $hourRows whole hour rows: ${shown.height()} tall after the scroll, ${before.height()} before",
                shown.height() >= HOUR_ROW * hourRows,
            )
            val head = composeRule.onNode(isHeading() and hasContentDescription(headLabel, substring = true))
            assertTrue("the day heads show whole", shownWhole(head))
            assertTrue("the day heads stay above the grid", head.getBoundsInRoot().bottom <= shown.top)

            for ((name, control) in controls()) {
                control.performScrollTo()
                composeRule.waitForIdle()
                assertTrue("$name scrolls into the window whole: ${control.getBoundsInRoot()}", shownWhole(control))
            }
        }
    }

    /** The view switch's segments and the calendar toolbar's buttons. */
    private fun controls(): List<Pair<String, SemanticsNodeInteraction>> {
        val segments = listOf(R.string.timer_view_list, R.string.timer_view_calendar).map { label ->
            string(label) to composeRule.onNode(hasText(string(label)) and isSelectable())
        }
        val arrows = arrowLabels.map { label ->
            string(label) to composeRule.onNode(hasContentDescription(string(label)) and hasClickAction())
        }
        val current = string(thisWeek).let { it to composeRule.onNode(hasText(it) and hasClickAction()) }
        return segments + arrows + current
    }

    /** Whether nothing clips [node]: its bounds in the window are its whole size. */
    private fun shownWhole(node: SemanticsNodeInteraction): Boolean {
        val semantics = node.fetchSemanticsNode()
        val shown = semantics.boundsInRoot
        return abs(shown.width - semantics.size.width) <= 1f && abs(shown.height - semantics.size.height) <= 1f
    }

    private fun DpRect.height(): Dp = bottom - top

    private fun string(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SWIPE_MS = 400L

        /** An hour row of the grid, as tall as WeekCalendar's HourHeight. */
        val HOUR_ROW: Dp = TokklSpacing.space8

        const val STAND_UP = "Stand-up"

        /** How TalkBack reads the head of Monday, a day that is not today. */
        const val MONDAY = "Mon, 12 Oct"
    }
}

/** At the usual font scale, the grid shows four hour rows. */
class UsualFontLandscapeCalendarTest : LandscapeCalendarTest(fontScale = 1f, hourRows = 4)

/** At the largest font scale, the grid still shows a whole hour row. */
class LargestFontLandscapeCalendarTest : LandscapeCalendarTest(fontScale = SystemFontScale.LARGEST, hourRows = 1)

/**
 * On a phone held upright at the largest font scale, text takes the room it takes on a phone held
 * sideways, so the title, the timer, the switch and the toolbar scroll away there too, and the grid
 * shows eight hour rows.
 */
class LargestFontUprightCalendarTest : LandscapeCalendarTest(fontScale = SystemFontScale.LARGEST, hourRows = 8, landscape = false)

/**
 * The Timer page in the Calendar view, with the idle timer, above the bottom bar, at noon on the
 * Thursday of [WEEK].
 */
@Composable
private fun CalendarPage() {
    SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}) {
        TimerScreen(
            view = TimerView.CALENDAR,
            onViewChange = {},
            timer = {
                TimerBarContent(
                    state = TimerState.Idle(EntryFields(), EntryLabels(), workspaceId = 1),
                    elapsedSeconds = 0,
                    onDescriptionChange = {},
                    onDescriptionDone = {},
                    onPick = {},
                    onBillableChange = {},
                    onStart = {},
                    onStop = {},
                    onAddEntry = {},
                    picker = { _, _, _, _, _ -> },
                )
            },
            listContent = {},
            calendarContent = { CalendarContent(state = WEEK, now = NOON, onStep = {}, onToday = {}, onRetry = {}) },
        )
    }
}

private val NOON: Instant = Instant.parse("2026-10-15T12:00:00Z")

/** The week of 12 October 2026 with Thursday chosen, and an entry on Monday and on Thursday. */
private val WEEK = CalendarState.Shown(
    selected = LocalDate.of(2026, 10, 15),
    weekStart = LocalDate.of(2026, 10, 12),
    today = LocalDate.of(2026, 10, 15),
    zone = ZoneOffset.UTC,
    settings = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null),
    entries = listOf(
        entry(1, "Paint the mural", "2026-10-12T09:00:00Z", "2026-10-12T12:30:00Z"),
        entry(2, "Stand-up", "2026-10-15T08:00:00Z", "2026-10-15T08:30:00Z"),
    ),
    week = WeekSource.Cached,
)

private fun entry(id: Long, description: String, start: String, stop: String) = CalendarEntry(
    id = id,
    description = description,
    project = "Tokkl for Android",
    projectColor = "#0b83d9",
    task = null,
    start = Instant.parse(start),
    stop = Instant.parse(stop),
    unsent = false,
)
