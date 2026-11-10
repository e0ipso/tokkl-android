package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The Detailed and Weekly tabs of the reports model over a [TestReportsServer] that answers as
 * Tokkl does. The account is at UTC+14 with weeks from Wednesday, and the clock says Tuesday 20
 * October in UTC, already Wednesday 21 October in the account's zone. Calls run when [io] runs
 * them, so a test can look at the state while they wait. What each request sends and how each
 * answer reads is ReportsServerTest's.
 */
class ReportTabsViewModelTest {

    private val server = TestReportsServer()
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
    @Volatile
    private var laterPagesFail = false

    @Before
    fun setUp() {
        server.workspace = WORKSPACE
        server.summaryAnswer = { searchPage(seconds = 900) }
        server.detailedAnswer = { call ->
            when {
                call.firstRow == 1 -> detailedPage(listOf(NEWEST_ROW), nextRow = 51)
                laterPagesFail -> status(503)
                else -> detailedPage(listOf(RUNNING_ROW), nextRow = null)
            }
        }
        server.weeklyAnswer = { json(WEEKLY_ROWS) }
        route = TestReportsRoute(scope) { choices, saved, own ->
            reportsViewModel(
                settings,
                server.repository,
                own,
                io,
                Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneOffset.UTC),
                choices = choices,
                saved = saved,
            )
        }
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        server.close()
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
            ReportCall.Detailed(WORKSPACE, week("2026-10-21"), firstRow = 1, ReportFilters()),
            server.detailedPages.single(),
        )

        model.loadMore()
        assertTrue("the next page loads", detailed().loadingMore)
        model.loadMore()
        io.runAll()

        val second = detailed()
        assertEquals(listOf(NEWEST, RUNNING), second.entries)
        assertNull("the last page", second.nextRow)
        assertEquals(51, server.detailedPages.last().firstRow)
        assertEquals("the summary", 1, server.summaries.size)
        assertEquals("then two pages", 2, server.detailedPages.size)
        model.loadMore()
        assertTrue("nothing more to load", io.isEmpty())
    }

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
        assertEquals("Try again asks for the first page", 1, server.detailedPages.last().firstRow)
        model.selectTab(ReportTab.SUMMARY)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is SummaryReport)
        assertEquals(week("2026-10-21"), server.summaries.single().range)
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
            server.weeklies.last(),
        )
        assertEquals(listOf(5L, 4L, 3L, null), report.rows.map { it.id })
        assertEquals(listOf(900L, 0, 0, 0, 0, 0, 900), report.rows[1].seconds)
        assertEquals("#990099", report.rows[0].color)
        assertEquals(listOf(4500L, 900, 0, 0, 0, 0, 1800), report.dayTotals)
        assertEquals(7200L, report.total)
        assertEquals(LocalDate.parse("2026-10-21"), report.weekStart)

        model.shift(-1)
        io.runAll()
        assertEquals(LocalDate.parse("2026-10-14"), server.weeklies.last().weekStart)
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
        assertEquals(filters, model.chosen.value.filters)
        assertEquals(filters, server.summaries.last().filters)

        model.selectTab(ReportTab.DETAILED)
        io.runAll()
        model.loadMore()
        io.runAll()
        assertEquals(listOf(1, 51), server.detailedPages.takeLast(2).map { it.firstRow })
        assertEquals("both pages", listOf(filters, filters), server.detailedPages.takeLast(2).map { it.filters })

        model.selectTab(ReportTab.WEEKLY)
        io.runAll()
        assertEquals(filters, server.weeklies.last().filters)
        model.applyFilters(filters)
        assertTrue("the same filters ask nothing", io.isEmpty())

        screen.leave()
        route.reopen()
        screen = TestScreen(model.page, scope, scheduler)
        screen.show()
        io.runAll()
        assertEquals("the filters stay", filters, model.chosen.value.filters)
        assertEquals("the tab stays", ReportTab.WEEKLY, model.chosen.value.tab)
        assertEquals(filters, server.weeklies.last().filters)

        val nonBillable = ReportFilters(billable = BillableFilter.NON_BILLABLE)
        model.applyFilters(nonBillable)
        io.runAll()
        assertEquals(nonBillable, server.weeklies.last().filters)
        model.applyFilters(ReportFilters())
        io.runAll()
        assertEquals("no filters", ReportFilters(), server.weeklies.last().filters)
    }

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

        assertEquals(filters, model.chosen.value.filters)
        assertEquals(ReportTab.WEEKLY, model.chosen.value.tab)
        assertEquals(filters, server.weeklies.last().filters)
    }

    @Test
    fun theRangeComesBackWhenANewProcessRestoresThePage() {
        screen.show()
        io.runAll()
        model.shift(-1)
        io.runAll()
        val before = RangeSelection(week("2026-10-14"), preset = null)
        assertEquals(before, model.state.value.selection())
        model.select(RangePreset.LAST_MONTH)
        io.runAll()
        val shown = model.state.value.selection()!!
        screen.leave()

        route.restart()
        screen = TestScreen(model.page, scope, scheduler)
        screen.show()
        io.runAll()

        assertEquals(shown, model.state.value.selection())
        assertEquals(shown.range, server.summaries.last().range)
    }

    @Test
    fun aDayTapWaitingAtTheRestoreWinsOverTheSavedRange() {
        screen.show()
        io.runAll()
        model.shift(-1)
        io.runAll()
        screen.leave()

        route.restart(before = ReportChoices::openToday)
        screen = TestScreen(model.page, scope, scheduler)
        screen.show()
        io.runAll()

        assertEquals(RangePreset.TODAY, model.state.value.selection()?.preset)
    }

    @Test
    fun aDayTapWaitingAtTheRestoreWinsOverTheSavedFilters() {
        screen.show()
        io.runAll()
        model.applyFilters(ReportFilters(billable = BillableFilter.BILLABLE))
        model.selectTab(ReportTab.WEEKLY)
        io.runAll()
        screen.leave()

        route.restart(before = ReportChoices::openToday)

        assertEquals(ReportFilters(), model.chosen.value.filters)
        assertEquals(ReportTab.SUMMARY, model.chosen.value.tab)
    }

    @Test
    fun aWeeklyAnswerThatIsNotAWeekIsAnUnexpectedAnswer() {
        // A row of two days.
        server.weeklyAnswer = { json("""[{"project_id":1,"project_name":"x","seconds":[1,2]}]""") }
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

        /** [NEWEST] as Tokkl's detailed search sends it. */
        val NEWEST_ROW = detailedRow(10, "2026-10-21T00:30:00+14:00", "\"2026-10-21T01:30:00+14:00\"")

        /** [RUNNING] as Tokkl's detailed search sends it, in UTC. */
        val RUNNING_ROW = detailedRow(9, "2026-10-20T10:00:00+00:00", "null")

        /**
         * Two rows of Beta to merge, ties by time to sort by title, and time without a project,
         * whose color is in project_hex_color, as Tokkl's weekly report sends them.
         */
        const val WEEKLY_ROWS = """[
            {"project_id":null,"project_name":null,"project_color":null,"seconds":[0,0,0,0,0,0,900]},
            {"project_id":4,"project_name":"Beta","project_color":"#06aaf5","seconds":[900,0,0,0,0,0,0]},
            {"project_id":3,"project_name":"alpha","project_color":"#06aaf5","seconds":[0,900,0,0,0,0,0]},
            {"project_id":5,"project_name":"Big","project_hex_color":"#990099","seconds":[3600,0,0,0,0,0,0]},
            {"project_id":4,"project_name":"Beta","project_color":"#06aaf5","seconds":[0,0,0,0,0,0,900]}
        ]"""

        /** A detailed row of Web's Design task with one entry of 15 minutes, as Tokkl sends it. */
        fun detailedRow(id: Int, start: String, stop: String) = """{"project_id":3""" +
            ""","project_name":"Web","project_color":"#06aaf5","task_name":"Design","description":"d$id",""" +
            """"billable":true,"billable_amount_in_cents":null,"currency":"EUR","row_number":$id,""" +
            """"time_entries":[{"id":$id,"seconds":900,"start":"$start","stop":$stop,""" +
            """"at":"2026-10-20T10:00:00+00:00"}]}"""
    }
}
