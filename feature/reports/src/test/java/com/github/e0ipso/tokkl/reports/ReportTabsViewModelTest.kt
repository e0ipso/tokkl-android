package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The Detailed and Weekly tabs of the reports model over a [FakeReportsRepository] that answers
 * as ReportsServer reads Tokkl's answers. The account is at UTC+14 with weeks from Wednesday, and
 * the clock says Tuesday 20 October in UTC, already Wednesday 21 October in the account's zone.
 * Calls run when [io] runs them, so a test can look at the state while they wait. What each
 * request sends and how each answer reads is ReportsServerTest's.
 */
class ReportTabsViewModelTest {

    private val repository = FakeReportsRepository()
    private val io = QueuedDispatcher()
    private val settings = MutableStateFlow<AccountSettings?>(
        AccountSettings(ZONE, DayOfWeek.WEDNESDAY, timeResolutionMinutes = null),
    )
    private lateinit var route: TestReportsRoute
    private val model get() = route.model
    private lateinit var screen: TestScreen
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))

    /** The detailed pages after the first fail as with a proxy's 503 while the server is down. */
    private var laterPagesFail = false

    @Before
    fun setUp() {
        repository.workspaceAnswer = { ApiResult.Success(WORKSPACE) }
        repository.summaryAnswer = { ApiResult.Success(summaryOf(it.range, seconds = 900)) }
        repository.detailedAnswer = { call ->
            when {
                call.firstRow == 1 -> ApiResult.Success(DetailedPage(listOf(NEWEST), nextRow = 51))
                laterPagesFail -> ApiResult.Failure(ApiError.UnexpectedStatus(503))
                else -> ApiResult.Success(DetailedPage(listOf(RUNNING), nextRow = null))
            }
        }
        repository.weeklyAnswer = { ApiResult.Success(WEEKLY) }
        route = TestReportsRoute(scope) { choices, saved, own ->
            reportsViewModel(
                settings,
                repository,
                own,
                io,
                Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneOffset.UTC),
                choices = choices,
                saved = saved,
            )
        }
        screen = TestScreen(model.page, scope, scheduler)
    }

    /**
     * The Detailed tab asks for the account's week from the first row, in the account's zone and
     * with the workspace the screen read. The next page loads from the row the server named, once
     * at a time, and nothing more loads after the last.
     */
    @Test
    fun theDetailedTabPagesAsTheServerSays() {
        screen.show()
        io.runAll()
        model.selectTab(ReportTab.DETAILED)
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()

        val first = detailed()
        assertEquals(51, first.nextRow)
        assertEquals(listOf(NEWEST), first.entries)
        assertEquals(
            ReportCall.Detailed(WORKSPACE, week("2026-10-21"), ZoneId.of(ZONE), firstRow = 1, ReportFilters()),
            repository.detailedPages.single(),
        )

        model.loadMore()
        assertTrue("the next page loads", detailed().loadingMore)
        model.loadMore()
        io.runAll()

        val second = detailed()
        assertEquals(listOf(10L, 9L), second.entries.map { it.id })
        assertNull("the last page", second.nextRow)
        assertEquals(51, repository.detailedPages.last().firstRow)
        assertEquals("the summary", 1, repository.summaries.size)
        assertEquals("then two pages", 2, repository.detailedPages.size)
        model.loadMore()
        assertTrue("nothing more to load", io.isEmpty())
    }

    /** A next page that fails shows why, with no rows, and Try again starts the tab over. */
    @Test
    fun aFailedNextPageShowsWhyAndTryAgainLoadsTheFirstPage() {
        model.selectTab(ReportTab.DETAILED)
        screen.show()
        io.runAll()
        laterPagesFail = true

        model.loadMore()
        io.runAll()
        assertEquals(ReportFailure.Unreachable, (model.state.value as ReportsState.Failed).failure)

        model.retry()
        io.runAll()
        assertEquals(1, detailed().entries.size)
        assertEquals("Try again asks for the first page", 1, repository.detailedPages.last().firstRow)
        model.selectTab(ReportTab.SUMMARY)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is SummaryReport)
        assertEquals(week("2026-10-21"), repository.summaries.single().range)
    }

    /**
     * The Weekly tab shows one week of the account's: any other range turns into its week, and
     * the rows sort as the web app sorts them, merged by project, time without a project last.
     */
    @Test
    fun theWeeklyTabAsksForTheAccountsWeekAndSortsAsTheWebApp() {
        screen.show()
        io.runAll()
        model.select(RangePreset.THIS_MONTH)
        io.runAll()
        model.selectTab(ReportTab.WEEKLY)

        val loading = model.state.value as ReportsState.Loading
        assertEquals(RangeSelection(week("2026-10-21"), null), loading.selection)
        io.runAll()
        val report = (model.state.value as ReportsState.Loaded).report as WeeklyReport
        assertEquals(
            ReportCall.Weekly(WORKSPACE, LocalDate.parse("2026-10-21"), ReportFilters()),
            repository.weeklies.last(),
        )
        assertEquals(listOf(5L, 4L, 3L, null), report.rows.map { it.id })
        assertEquals(listOf(900L, 0, 0, 0, 0, 0, 900), report.rows[1].seconds)
        assertEquals("#990099", report.rows[0].color)
        assertEquals(listOf(4500L, 900, 0, 0, 0, 0, 1800), report.dayTotals)
        assertEquals(7200L, report.total)
        assertEquals(LocalDate.parse("2026-10-21"), report.weekStart)

        model.shift(-1)
        io.runAll()
        assertEquals(LocalDate.parse("2026-10-14"), repository.weeklies.last().weekStart)
        assertEquals(week("2026-10-14"), loadedRange())
        model.select(RangePreset.TODAY)
        io.runAll()
        assertEquals(week("2026-10-21"), loadedRange())
        model.selectCustom(ReportRange(LocalDate.parse("2026-09-03"), LocalDate.parse("2026-09-20")))
        io.runAll()
        assertEquals("the week of the range's first day", week("2026-09-02"), loadedRange())

        model.selectTab(ReportTab.SUMMARY)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is SummaryReport)
        assertEquals("the summary keeps the week", week("2026-09-02"), loadedRange())
    }

    /**
     * The filters go with the summary, every page of the detailed report and the weekly report.
     * Changing them asks again for the tab on screen, the same filters ask nothing, and they stay
     * when the screen closes.
     */
    @Test
    fun theFiltersGoWithEveryTabsRequest() {
        screen.show()
        io.runAll()
        val filters = ReportFilters(
            projects = listOf(3L, null),
            clients = listOf(8L),
            tags = listOf(null),
            billable = BillableFilter.BILLABLE,
        )

        model.applyFilters(filters)
        assertTrue("the summary loads again", model.state.value is ReportsState.Loading)
        io.runAll()
        assertEquals(filters, model.filters.value)
        assertEquals(filters, repository.summaries.last().filters)

        model.selectTab(ReportTab.DETAILED)
        io.runAll()
        model.loadMore()
        io.runAll()
        assertEquals(listOf(1, 51), repository.detailedPages.takeLast(2).map { it.firstRow })
        assertEquals("both pages", listOf(filters, filters), repository.detailedPages.takeLast(2).map { it.filters })

        model.selectTab(ReportTab.WEEKLY)
        io.runAll()
        assertEquals(filters, repository.weeklies.last().filters)
        model.applyFilters(filters)
        assertTrue("the same filters ask nothing", io.isEmpty())

        screen.leave()
        route.reopen()
        screen = TestScreen(model.page, scope, scheduler)
        screen.show()
        io.runAll()
        assertEquals("the filters stay", filters, model.filters.value)
        assertEquals("the tab stays", ReportTab.WEEKLY, model.tab.value)
        assertEquals(filters, repository.weeklies.last().filters)

        val nonBillable = ReportFilters(billable = BillableFilter.NON_BILLABLE)
        model.applyFilters(nonBillable)
        io.runAll()
        assertEquals(nonBillable, repository.weeklies.last().filters)
        model.applyFilters(ReportFilters())
        io.runAll()
        assertEquals("no filters", ReportFilters(), repository.weeklies.last().filters)
    }

    /**
     * The process died with Reports on the back stack: the page it restores opens on the tab and
     * the filters it had, and asks for them.
     */
    @Test
    fun theTabAndTheFiltersComeBackWhenANewProcessRestoresThePage() {
        screen.show()
        io.runAll()
        val filters = ReportFilters(projects = listOf(3L), billable = BillableFilter.BILLABLE)
        model.applyFilters(filters)
        model.selectTab(ReportTab.WEEKLY)
        io.runAll()
        screen.leave()

        route.restart()
        screen = TestScreen(model.page, scope, scheduler)
        screen.show()
        io.runAll()

        assertEquals(filters, model.filters.value)
        assertEquals(ReportTab.WEEKLY, model.tab.value)
        assertEquals(filters, repository.weeklies.last().filters)
    }

    /** A widget's day tap that waits when the new process restores the page wins over what it saved. */
    @Test
    fun aDayTapWaitingAtTheRestoreWinsOverTheSavedFilters() {
        screen.show()
        io.runAll()
        model.applyFilters(ReportFilters(billable = BillableFilter.BILLABLE))
        model.selectTab(ReportTab.WEEKLY)
        io.runAll()
        screen.leave()

        route.restart(before = ReportChoices::openToday)

        assertEquals(ReportFilters(), model.filters.value)
        assertEquals(ReportTab.SUMMARY, model.tab.value)
    }

    /** A weekly answer ReportsServer could not read shows as an unexpected answer. */
    @Test
    fun aWeeklyAnswerThatIsNotAWeekIsAnUnexpectedAnswer() {
        repository.weeklyAnswer = {
            ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("a weekly row does not hold 7 days")))
        }
        model.selectTab(ReportTab.WEEKLY)
        screen.show()
        io.runAll()

        assertEquals(ReportFailure.Unexpected(null), (model.state.value as ReportsState.Failed).failure)
    }

    @Test
    fun weeklyRowsWithTheSameTimeSortByTitleIgnoringCase() {
        val rows = listOf(
            WeeklyRow(2, "beta", null, List(7) { 0L }),
            WeeklyRow(1, "Alpha", null, List(7) { 0L }),
            WeeklyRow(3, "alpha", null, List(7) { 0L }),
        )

        assertEquals(
            listOf("alpha", "Alpha", "beta"),
            WeeklyReport.of(LocalDate.parse("2026-10-21"), rows).rows.map { it.title },
        )
    }

    /** A range's week is the one its first day falls in, or this week when the range holds today. */
    @Test
    fun aRangesWeekStartsOnTheAccountsFirstDay() {
        val today = LocalDate.parse("2026-10-21")

        assertEquals(
            week("2026-10-21"),
            ReportRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")).week(today, DayOfWeek.WEDNESDAY),
        )
        assertEquals(
            week("2026-09-30"),
            ReportRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10")).week(today, DayOfWeek.WEDNESDAY),
        )
    }

    private fun detailed(): DetailedReport = (model.state.value as ReportsState.Loaded).report as DetailedReport

    private fun loadedRange(): ReportRange = (model.state.value as ReportsState.Loaded).selection.range

    private fun week(start: String): ReportRange = LocalDate.parse(start).let { ReportRange(it, it.plusDays(6)) }

    private companion object {
        const val ZONE = "Pacific/Kiritimati"

        /** The account's workspace, with rounding to the next quarter of an hour. */
        val WORKSPACE = reportWorkspace(rounding = 1, roundingMinutes = 15)

        /** The first detailed page's one entry, 15 minutes on Wednesday 21 October at UTC+14. */
        val NEWEST = DetailedEntry(
            id = 10,
            description = "d10",
            projectName = "Web",
            projectColor = "#06aaf5",
            taskName = "Design",
            start = ZonedDateTime.parse("2026-10-21T00:30:00+14:00[Pacific/Kiritimati]"),
            stop = ZonedDateTime.parse("2026-10-21T01:30:00+14:00[Pacific/Kiritimati]"),
            seconds = 900,
        )

        /** The second and last page's one entry, still running. */
        val RUNNING = DetailedEntry(
            id = 9,
            description = "d9",
            projectName = "Web",
            projectColor = "#06aaf5",
            taskName = "Design",
            start = ZonedDateTime.parse("2026-10-21T00:00:00+14:00[Pacific/Kiritimati]"),
            stop = null,
            seconds = 900,
        )

        /**
         * Two rows of Beta to merge, ties by time to sort by title, and time without a project, as
         * ReportsServer reads them.
         */
        val WEEKLY = listOf(
            WeeklyRow(null, "", null, listOf(0L, 0L, 0L, 0L, 0L, 0L, 900L)),
            WeeklyRow(4, "Beta", "#06aaf5", listOf(900L, 0L, 0L, 0L, 0L, 0L, 0L)),
            WeeklyRow(3, "alpha", "#06aaf5", listOf(0L, 900L, 0L, 0L, 0L, 0L, 0L)),
            WeeklyRow(5, "Big", "#990099", listOf(3600L, 0L, 0L, 0L, 0L, 0L, 0L)),
            WeeklyRow(4, "Beta", "#06aaf5", listOf(0L, 0L, 0L, 0L, 0L, 0L, 900L)),
        )
    }
}
