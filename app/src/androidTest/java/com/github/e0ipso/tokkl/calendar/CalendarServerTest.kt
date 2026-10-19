package com.github.e0ipso.tokkl.calendar

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.session.Session
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
     * server. Once the server can be reached, Try again loads it.
     */
    @Test
    fun offlineLastWeekShowsAndAnOlderWeekLoadsOnRetry() {
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

            AirplaneMode.set(false, account.client)
            composeRule.onNode(hasText(string(R.string.history_retry)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

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

    /** The block of the entry described [description], which TalkBack reads first. */
    private fun blockOf(description: String): SemanticsMatcher = hasContentDescription("$description, ", substring = true)

    private fun exists(matcher: SemanticsMatcher): Boolean = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val ZONE = "Europe/Madrid"
        const val LOAD_TIMEOUT_MS = 30_000L

        /** How long the offline foreground test watches the week stay as it was. */
        const val STAYS_MS = 3_000L
        const val POLL_MS = 200L
    }
}
