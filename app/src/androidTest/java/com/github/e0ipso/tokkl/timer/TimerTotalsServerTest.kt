package com.github.e0ipso.tokkl.timer

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Normal acceptance coverage using the app, Room queue and disposable server accounts. */
class TimerTotalsServerTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val app get() = fixture.app
    private val context get() = fixture.context
    @Before fun prepare() = fixture.prepare()
    @After fun cleanup() = fixture.cleanup()

    @Test
    fun listAndOlderCalendarKeepTheSameAccountTotalsAboveTheSwitch() {
        val now = Instant.now()
        runBlocking { app.changes.create(fixture.account.workspaceId, EntryFields("Today"), now.minusSeconds(600), now) }
        fixture.drain()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            assertStoppedTotals()
            assertOrder()
            val before = compose.onNodeWithTag("timer_totals_week").fetchSemanticsNode().config[SemanticsProperties.Text]
            compose.onNode(hasText(context.getString(R.string.timer_view_calendar)) and isSelectable())
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithTag("timer_totals_week").performScrollTo().assertIsDisplayed()
            assertOrder()
            repeat(8) {
                compose.onNodeWithContentDescription(context.getString(R.string.calendar_previous_day))
                    .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            }
            assertStoppedTotals()
            assertEquals(before, compose.onNodeWithTag("timer_totals_week").fetchSemanticsNode().config[SemanticsProperties.Text])
            // A thumb on the page, as CalendarServerTest drags it: a swipe from the root's bottom
            // edge starts on the navigation bar, which does not scroll.
            compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(context.getString(R.string.timer_view_list)) and isSelectable()))
                .performTouchInput { swipeUp(startY = bottom - 1f, endY = top) }
            compose.onNodeWithTag("timer_totals_today").assertIsNotDisplayed()
        }
    }

    @Test
    fun offlineStartStopEditDeleteAndUndoRemapExactlyOnceWithoutReportsOrPaging() {
        // Every hop uses loopback, as in RunningTileServerTest, so a request the app attempts
        // offline reaches both counters: the outer relay hangs instead of failing to connect.
        HangingRelay(TokklTestServer.url).use { relay ->
        RequestCounter(relay.url, "/reports/").use { reports ->
        RequestCounter(reports.url, "GET /api/v9/me/time_entries?").use { history ->
            ServerTrustStore(context).confirmCleartext(history.url)
            Session(context).save(history.url, fixture.account.apiToken)
            fixture.refresh()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                // Isolate the summary's subscription from history's separate paging behavior.
                scenario.onActivity { it.setContent { TokklTheme { TimerTotals(app.cache.timeEntries, app.accountSettings.state) } } }
                val reportCount = reports.count
                val historyCount = history.count
                relay.hang()
                AirplaneMode.set(true, fixture.account.client)
                fixture.start()
                fixture.ageRunning()
                fixture.stop()
                assertStoppedTotals()
                val stopped = fixture.rows().single()
                assertTrue(runBlocking { app.changes.update(stopped.id, EntryFields("Edited total"), stopped.start.minusSeconds(600), stopped.stop) })
                assertStoppedTotals()
                assertTrue(runBlocking { app.changes.delete(stopped.id) })
                assertTrue(fixture.rows().isEmpty())
                assertStoppedTotals()
                runBlocking { app.sender.undoDelete(stopped.id) }
                assertEquals(stopped.id, fixture.rows().single().id)
                assertStoppedTotals()
                fixture.start("Second offline timer")
                fixture.ageRunning()
                fixture.stop()
                assertEquals(2, fixture.rows().size)
                assertStoppedTotals()
                assertEquals("offline summary does not page history", historyCount, history.count)
                val localIds = fixture.rows().map { it.id }
                AirplaneMode.set(false, fixture.account.client)
                relay.release()
                fixture.drain()
                assertStoppedTotals()
                assertEquals(2, fixture.rows().size)
                assertEquals(2, fixture.rows().map { it.id }.distinct().size)
                assertTrue(localIds.any { it < 0 })
                assertTrue(fixture.rows().all { it.id > 0 })
                relay.hang()
                AirplaneMode.set(true, fixture.account.client)
                fixture.start("Offline start and final stop")
                fixture.ageRunning()
                fixture.stop()
                assertStoppedTotals()
                assertEquals(3, fixture.rows().size)
                assertEquals("summary never sends Reports", reportCount, reports.count)
                // Refresh itself requests the cache window. Only check paging during local work.
                val afterRefresh = history.count
                compose.waitForIdle()
                assertEquals("local summary does not page history", afterRefresh, history.count)
                assertTrue("the cache refresh may read its normal window", history.count >= historyCount)
                assertTrue(reports.failures.isEmpty())
                assertTrue(history.failures.isEmpty())
            }
            Session(context).save(TokklTestServer.url, fixture.account.apiToken)
        }
        }
        }
    }

    @Test
    fun localTicksSundayWeekStartMidnightLoadingUnknownZoneAndZeroUseOneCacheSubscription() {
        val start = Instant.parse("2026-10-24T14:59:58Z") // Saturday 23:59:58 in Tokyo.
        val clock = MutableClock(start)
        val settings = MutableStateFlow<AccountSettings?>(AccountSettings("Asia/Tokyo", DayOfWeek.SUNDAY, null))
        val rows = MutableStateFlow(listOf(CachedTimeEntry(1, 20, null, null, "segment", start.minusSeconds(10), null, false, emptyList(), emptyList())))
        val subscriptions = AtomicInteger()
        val input = rows.onStart { subscriptions.incrementAndGet() }
        RequestCounter(TokklTestServer.url, " HTTP/1.1").use { requests ->
            ServerTrustStore(context).confirmCleartext(requests.url)
            Session(context).save(requests.url, fixture.account.apiToken)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.awaitTimerPage()
                scenario.onActivity { it.setContent { TokklTheme { TimerTotals(input, settings, clock) } } }
                awaitTotal("timer_totals_today", "0:00:10")
                val before = requests.count
                clock.at = start.plusSeconds(1)
                awaitTotal("timer_totals_today", "0:00:11")
                rows.value = rows.value.map { it.copy(stop = start.plusSeconds(1)) }
                compose.waitForIdle()
                clock.at = start.plusSeconds(2)
                awaitTotal("timer_totals_week", "0:00:00")
                awaitTotal("timer_totals_today", "0:00:00")
                settings.value = settings.value!!.copy(timeZone = "Unresolved/Account")
                compose.onNodeWithText(context.getString(R.string.history_unknown_time_zone, "Unresolved/Account")).assertExists()
                compose.onNodeWithTag("timer_totals_today").assertDoesNotExist()
                settings.value = null
                compose.onNodeWithTag("timer_totals_week").assertDoesNotExist()
                settings.value = AccountSettings("Asia/Tokyo", DayOfWeek.SUNDAY, null)
                rows.value = emptyList()
                awaitTotal("timer_totals_week", "0:00:00")
                assertEquals(1, subscriptions.get())
                assertEquals("local ticks, settings and week rollover add no requests", before, requests.count)
                assertTrue(requests.failures.isEmpty())
            }
            Session(context).save(TokklTestServer.url, fixture.account.apiToken)
        }
    }

    private fun assertOrder() {
        val today = compose.onNodeWithTag("timer_totals_today").fetchSemanticsNode().boundsInRoot
        val switch = compose.onNode(hasText(context.getString(R.string.timer_view_list)) and isSelectable()).fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithText(context.getString(R.string.timer_add_entry)).fetchSemanticsNode().boundsInRoot
        assertTrue("summary follows timer controls", add.bottom <= today.top)
        assertTrue("summary precedes view switch", today.bottom <= switch.top)
    }
    private fun assertStoppedTotals() {
        val rows = fixture.rows()
        assertTrue(rows.all { it.stop != null })
        val settings = app.accountSettings.state.value!!
        val time = settings.time()!!
        val today = time.local(Instant.now()).toLocalDate()
        val week = time.startOfWeek(today)
        fun seconds(row: CachedTimeEntry) = Math.floorDiv(Duration.between(row.start, row.stop!!).toMillis(), 1_000).coerceAtLeast(0)
        awaitTotal("timer_totals_today", settings.formatDuration(rows.filter { time.local(it.start).toLocalDate() == today }.sumOf(::seconds)))
        awaitTotal("timer_totals_week", settings.formatDuration(rows.filter { time.local(it.start).toLocalDate() in week..today }.sumOf(::seconds)))
    }
    private fun awaitTotal(tag: String, text: String) {
        compose.waitUntil("$tag shows $text", 5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true)).fetchSemanticsNodes().size == 1
        }
    }
    private class MutableClock(@Volatile var at: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = at
    }
}
