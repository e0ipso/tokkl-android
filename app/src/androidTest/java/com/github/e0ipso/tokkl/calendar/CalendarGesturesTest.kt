package com.github.e0ipso.tokkl.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The calendar's drags (docs/PRD.md K2), by touch as a finger makes them: a long press, then a
 * drag. On empty space it creates an entry, on a block it moves it, and on a block's top or bottom
 * edge it moves that end, in whole steps of 15 minutes at Tokkl's default resolution of 5. Each
 * test checks the change the calendar's model queues, on this upright phone, which shows a day,
 * and in a window of medium width, which shows the week. TalkBack opens the entry form instead.
 * Today is Thursday 15 October 2026 in UTC, with one entry from 09:00 to 10:00.
 */
class CalendarGesturesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val changes = RecordingChanges()

    /** What the calendar asked the Timer page to open in the entry form. */
    private val opened = CopyOnWriteArrayList<String>()

    @After
    fun stopTheModel() {
        scope.cancel()
    }

    @Test
    fun aDragOnEmptySpaceCreatesAnEntryAndOpensIt() {
        val model = show(window = null)

        // From 13:00, four hours under the block's top, down an hour and a half.
        composeRule.onNode(block).performTouchInput { holdAndDrag(Offset(centerX, hours(4f)), hours(1.5f)) }

        awaitCalls(1)
        assertEquals(listOf("create 7 ${EntryFields()} 2026-10-15T13:00:00Z 2026-10-15T14:30:00Z"), changes.calls)
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        assertEquals(listOf("edit $LOCAL_ID"), opened)
        assertEquals(THURSDAY, shown(model).selected)
    }

    @Test
    fun aDragOnABlockMovesIt() {
        show(window = null)

        composeRule.onNode(block).performTouchInput { holdAndDrag(center, hours(1f)) }

        awaitCalls(1)
        assertEquals(listOf("update ${ENTRY.id} $FIELDS 2026-10-15T10:00:00Z 2026-10-15T11:00:00Z"), changes.calls)
    }

    @Test
    fun aDragOnABlocksEdgesMovesThatEnd() {
        show(window = null)

        composeRule.onNode(block).performTouchInput { holdAndDrag(Offset(centerX, bottom - EDGE.toPx()), hours(0.5f)) }
        awaitCalls(1)
        composeRule.onNode(block).performTouchInput { holdAndDrag(Offset(centerX, top + EDGE.toPx()), hours(-0.75f)) }
        awaitCalls(2)

        assertEquals(
            listOf(
                "update ${ENTRY.id} $FIELDS 2026-10-15T09:00:00Z 2026-10-15T10:30:00Z",
                "update ${ENTRY.id} $FIELDS 2026-10-15T08:15:00Z 2026-10-15T10:00:00Z",
            ),
            changes.calls,
        )
    }

    /** A held block dragged sideways is no swipe, and let go where it was, it opens in the form. */
    @Test
    fun aHeldBlockDraggedSidewaysStaysOnItsDay() {
        val model = show(window = null)

        composeRule.onNode(block).performTouchInput { holdAndDrag(center, dy = 0f, dx = -width * 2f) }

        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        assertEquals(listOf("edit ${ENTRY.id}"), opened)
        assertEquals(THURSDAY, shown(model).selected)
        assertEquals(emptyList<String>(), changes.calls)
    }

    @Test
    fun theWeekViewCreatesAndMovesEntriesToo() {
        show(window = MEDIUM)

        composeRule.onNode(block).performTouchInput { holdAndDrag(center, hours(-2f)) }
        awaitCalls(1)
        composeRule.onNode(block).performTouchInput { holdAndDrag(Offset(centerX, hours(3f)), hours(1f)) }
        awaitCalls(2)

        assertEquals(
            listOf(
                "update ${ENTRY.id} $FIELDS 2026-10-15T07:00:00Z 2026-10-15T08:00:00Z",
                "create 7 ${EntryFields()} 2026-10-15T12:00:00Z 2026-10-15T13:00:00Z",
            ),
            changes.calls,
        )
    }

    /** A tap on a block opens it in the entry form, and so does TalkBack's click, which reads "Edit entry". */
    @Test
    fun aTapOrTalkBacksClickOpensTheBlockInTheForm() {
        show(window = null)

        composeRule.onNode(block).performTouchInput { click(center) }
        composeRule.onNode(block).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(TIMEOUT_MS) { opened.size == 2 }
        assertEquals(listOf("edit ${ENTRY.id}", "edit ${ENTRY.id}"), opened)
        val label = composeRule.onNode(block).fetchSemanticsNode().config[SemanticsActions.OnClick].label
        assertEquals(context.getString(R.string.entry_edit_action), label)
    }

    /** TalkBack's alternative to a create drag: an action on the day's strip tab or head opens the form on that day. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun eachDaysHeadOffersTalkBackAnActionThatAddsAnEntry() {
        show(window = null)
        composeRule.onNode(hasContentDescription(dayLabel(MONDAY), substring = true))
            .performCustomAccessibilityActionWithLabel(addLabel(MONDAY))

        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        assertEquals(listOf("add $MONDAY"), opened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theWeekViewsDayHeadsOfferTheActionToo() {
        show(window = MEDIUM)
        composeRule.onNode(hasContentDescription(dayLabel(MONDAY), substring = true))
            .performCustomAccessibilityActionWithLabel(addLabel(MONDAY))

        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        assertEquals(listOf("add $MONDAY"), opened)
    }

    /** The calendar on [ENTRY], in a window of [window], or the phone's own. */
    private fun show(window: DpSize?): CalendarModel {
        val model = CalendarModel(
            entries = MutableStateFlow(listOf(EntryWithSendState(ENTRY, unsent = false))),
            projects = MutableStateFlow(emptyList()),
            tasks = MutableStateFlow(emptyList()),
            accountSettings = MutableStateFlow(SETTINGS),
            changes = changes,
            client = { null },
            scope = scope,
            io = Dispatchers.IO,
            clock = CLOCK,
        )
        composeRule.setContent {
            TokklTheme(darkTheme = false) {
                WindowOfSize(window) {
                    WeekCalendar(
                        model = model,
                        onEdit = { id, _ -> opened += "edit $id" },
                        onAdd = { day -> opened += "add $day" },
                        clock = CLOCK,
                    )
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodes(block).fetchSemanticsNodes().isNotEmpty() }
        return model
    }

    private fun shown(model: CalendarModel): CalendarState.Shown = model.state.value as CalendarState.Shown

    /**
     * Presses at [from], holds past the long-press time, drags [dy] down and [dx] across in a few
     * moves, and lifts.
     */
    private fun TouchInjectionScope.holdAndDrag(from: Offset, dy: Float, dx: Float = 0f) {
        down(from)
        advanceEventTime(viewConfiguration.longPressTimeoutMillis + HOLD_MARGIN_MS)
        repeat(MOVES) { moveBy(Offset(dx / MOVES, dy / MOVES), delayMillis = MOVE_MS) }
        up()
    }

    /** [count] hours of the grid in pixels. */
    private fun TouchInjectionScope.hours(count: Float): Float = count * HOUR.toPx()

    private fun awaitCalls(count: Int) = composeRule.waitUntil(TIMEOUT_MS) { changes.calls.size >= count }

    private fun dayLabel(day: LocalDate): String {
        val words = DateWords(context.resources)
        return context.getString(R.string.history_day, words.weekday(day), words.short(day))
    }

    private fun addLabel(day: LocalDate): String = context.getString(R.string.calendar_add_entry, dayLabel(day))

    /** The entry's block, which TalkBack reads with its description first. */
    private val block: SemanticsMatcher = hasContentDescription("${ENTRY.description}, ", substring = true)

    /** Records what the calendar queues, and answers a create with [LOCAL_ID]. */
    private class RecordingChanges : EntryChanges {
        val calls = CopyOnWriteArrayList<String>()

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            calls += "create $workspaceId $fields $start $stop"
            return LOCAL_ID
        }

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean {
            calls += "update $entryId $fields $start $stop"
            return true
        }

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError("the calendar starts no timer")

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = throw AssertionError("the calendar stops no timer")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("the calendar deletes nothing")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val LOCAL_ID = -4L
        const val HOLD_MARGIN_MS = 100L
        const val MOVES = 4
        const val MOVE_MS = 30L

        /** An hour row of the grid, and how far into a block its edge reaches. */
        val HOUR = 48.dp
        val EDGE = 4.dp

        /** Thursday 15 October 2026 at noon. */
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
        val THURSDAY: LocalDate = LocalDate.of(2026, 10, 15)
        val MONDAY: LocalDate = LocalDate.of(2026, 10, 12)

        /** A window of medium width, an unfolded foldable or a tablet, where the week shows. */
        val MEDIUM = DpSize(840.dp, 900.dp)

        val SETTINGS = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = 5.0, defaultWorkspaceId = 7)

        val ENTRY = CachedTimeEntry(
            id = 21,
            workspaceId = 7,
            description = "Review the plan",
            projectId = null,
            taskId = null,
            tagIds = emptyList(),
            billable = false,
            start = Instant.parse("2026-10-15T09:00:00Z"),
            stop = Instant.parse("2026-10-15T10:00:00Z"),
            tags = emptyList(),
        )
        val FIELDS = EntryFields(description = "Review the plan")
    }
}
