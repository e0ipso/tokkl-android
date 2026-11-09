package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The reports model's summary on a Tokkl server whose account has a role, so the workspace is a
 * candidate for Tokkl's app report, which a [TestReportsServer] answers. How the request goes and
 * how its answer reads is AppSummaryServerTest's. The account is at UTC+14 with weeks from
 * Wednesday.
 */
class AppSummaryViewModelTest {

    private val server = TestReportsServer()
    private val io = QueuedDispatcher()
    private lateinit var route: TestReportsRoute
    private val model get() = route.model
    private lateinit var screen: TestScreen
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private val app = TestApp(scheduler)

    private val settings = MutableStateFlow<AccountSettings?>(
        AccountSettings("Pacific/Kiritimati", DayOfWeek.WEDNESDAY, timeResolutionMinutes = null),
    )

    /** What the summary answers, 7317 seconds until a test changes it. */
    @Volatile
    private var summary: (ReportCall.Summary) -> MockResponse = { appSummary(it.range, 7317) }

    @Before
    fun setUp() {
        server.workspace = WORKSPACE
        server.summaryAnswer = { summary(it) }
        route = TestReportsRoute(scope) { choices, saved, own ->
            reportsViewModel(
                accountSettings = settings,
                repository = server.repository,
                scope = own,
                io = io,
                clock = CLOCK,
                app = app.app,
                choices = choices,
                saved = saved,
            )
        }
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        screen.leave()
        server.close()
    }

    @Test
    fun accountTimezoneAndWeekStartSelectTheAppReportDates() {
        screen.show()
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()
        assertEquals(
            "the app report picks the workspace itself",
            ReportCall.Summary(null, range("2026-10-21", "2026-10-27"), ReportFilters()),
            server.summaries.single(),
        )
        assertEquals("the server's summary as it sent it", summaryOf(range("2026-10-21", "2026-10-27"), 7317), report())
        assertTrue("the summary alone", server.detailedPages.isEmpty())
    }

    @Test
    fun foregroundRefreshDropsOldValuesAndUsesTheNewServerAnswer() {
        screen.show()
        io.runAll()
        val first = model.state.value
        screen.hide()
        screen.show()
        assertSame("a rotation asks nothing", first, model.state.value)
        assertEquals(1, server.summaries.size)
        summary = { appSummary(it.range, 9001) }
        screen.hide()
        app.trip()
        screen.show()
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()
        assertEquals(9001L, report().totals.seconds)
        assertEquals(2, server.summaries.size)
    }

    @Test
    fun latestRangeAndFiltersCancelQueuedLoads() {
        screen.show()
        model.select(RangePreset.TODAY)
        val filters = ReportFilters(tags = listOf(null, 4), tagsNot = true)
        model.applyFilters(filters)
        io.runAll()
        val asked = server.summaries.single()
        assertEquals(range("2026-10-21", "2026-10-21"), asked.range)
        assertEquals(filters, asked.filters)
        assertEquals(7317L, report().totals.seconds)
    }

    @Test
    fun closeCancelsPendingLoadsAndReopenReadsTheAccountAgain() {
        screen.show()
        reopen()
        io.runAll()
        assertEquals(ReportsState.Closed, model.state.value)
        assertTrue(server.summaries.isEmpty())
        screen.show()
        io.runAll()
        assertEquals(7317L, report().totals.seconds)
        reopen()
        screen.show()
        io.runAll()
        assertEquals(2, server.workspaceCalls.size)
    }

