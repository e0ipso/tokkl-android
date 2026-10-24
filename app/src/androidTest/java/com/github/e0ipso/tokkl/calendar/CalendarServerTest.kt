package com.github.e0ipso.tokkl.calendar

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.BackgroundTrip
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Calendar view against the real Tokkl container, in the real activity, on an upright phone,
 * where it shows one day at a time (docs/PRD.md K1). Each test signs in to an account of its own,
 * in Madrid, whose weeks end today, so last week starts on the cache's first day, 13 days ago,
 * and every week before it loads from the server. Tests move the calendar through the activity's
 * own model, as its arrows do, and read the blocks as TalkBack reads them.
 */
class CalendarServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val zone = ZoneId.of(ZONE)
    private val today: LocalDate get() = LocalDate.now(zone)
    private lateinit var account: TestAccounts.Account

    /** The first day of a week that only the server has, four weeks before this one. */
    private val olderWeek: LocalDate get() = today.minusDays(6 + 28)

    @Before
    fun signInToAnAccountWhoseWeeksEndToday() {
        account = TestAccounts.create(timezone = ZONE)
        // Toggl numbers the days from 0 for Sunday. A week that starts tomorrow ends today.
        val beginningOfWeek = today.plusDays(1).dayOfWeek.value % 7
        val me = buildJsonObject { put("beginning_of_week", beginningOfWeek) }
        account.client.send("PUT", account.client.server.api("me"), me, JsonObject.serializer()).orFail("PUT /api/v9/me")
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.set(false, account.client)
        } finally {
            try {
                TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    /** TalkBack reads a block's description, times and duration, in the account's zone and clock. */
    @Test
    fun aBlockReadsItsEntry() {
        val day = today.minusDays(1)
        seed("Write the report", day.atTime(9, 0), day.atTime(10, 30))
        refresh()

        withCalendar { model ->
            show(model, day)

            awaitBlock(spoken("Write the report", day.atTime(9, 0), day.atTime(10, 30)))
        }
    }

    @Test
    fun deletingFromTheCalendarEditorClosesItAndUndoRestoresTheBlockOffline() {
        val day = today.minusDays(1)
        val description = "Delete from the calendar editor"
        val id = seed(description, day.atTime(9, 0), day.atTime(10, 30))
        refresh()
        assertTrue("Tokkl offers Undo for this editor", app.capabilities.state.value.restore)

        withCalendar { model ->
            show(model, day)
            val label = spoken(description, day.atTime(9, 0), day.atTime(10, 30))
            awaitBlock(label)
            AirplaneMode.set(true, account.client)
            fun deleteFromEditor() {
                composeRule.onNode(blockOf(description)).performScrollTo()
                    .performSemanticsAction(SemanticsActions.OnClick)
                composeRule.waitUntil("the calendar opens its entry editor", LOAD_TIMEOUT_MS) {
                    exists(hasText(string(R.string.entry_form_edit))) &&
                        exists(hasText(string(R.string.history_delete)) and hasClickAction())
                }
                composeRule.onNode(hasText(string(R.string.history_delete)) and hasClickAction())
                    .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
                composeRule.waitUntil("Delete closes the editor and removes the calendar block", LOAD_TIMEOUT_MS) {
                    !exists(hasText(string(R.string.entry_save))) && !exists(blockOf(description)) &&
                        runBlocking { app.cache.entries.first() }.none { it.entry.id == id }
                }
                assertEquals(1, runBlocking { app.changes.unsentCount.first() })
            }

            deleteFromEditor()
            composeRule.onNode(hasText(string(R.string.history_undo)) and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick)
            awaitBlock(label)
            assertEquals("Undo cancels the offline delete", 0, runBlocking { app.changes.unsentCount.first() })
            assertEquals(id, runBlocking { app.cache.entries.first() }.single().entry.id)

            deleteFromEditor()
            AirplaneMode.set(false, account.client)
            sendTheQueue()
            val answer = account.client.get(
                account.client.server.api("me", "time_entries", id.toString()), JsonObject.serializer(),
            )
            assertEquals("the editor's queued delete reaches the server", 404,
                ((answer as? ApiResult.Failure)?.error as? ApiError.UnexpectedStatus)?.status)
        }
    }

    /**
     * An entry logged offline shows at once, and TalkBack reads "Not sent" for its block until the
     * server has it. Its times are the ones the server will keep, so they stay when it answers.
     */
    @Test
    fun anUnsentBlockSaysNotSentUntilTheServerHasIt() {
        val day = today.minusDays(1)
        val start = day.atTime(11, 0)
        val stop = day.atTime(11, 45)
        val workspace = account.workspaceId
        refresh()
        AirplaneMode.set(true, account.client)
        runBlocking { app.changes.create(workspace, EntryFields(description = "Logged offline"), start.atZone(zone).toInstant(), stop.atZone(zone).toInstant()) }

        withCalendar { model ->
            show(model, day)
            val sent = spoken("Logged offline", start, stop)
            awaitBlock("$sent, ${string(R.string.history_unsent)}")

            AirplaneMode.set(false, account.client)
            sendTheQueue()

            awaitBlock(sent)
        }
    }

    /**
     * Offline, last week still shows from the phone, and an older week says that it needs the
     * server, with Try again. Once the network comes back with the app on screen, the week loads
     * without a tap.
     */
    @Test
    fun offlineLastWeekShowsAndAnOlderWeekLoadsWhenTheNetworkReturns() {
        val lastWeek = today.minusDays(10)
        val older = olderWeek.plusDays(2)
        seed("Kept on the phone", lastWeek.atTime(14, 0), lastWeek.atTime(15, 0))
        seed("Kept on the server", older.atTime(14, 0), older.atTime(15, 0))
        refresh()
        AirplaneMode.set(true, account.client)

        withCalendar { model ->
            show(model, lastWeek)
            awaitBlock(spoken("Kept on the phone", lastWeek.atTime(14, 0), lastWeek.atTime(15, 0)))

            show(model, older)
            composeRule.waitUntil("the week says it needs the server", LOAD_TIMEOUT_MS) { exists(hasText(string(R.string.calendar_offline_title))) }
            assertTrue("no block before the week loads", !exists(blockOf("Kept on the server")))
            assertTrue("Try again shows", exists(hasText(string(R.string.history_retry)) and hasClickAction()))

            AirplaneMode.set(false, account.client)

            awaitBlock(spoken("Kept on the server", older.atTime(14, 0), older.atTime(15, 0)))
            assertTrue("the message goes", !exists(hasText(string(R.string.calendar_offline_title))))
        }
    }

    /** A swipe to the right shows the day before, and one to the left the day after. */
    @Test
    fun swipesMoveBetweenDays() {
        val yesterday = today.minusDays(1)
        seed("Today's block", today.atTime(6, 0), today.atTime(7, 0))
        seed("Yesterday's block", yesterday.atTime(6, 0), yesterday.atTime(7, 0))
        refresh()

        withCalendar { model ->
            awaitBlock(spoken("Today's block", today.atTime(6, 0), today.atTime(7, 0)))
            composeRule.onNode(blockOf("Today's block")).performScrollTo().performTouchInput { swipeRight() }

            awaitBlock(spoken("Yesterday's block", yesterday.atTime(6, 0), yesterday.atTime(7, 0)))
            assertEquals(yesterday, (model.state.value as CalendarState.Shown).selected)
            assertTrue("one day shows at a time", !exists(blockOf("Today's block")))

            composeRule.onNode(blockOf("Yesterday's block")).performScrollTo().performTouchInput { swipeLeft() }

            awaitBlock(spoken("Today's block", today.atTime(6, 0), today.atTime(7, 0)))
            assertEquals(today, (model.state.value as CalendarState.Shown).selected)
        }
    }

    /**
     * The arrow back from the cache's first day, the first day of last week, crosses into the week
     * before, which loads from the server, and its strip shows that week's days with their totals.
     * A tap on a day of the strip shows it, and the arrow forward crosses back into last week.
     */
    @Test
    fun theArrowsAndTheStripCrossIntoAWeekOnlyTheServerHas() {
        val cacheStart = today.minusDays(13)
        val dayBefore = cacheStart.minusDays(1)
        val weekBefore = cacheStart.minusDays(7)
        seed("On the cache's first day", cacheStart.atTime(9, 0), cacheStart.atTime(10, 0))
        seed("On the server", dayBefore.atTime(9, 0), dayBefore.atTime(10, 0))
        seed("Early in the week before", weekBefore.atTime(9, 0), weekBefore.atTime(9, 30))
        refresh()

        withCalendar { model ->
            show(model, cacheStart)
            awaitBlock(spoken("On the cache's first day", cacheStart.atTime(9, 0), cacheStart.atTime(10, 0)))

            composeRule.onNode(hasContentDescription(string(R.string.calendar_previous_day)) and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick)

            awaitBlock(spoken("On the server", dayBefore.atTime(9, 0), dayBefore.atTime(10, 0)))
            assertEquals(dayBefore, (model.state.value as CalendarState.Shown).selected)
            composeRule.onNode(stripDay(dayBefore, minutes = 60)).assertIsSelected()

            composeRule.onNode(stripDay(weekBefore, minutes = 30)).performSemanticsAction(SemanticsActions.OnClick)

            awaitBlock(spoken("Early in the week before", weekBefore.atTime(9, 0), weekBefore.atTime(9, 30)))
            assertEquals(weekBefore, (model.state.value as CalendarState.Shown).selected)

            composeRule.onNode(stripDay(dayBefore, minutes = 60)).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.onNode(hasContentDescription(string(R.string.calendar_next_day)) and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick)

            awaitBlock(spoken("On the cache's first day", cacheStart.atTime(9, 0), cacheStart.atTime(10, 0)))
            composeRule.onNode(stripDay(cacheStart, minutes = 60)).assertIsSelected()
        }
    }

    /**
     * The cache keeps entries by their start, so an entry from the evening before its first day
     * that runs past midnight is not in it. Last week, which starts on that first day, still
     * shows it.
     */
    @Test
    fun anEntryFromTheEveningBeforeTheCacheShowsInLastWeek() {
        val cacheStart = today.minusDays(13)
        seed("From the evening before", cacheStart.minusDays(1).atTime(23, 0), cacheStart.atTime(1, 0))
        refresh()

        withCalendar { model ->
            show(model, cacheStart)
            assertEquals(WeekSource.Cached, (model.state.value as CalendarState.Shown).week)

            awaitBlock(spoken("From the evening before", cacheStart.minusDays(1).atTime(23, 0), cacheStart.atTime(1, 0)))
        }
    }

    /** An entry that starts two days before an older week and runs into it shows in that week. */
    @Test
    fun anEntryFromTwoDaysBeforeAnOlderWeekShowsInIt() {
        val start = olderWeek.minusDays(2).atTime(23, 0)
        val stop = olderWeek.atTime(1, 0)
        seed("From two days before", start, stop)
        refresh()

        withCalendar { model ->
            show(model, olderWeek)

            awaitBlock(spoken("From two days before", start, stop))
            assertEquals(WeekSource.Loaded, (model.state.value as CalendarState.Shown).week)
        }
    }

    /** An entry deleted in the web app while the app was in the background goes once it returns. */
    @Test
    fun anEntryDeletedElsewhereGoesFromAnOlderWeekAfterTheForeground() {
        val day = olderWeek.plusDays(2)
        val id = seed("Deleted in the web app", day.atTime(10, 0), day.atTime(10, 30))
        refresh()

        withCalendar { model ->
            show(model, day)
            awaitBlock(spoken("Deleted in the web app", day.atTime(10, 0), day.atTime(10, 30)))

            val activity = BackgroundTrip.leave()
            account.deleteEntry(id)
            BackgroundTrip.comeBack(activity)

            composeRule.waitUntil("the deleted entry goes", LOAD_TIMEOUT_MS) { !exists(blockOf("Deleted in the web app")) }
            assertEquals(WeekSource.Loaded, (model.state.value as CalendarState.Shown).week)
        }
    }

    /**
     * Offline, a long press and a drag on a cached day's block moves the entry an hour through the
     * queue (docs/PRD.md K2): the cache has it at once, unsent, and the server once the network
     * is back.
     */
    @Test
    fun offlineADragMovesACachedEntryThroughTheQueue() {
        val day = today.minusDays(1)
        val id = seed("Moved offline", day.atTime(9, 0), day.atTime(10, 0))
        refresh()
        AirplaneMode.set(true, account.client)

        withCalendar { model ->
            show(model, day)
            awaitBlock(spoken("Moved offline", day.atTime(9, 0), day.atTime(10, 0)))
            dragOnGrid(blockOf("Moved offline"), from = { center }, dy = { HOUR.toPx() })

            val moved = awaitCached("the moved entry") { it.entry.id == id && it.entry.start == instant(day.atTime(10, 0)) }
            assertTrue("unsent offline", moved.unsent)
            assertEquals(instant(day.atTime(11, 0)), moved.entry.stop)
            awaitBlock("${spoken("Moved offline", day.atTime(10, 0), day.atTime(11, 0))}, ${string(R.string.history_unsent)}")

            AirplaneMode.set(false, account.client)
            sendTheQueue()
            val stored = account.entry(id)
            assertEquals(instant(day.atTime(10, 0)), OffsetDateTime.parse(stored.getValue("start").jsonPrimitive.content).toInstant())
            assertEquals(instant(day.atTime(11, 0)), OffsetDateTime.parse(stored.getValue("stop").jsonPrimitive.content).toInstant())
        }
    }

    /**
     * A drag on a block of a week only the server has keeps the new times once the change is sent
     * and the refresh after it drops the entry from the phone, and a second drag starts from them.
     */
    @Test
    fun aDragOnAnOlderWeekKeepsItsTimesAfterTheSend() {
        val day = olderWeek.plusDays(2)
        val description = "Moved on an older week"
        val id = seed(description, day.atTime(9, 0), day.atTime(10, 0))
        refresh()

        withCalendar { model ->
            show(model, day)
            awaitBlock(spoken(description, day.atTime(9, 0), day.atTime(10, 0)))

            dragOnGrid(blockOf(description), from = { center }, dy = { HOUR.toPx() })
            awaitStoredAndDropped(id, day.atTime(10, 0), day.atTime(11, 0))
            awaitBlock(spoken(description, day.atTime(10, 0), day.atTime(11, 0)))
            assertTrue("the server week's old copy is gone", !exists(hasContentDescription(spoken(description, day.atTime(9, 0), day.atTime(10, 0)))))

            dragOnGrid(blockOf(description), from = { center }, dy = { HOUR.toPx() })
            awaitStoredAndDropped(id, day.atTime(11, 0), day.atTime(12, 0))
            awaitBlock(spoken(description, day.atTime(11, 0), day.atTime(12, 0)))
        }
    }

    /**
     * Offline, a long press and a drag on a cached day's empty space queues a new entry from where
     * the press was, and the entry form opens on it, as the web app's editor does.
     */
    @Test
    fun offlineADragOnEmptySpaceCreatesAnEntryAndOpensTheForm() {
        val day = today.minusDays(1)
        seed("Anchor", day.atTime(9, 0), day.atTime(9, 30))
        refresh()
        AirplaneMode.set(true, account.client)

        withCalendar { model ->
            show(model, day)
            awaitBlock(spoken("Anchor", day.atTime(9, 0), day.atTime(9, 30)))
            // Down half an hour from 08:00, an hour over the anchor's top, or where the anchor
            // scrolled into view at the grid's top, from 10:00, half an hour under its bottom.
            var start = day.atTime(8, 0)
            dragOnGrid(blockOf("Anchor"), from = { onGrid ->
                val above = Offset(centerX, -HOUR.toPx())
                if (above.y in onGrid) {
                    above
                } else {
                    start = day.atTime(10, 0)
                    Offset(centerX, height + HOUR.toPx() / 2)
                }
            }, dy = { HOUR.toPx() / 2 })

            val created = awaitCached("the created entry") { it.entry.start == instant(start) }
            assertTrue("unsent offline", created.unsent)
            assertEquals(instant(start.plusMinutes(30)), created.entry.stop)
            composeRule.waitUntil("the entry form opens", LOAD_TIMEOUT_MS) { exists(hasText(string(R.string.entry_save))) }
        }
    }

    /** Back in the foreground offline, an older week keeps the entries it loaded, with no message. */
    @Test
    fun offlineTheForegroundKeepsAnOlderWeek() {
        val day = olderWeek.plusDays(2)
        seed("Kept offline", day.atTime(10, 0), day.atTime(10, 30))
        refresh()

        withCalendar { model ->
            show(model, day)
            val block = spoken("Kept offline", day.atTime(10, 0), day.atTime(10, 30))
            awaitBlock(block)

            val activity = BackgroundTrip.leave()
            AirplaneMode.set(true, account.client)
            BackgroundTrip.comeBack(activity)

            composeRule.waitUntil("the calendar counts the trip", LOAD_TIMEOUT_MS) { model.foregrounds.value == 1 }
            // The week loads again and fails. Nothing on screen may change meanwhile or after.
            val until = SystemClock.uptimeMillis() + STAYS_MS
            while (SystemClock.uptimeMillis() < until) {
                composeRule.waitForIdle()
                assertTrue("the block stays", exists(hasContentDescription(block)))
                assertTrue("no offline message", !exists(hasText(string(R.string.calendar_offline_title))))
                assertEquals(WeekSource.Loaded, (model.state.value as CalendarState.Shown).week)
                SystemClock.sleep(POLL_MS)
            }
        }
    }

    private fun refresh() = runBlocking { app.accountSettings.refresh().join() }

    /**
     * Scrolls the Timer page's top away and the grid until [anchor]'s block shows, then holds and
     * drags from [from], relative to the block, by [dy]. [from] gets the block's y range that the
     * grid shows on screen. A press is a touch on the screen, so it must land on the part of the
     * grid that shows: a block clipped to nothing puts its touch in the screen's corner. The press
     * point is checked against the grid on screen first.
     */
    private fun dragOnGrid(
        anchor: SemanticsMatcher,
        from: TouchInjectionScope.(onGrid: ClosedFloatingPointRange<Float>) -> Offset,
        dy: TouchInjectionScope.() -> Float,
    ) {
        // As under a thumb, the Timer page's title, timer, favorites and switch scroll away first,
        // so the grid gets the page's height. A semantics scroll alone would spend part of the
        // grid's scroll on the page's top and leave the block off the screen.
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText(string(R.string.timer_view_list)) and isSelectable()))
            .performTouchInput { swipeUp(startY = bottom - 1f, endY = top, durationMillis = SWIPE_MS) }
        composeRule.waitForIdle()
        val block = composeRule.onNode(anchor)
        block.performScrollTo()
        // The grid is the innermost scrolling node around the block; the page may scroll too.
        val grid = composeRule.onAllNodes(hasScrollAction() and hasAnyDescendant(anchor)).fetchSemanticsNodes()
            .minBy { it.boundsInRoot.height }.boundsInRoot
        val shown = block.fetchSemanticsNode().boundsInRoot
        block.performTouchInput {
            val press = from((grid.top - shown.top)..(grid.bottom - shown.top))
            val y = shown.top + press.y
            check(y in grid.top..grid.bottom) { "the press at $y px is off the grid on screen, ${grid.top} to ${grid.bottom} px" }
            holdAndDrag(press, dy())
        }
    }

    /** Presses at [from], holds past the long-press time, drags [dy] down in a few moves, and lifts. */
    private fun TouchInjectionScope.holdAndDrag(from: Offset, dy: Float) {
        down(from)
        advanceEventTime(viewConfiguration.longPressTimeoutMillis + HOLD_MARGIN_MS)
        repeat(MOVES) { moveBy(Offset(0f, dy / MOVES), delayMillis = MOVE_MS) }
        up()
    }

    /** Waits until the cache holds an entry that [matches], and returns it. */
    private fun awaitCached(what: String, matches: (EntryWithSendState) -> Boolean): EntryWithSendState {
        var found: EntryWithSendState? = null
        composeRule.waitUntil(what, LOAD_TIMEOUT_MS) {
            found = runBlocking { app.cache.entries.first() }.firstOrNull(matches)
            found != null
        }
        return checkNotNull(found)
    }

    /**
     * Waits until the queue has sent the entry [id] and the server stores it from [start] to
     * [stop], then until the refresh after the send has dropped it from the phone, as it does an
     * entry that starts before the cache.
     */
    private fun awaitStoredAndDropped(id: Long, start: LocalDateTime, stop: LocalDateTime) {
        val span = instant(start) to instant(stop)
        composeRule.waitUntil("the server stores the entry from $start to $stop", LOAD_TIMEOUT_MS) {
            sendTheQueue()
            val stored = account.entry(id)
            val times = listOf("start", "stop").map { OffsetDateTime.parse(stored.getValue(it).jsonPrimitive.content).toInstant() }
            times == span.toList()
        }
        composeRule.waitUntil("the refresh drops the entry from the phone", LOAD_TIMEOUT_MS) {
            runBlocking { app.cache.entries.first() }.none { it.entry.id == id }
        }
    }

    private fun instant(at: LocalDateTime): Instant = at.atZone(zone).toInstant()

    /** Sends the queue until it is empty, as the next refresh would. */
    private fun sendTheQueue() {
        val deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            check(SystemClock.uptimeMillis() < deadline) { "the queue did not empty" }
            runBlocking { app.sender.send().join() }
        }
    }

    /** Creates a stopped entry from [start] to [stop], in the account's zone, and returns its id. */
    private fun seed(description: String, start: LocalDateTime, stop: LocalDateTime): Long =
        account.createEntry(description, start.atZone(zone).toInstant(), stop.atZone(zone).toInstant())

    /** What TalkBack reads for the block of a stopped entry with no project. */
    private fun spoken(description: String, start: LocalDateTime, stop: LocalDateTime): String {
        val settings = checkNotNull(app.accountSettings.state.value) { "no account settings" }
        val from = start.atZone(zone).toInstant()
        val to = stop.atZone(zone).toInstant()
        return listOf(
            description,
            context.getString(R.string.history_time_range_spoken, settings.formatTime(from, zone), settings.formatTime(to, zone)),
            spokenDuration(context.resources, to.epochSecond - from.epochSecond),
        ).joinToString(", ")
    }

    /** Starts the app, shows the Calendar view and runs [steps] with the activity's calendar model. */
    private fun withCalendar(steps: (CalendarModel) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            composeRule.onNode(hasText(string(R.string.timer_view_calendar)) and isSelectable())
                .performSemanticsAction(SemanticsActions.OnClick)
            var model: CalendarModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[CalendarViewModel::class.java].model }
            steps(checkNotNull(model))
        }
    }

    /** Shows [day], as the arrows would. */
    private fun show(model: CalendarModel, day: LocalDate) {
        instrumentation.runOnMainSync {
            model.showToday()
            model.step(ChronoUnit.DAYS.between(today, day))
        }
        composeRule.waitUntil("the calendar shows $day", LOAD_TIMEOUT_MS) {
            (model.state.value as? CalendarState.Shown)?.selected == day
        }
    }

    private fun awaitBlock(spoken: String) {
        composeRule.waitUntil("a block reads \"$spoken\"", LOAD_TIMEOUT_MS) { exists(hasContentDescription(spoken)) }
    }

    /** The day of the week's strip that TalkBack reads as [day] with [minutes] in total, once the week has loaded. */
    private fun stripDay(day: LocalDate, minutes: Long): SemanticsMatcher {
        val words = DateWords(context.resources)
        val label = context.getString(R.string.history_day, words.weekday(day), words.short(day))
        val spoken = context.getString(R.string.history_day_spoken, label, spokenDuration(context.resources, minutes * 60))
        return hasContentDescription(spoken) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    }

    /** The block of the entry described [description], which TalkBack reads first. */
    private fun blockOf(description: String): SemanticsMatcher = hasContentDescription("$description, ", substring = true)

    private fun exists(matcher: SemanticsMatcher): Boolean = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val ZONE = "Europe/Madrid"
        const val LOAD_TIMEOUT_MS = 30_000L
        const val SWIPE_MS = 400L

        /** How long the offline foreground test watches the week stay as it was. */
        const val STAYS_MS = 3_000L
        const val POLL_MS = 200L

        /** An hour row of the grid. */
        val HOUR = 48.dp
        const val HOLD_MARGIN_MS = 100L
        const val MOVES = 4
        const val MOVE_MS = 30L
    }
}
