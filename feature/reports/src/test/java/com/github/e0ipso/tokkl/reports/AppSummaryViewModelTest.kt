package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The reports model's summary on a Tokkl server whose account has a role, so the workspace is a
 * candidate for Tokkl's app report. A [FakeReportsRepository] stands in for the server; which
 * endpoint answers the summary, and how its answer reads, is ReportsServer's and
 * AppSummaryServerTest pins it. The account is at UTC+14 with weeks from Wednesday.
 */
class AppSummaryViewModelTest {

    private val repository = FakeReportsRepository()
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
    private var summary: (ReportCall.Summary) -> ApiResult<SummaryReport> = {
        ApiResult.Success(summaryOf(it.range, 7317))
    }

    @Before
    fun setUp() {
        repository.workspaceAnswer = { ApiResult.Success(WORKSPACE) }
        repository.summaryAnswer = { summary(it) }
        route = TestReportsRoute(scope) { choices, own ->
            reportsViewModel(
                accountSettings = settings,
                repository = repository,
                scope = own,
                io = io,
                clock = CLOCK,
                app = app.app,
                choices = choices,
            )
        }
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        screen.leave()
    }

    @Test
    fun accountTimezoneAndWeekStartSelectTheAppReportDates() {
        screen.show()
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()
        assertEquals(
            ReportCall.Summary(
                WORKSPACE,
                range("2026-10-21", "2026-10-27"),
                ZoneId.of("Pacific/Kiritimati"),
                DayOfWeek.WEDNESDAY,
                ReportFilters(),
            ),
            repository.summaries.single(),
        )
        assertEquals("the server's summary as it sent it", summaryOf(range("2026-10-21", "2026-10-27"), 7317), report())
        assertTrue("the summary alone", repository.detailedPages.isEmpty())
    }

    @Test
    fun foregroundRefreshDropsOldValuesAndUsesTheNewServerAnswer() {
        screen.show()
        io.runAll()
        val first = model.state.value
        screen.hide()
        screen.show()
        assertSame("a rotation asks nothing", first, model.state.value)
        assertEquals(1, repository.summaries.size)
        summary = { ApiResult.Success(summaryOf(it.range, 9001)) }
        screen.hide()
        app.trip()
        screen.show()
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()
        assertEquals(9001L, report().totals.seconds)
        assertEquals(2, repository.summaries.size)
    }

    @Test
    fun latestRangeAndFiltersCancelQueuedLoads() {
        screen.show()
        model.select(RangePreset.TODAY)
        val filters = ReportFilters(tags = listOf(null, 4), tagsNot = true)
        model.applyFilters(filters)
        io.runAll()
        val asked = repository.summaries.single()
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
        assertTrue(repository.summaries.isEmpty())
        screen.show()
        io.runAll()
        assertEquals(7317L, report().totals.seconds)
        reopen()
        screen.show()
        io.runAll()
        assertEquals(2, repository.workspaceCalls.size)
    }

    /**
     * The route leaves the back stack while the summary is on its way. The repository still
     * returns its answer, as a request that already reached the server would, and the model drops it.
     */
    @Test
    fun closingWhileTheServerIsAnsweringCannotPublishItsLateSummary() {
        val answer = CompletableDeferred<ApiResult<SummaryReport>>()
        repository.summaryAnswer = { withContext(NonCancellable) { answer.await() } }
        val liveScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val liveRoute = TestReportsRoute(liveScope) { choices, own ->
            reportsViewModel(
                accountSettings = settings,
                repository = repository,
                scope = own,
                io = UnconfinedTestDispatcher(scheduler),
                clock = CLOCK,
                app = app.app,
                choices = choices,
            )
        }
        val live = liveRoute.model
        val liveScreen = TestScreen(live.page, liveScope, scheduler)
        try {
            liveScreen.show()
            assertEquals("the summary is on its way", 1, repository.summaries.size)
            val loading = live.state.value
            assertTrue("loading: $loading", loading is ReportsState.Loading)

            // The route leaves the back stack, which cancels the view model's scope and its load.
            liveRoute.leave()
            answer.complete(ApiResult.Success(summaryOf(range("2026-10-21", "2026-10-27"), 7317)))
            scheduler.runCurrent()

            assertSame("the late summary does not show", loading, live.state.value)
            assertEquals(1, repository.summaries.size)
        } finally {
            answer.cancel()
            liveScreen.leave()
            liveScope.cancel()
        }
    }

    @Test
    fun failureRefreshShowsNoStaleNumbersAndRetryCanRecover() {
        screen.show()
        io.runAll()
        summary = { ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("not a report"))) }
        model.retry()
        io.runAll()
        assertEquals(ReportFailure.Unexpected(null), (model.state.value as ReportsState.Failed).failure)
        assertTrue("no other report stands in", repository.detailedPages.isEmpty())
        summary = { ApiResult.Success(summaryOf(it.range, 7317)) }
        model.retry()
        io.runAll()
        assertEquals(7317L, report().totals.seconds)
    }

    @Test
    fun networkRecoveryRetriesSummaryButAuthAndQuotaRemainVisible() {
        screen.show()
        io.runAll()
        for ((error, expected) in listOf(
            ApiError.UnexpectedStatus(503) to ReportFailure.Unreachable,
            ApiError.AuthFailed to ReportFailure.AuthFailed,
            ApiError.RateLimited(retryAfterSeconds = 17) to ReportFailure.Unexpected(429),
        )) {
            summary = { ApiResult.Failure(error) }
            model.retry()
            io.runAll()
            assertEquals(expected, (model.state.value as ReportsState.Failed).failure)
            val count = repository.summaries.size
            app.reconnect()
            io.runAll()
            assertEquals(count + if (expected == ReportFailure.Unreachable) 1 else 0, repository.summaries.size)
        }
        assertTrue("no other report stands in", repository.detailedPages.isEmpty())
    }

    @Test
    fun switchingTabsKeepsDetailedAndWeeklyOnTheirOwnReports() {
        repository.detailedAnswer = { ApiResult.Success(DetailedPage(emptyList(), nextRow = null)) }
        repository.weeklyAnswer = { ApiResult.Success(emptyList()) }
        screen.show()
        io.runAll()
        model.selectTab(ReportTab.DETAILED)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is DetailedReport)
        assertEquals(1, repository.detailedPages.size)
        model.selectTab(ReportTab.WEEKLY)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is WeeklyReport)
        assertEquals(1, repository.weeklies.size)
        assertEquals(1, repository.summaries.size)
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
    }
}