    /**
     * The route leaves the back stack while the server is answering the summary, which it holds
     * until the test lets it go, and the model drops the late answer.
     */
    @Test
    fun closingWhileTheServerIsAnsweringCannotPublishItsLateSummary() {
        val arrived = CountDownLatch(1)
        val answer = CountDownLatch(1)
        summary = {
            arrived.countDown()
            answer.await(WAIT_SECONDS, TimeUnit.SECONDS)
            appSummary(it.range, 7317)
        }
        // The request blocks a thread of its own while the server holds the answer.
        val requests = Executors.newSingleThreadExecutor()
        val liveScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val liveRoute = TestReportsRoute(liveScope) { choices, saved, own ->
            reportsViewModel(
                accountSettings = settings,
                repository = server.repository,
                scope = own,
                io = requests.asCoroutineDispatcher(),
                clock = CLOCK,
                app = app.app,
                choices = choices,
                saved = saved,
            )
        }
        val live = liveRoute.model
        val liveScreen = TestScreen(live.page, liveScope, scheduler)
        try {
            liveScreen.show()
            assertTrue("the summary is on its way", arrived.await(WAIT_SECONDS, TimeUnit.SECONDS))
            val loading = live.state.value
            assertTrue("loading: $loading", loading is ReportsState.Loading)

            // The route leaves the back stack, which cancels the view model's scope and its load.
            liveRoute.leave()
            answer.countDown()
            requests.shutdown()
            assertTrue(requests.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS))
            scheduler.runCurrent()

            assertSame("the late summary does not show", loading, live.state.value)
            assertEquals(1, server.summaries.size)
        } finally {
            answer.countDown()
            requests.shutdownNow()
            liveScreen.leave()
            liveScope.cancel()
        }
    }

    @Test
    fun failureRefreshShowsNoStaleNumbersAndRetryCanRecover() {
        screen.show()
        io.runAll()
        // Not a report.
        summary = { json("{}") }
        model.retry()
        io.runAll()
        assertEquals(ReportFailure.Unexpected(null), (model.state.value as ReportsState.Failed).failure)
        assertTrue("no other report stands in", server.detailedPages.isEmpty())
        summary = { appSummary(it.range, 7317) }
        model.retry()
        io.runAll()
        assertEquals(7317L, report().totals.seconds)
    }

    @Test
    fun networkRecoveryRetriesSummaryButAuthAndQuotaRemainVisible() {
        screen.show()
        io.runAll()
        for ((error, expected) in listOf(
            status(503) to ReportFailure.Unreachable,
            status(403) to ReportFailure.AuthFailed,
            status(429, "Retry-After" to "17") to ReportFailure.Unexpected(429),
        )) {
            summary = { error }
            model.retry()
            io.runAll()
            assertEquals(expected, (model.state.value as ReportsState.Failed).failure)
            val count = server.summaries.size
            app.reconnect()
            io.runAll()
            assertEquals(count + if (expected == ReportFailure.Unreachable) 1 else 0, server.summaries.size)
        }
        assertTrue("no other report stands in", server.detailedPages.isEmpty())
    }

    @Test
    fun switchingTabsKeepsDetailedAndWeeklyOnTheirOwnReports() {
        server.detailedAnswer = { detailedPage(emptyList(), nextRow = null) }
        server.weeklyAnswer = { json("[]") }
        screen.show()
        io.runAll()
        model.selectTab(ReportTab.DETAILED)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is DetailedReport)
        assertEquals(1, server.detailedPages.size)
        model.selectTab(ReportTab.WEEKLY)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is WeeklyReport)
        assertEquals(1, server.weeklies.size)
        assertEquals(1, server.summaries.size)
    }

    private fun report() = (model.state.value as ReportsState.Loaded).report as SummaryReport

    /** The route leaves the back stack and comes back, with a screen that has not shown yet. */
    private fun reopen() {
        screen.leave()
        route.reopen()
        screen = TestScreen(model.page, scope, scheduler)
    }

    private companion object {
        /** A Tokkl workspace with rounding, a candidate for the app report. */
        val WORKSPACE = reportWorkspace(rounding = 1, roundingMinutes = 15, appSummaryCandidate = true)

        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneOffset.UTC)

        /** How long a test waits for the server or the request's thread. */
        const val WAIT_SECONDS = 5L
    }
}
