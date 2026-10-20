package com.github.e0ipso.tokkl.calendar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The strip of the week's days above the one-day calendar on an upright phone, at Android's
 * largest font scale, 200%, and at the width the Timer page gives the calendar. In each duration
 * format an account can choose, every day shows its weekday, date and total whole: no line ends
 * in an ellipsis, is cut off or leaves its day, and no two days overlap. The chosen day stays
 * marked, selected for TalkBack and in Tokkl's frame. DayCalendarTest covers the strip at the
 * usual size.
 */
class WeekStripFitTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST)).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The account's settings, whose duration format each step changes. */
    private var settings by mutableStateOf(SETTINGS)

    @Test
    fun theDaysFitAndTheChosenDayStaysMarked() {
        show()
        for (format in DurationFormat.entries) {
            settings = SETTINGS.copy(durationFormat = format)
            composeRule.waitForIdle()

            assertDaysFit(days(), format)
            stripDay("Mon, 12 Oct").assertIsSelected()
            assertTrue("the chosen day has a frame, $format", framed(stripDay("Mon, 12 Oct")))
            for (other in listOf(string(R.string.history_today), "Tue, 13 Oct")) {
                stripDay(other).assertIsNotSelected()
                assertFalse("$other has no frame, $format", framed(stripDay(other)))
            }
        }
    }

    @Test
    fun everyDayShowsItsWeekdayDateAndTotalWhole() {
        show()
        for (format in DurationFormat.entries) {
            settings = SETTINGS.copy(durationFormat = format)
            composeRule.waitForIdle()

            assertTextsWhole(days(), format)
            // The strip may leave the seconds out where the total does not fit; TalkBack does not.
            stripDay(string(R.string.history_day_spoken, "Mon, 12 Oct", "10 hours, 30 minutes, 15 seconds")).assertExists()
        }
    }

    /** The strip's seven days. */
    private fun days(): List<SemanticsNode> =
        composeRule.onAllNodes(stripDay).fetchSemanticsNodes().also { assertEquals("the strip's days", 7, it.size) }

    /** The calendar on [SELECTED], in a box of the width the Timer page gives it. */
    private fun show() {
        composeRule.setContent {
            TokklTheme(darkTheme = false) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .screenContent(),
                ) {
                    CalendarContent(
                        state = CalendarState.Shown(
                            selected = SELECTED,
                            weekStart = MONDAY,
                            today = THURSDAY,
                            zone = ZoneOffset.UTC,
                            settings = settings,
                            entries = ENTRIES,
                            week = WeekSource.Cached,
                        ),
                        now = NOON,
                        onStep = {},
                        onSelect = {},
                        onToday = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
    }

    /** Each day is inside the screen and at least 48dp tall, and no two days overlap. */
    private fun assertDaysFit(days: List<SemanticsNode>, format: DurationFormat) {
        val screen = composeRule.onRoot().getBoundsInRoot()
        val bounds = days.map { day -> day.spoken() to byId(day.id).getUnclippedBoundsInRoot() }
        for ((name, box) in bounds) {
            assertTrue("$name at $box leaves the screen $screen, $format", box.inside(screen))
            assertTrue("$name is ${box.bottom - box.top} tall, $format", box.bottom - box.top >= MIN_TOUCH_TARGET - SLACK)
        }
        for ((i, first) in bounds.withIndex()) {
            for (second in bounds.drop(i + 1)) {
                assertFalse("${first.first} overlaps ${second.first}, $format", first.second.overlaps(second.second))
            }
        }
    }

    /**
     * The weekday, the date and the total of each day: none ends in an ellipsis or is cut off,
     * and each stays inside its day. The strip shrinks a line that does not fit, down to a
     * smallest size, and cuts it short with an ellipsis only below that. TalkBack reads each day
     * as one tab, so the lines show only in the unmerged tree.
     */
    private fun assertTextsWhole(days: List<SemanticsNode>, format: DurationFormat) {
        for (day in days) {
            val box = byId(day.id).getUnclippedBoundsInRoot()
            val lines = composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and hasAnyAncestor(byIdMatcher(day.id)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertEquals("the weekday, date and total of ${day.spoken()}, $format", 3, lines.size)
            for (line in lines) {
                val text = line.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                val layout = layoutOf(line)
                assertFalse("\"$text\" of ${day.spoken()} ends in an ellipsis, $format", layout.isLineEllipsized(layout.lineCount - 1))
                assertFalse("\"$text\" of ${day.spoken()} is cut off, $format", layout.didOverflowHeight)
                val lineBox = composeRule.onNode(byIdMatcher(line.id), useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue("\"$text\" at $lineBox leaves ${day.spoken()} at $box, $format", lineBox.inside(box))
            }
        }
    }

    /** Whether [day] draws Tokkl's frame along its start side, as DayCalendarTest checks it. */
    private fun framed(day: SemanticsNodeInteraction): Boolean {
        val pixels = day.captureToImage().toPixelMap()
        val edge = pixels[FRAME_PIXEL, pixels.height / 2]
        return close(edge, TokklLightColorScheme.outline)
    }

    private fun close(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < COLOUR_TOLERANCE && abs(a.green - b.green) < COLOUR_TOLERANCE && abs(a.blue - b.blue) < COLOUR_TOLERANCE

    /**
     * The strip's text sizes itself, and for such text semantics hands out the layout it drew,
     * so the ellipsis and the height are the ones on screen.
     */
    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action) { "no text layout" }.invoke(results)
        return results.single()
    }

    private fun stripDay(spoken: String): SemanticsNodeInteraction =
        composeRule.onNode(stripDay and hasContentDescription(spoken, substring = true))

    private fun byId(id: Int): SemanticsNodeInteraction = composeRule.onNode(byIdMatcher(id))

    private fun byIdMatcher(id: Int): SemanticsMatcher = SemanticsMatcher("has id $id") { it.id == id }

    private fun SemanticsNode.spoken(): String = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    private fun DpRect.inside(other: DpRect): Boolean =
        left >= other.left - SLACK && top >= other.top - SLACK && right <= other.right + SLACK && bottom <= other.bottom + SLACK

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        /** Less than a pixel, for bounds that touch after rounding. */
        val SLACK = 0.5.dp
        val MIN_TOUCH_TARGET = 48.dp

        val MONDAY: LocalDate = LocalDate.of(2026, 10, 12)
        val THURSDAY: LocalDate = LocalDate.of(2026, 10, 15)

        /** The chosen day, which is not today, so the frame and today's circle both show. */
        val SELECTED: LocalDate = MONDAY
        val NOON: Instant = Instant.parse("2026-10-15T12:00:00Z")
        val SETTINGS = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)

        /**
         * The longest totals of each format: a ten and a half hour Monday, 10:30:15 or 10.50 h,
         * a 45 minute Tuesday, 45:00 min in the classic format, and half an hour today.
         */
        val ENTRIES = listOf(
            entry(1, "Long day", "2026-10-12T08:00:00Z", "2026-10-12T18:30:15Z"),
            entry(2, "Stand-up", "2026-10-13T09:00:00Z", "2026-10-13T09:45:00Z"),
            entry(3, "Review", "2026-10-15T08:00:00Z", "2026-10-15T08:30:00Z"),
        )

        /** A day of the week's strip, which TalkBack reads as a tab. */
        val stripDay: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

        /** A pixel inside the 2dp frame, which is about five pixels wide at 420 dpi. */
        const val FRAME_PIXEL = 2
        const val COLOUR_TOLERANCE = 0.06f

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
    }
}
