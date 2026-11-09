package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The reports screen's model over a [FakeReportsRepository] that stands in for the server, for
 * an account at UTC+14 whose week starts on Wednesday. The clock reads Tuesday 20 October in UTC,
 * which is already Wednesday the 21st for the account, so a range taken from the phone's day or
 * from Monday weeks would differ.
 *
 * The model's work off the main thread waits in [io] until a test runs it, so a test sees what
 * the screen shows while the answer is on its way.
 *
 * The screen reads the page as the Reports route does ([TestScreen]), and the app goes to the
 * background and back as ProcessLifecycleOwner reports it ([TestApp]). ReportsForegroundTest checks
 * the rotation on the emulator; these tests check the model's half.
 */
class ReportsViewModelTest {

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
    private val app = TestApp(scheduler)

    @Before
    fun setUp() {
        repository.workspaceAnswer = { ApiResult.Success(WORKSPACE) }
        repository.summaryAnswer = { ApiResult.Success(summaryOf(it.range, seconds = 3600)) }
        route = TestReportsRoute(scope) { choices, saved, own ->
            reportsViewModel(
                accountSettings = settings,
                repository = repository,
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

    @Test
    fun opensOnThisWeekInTheAccountsTimezoneAndWeekStart() {
        screen.show()
        val thisWeek = RangeSelection(range("2026-10-21", "2026-10-27"), RangePreset.THIS_WEEK)
        assertEquals("no numbers before the answer", ReportsState.Loading(thisWeek), model.state.value)

        io.runAll()

        val loaded = loaded()
        assertEquals(thisWeek, loaded.selection)
        assertEquals(summaryOf(thisWeek.range, seconds = 3600), loaded.report)
        assertEquals(
            ReportCall.Summary(WORKSPACE, thisWeek.range, ZoneId.of(ZONE), DayOfWeek.WEDNESDAY, ReportFilters()),
            repository.summaries.single(),
        )
    }

    @Test
    fun widgetTodayReplacesARetainedWeeklyTabAndItsFiltersWithTheAccountDay() {
        screen.show()
        io.runAll()
        model.applyFilters(ReportFilters(billable = BillableFilter.BILLABLE))
        model.selectTab(ReportTab.WEEKLY)
        route.choices.openToday()
        assertEquals(ReportTab.SUMMARY, model.chosen.value.tab)
        assertEquals(ReportFilters(), model.chosen.value.filters)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-21", "2026-10-21"), RangePreset.TODAY), loaded().selection)
        assertSearched("2026-10-21", "2026-10-21")
        assertEquals("the latest search has no filters", ReportFilters(), repository.summaries.last().filters)
        assertTrue("the weekly tab asked for nothing", repository.weeklies.isEmpty())
        screen.leave()
        route.reopen()
        route.choices.openToday()
        screen = TestScreen(model.page, scope, scheduler)
        screen.show()
        io.runAll()
        assertEquals("the tap asks before the page shows, which asks nothing", 3, repository.summaries.size)
        assertEquals(RangePreset.TODAY, loaded().selection.preset)
    }

    @Test
    fun aWidgetTapBeforeThePageOpensShowsTodayOnce() {
        route.choices.selectTab(ReportTab.WEEKLY)
        route.choices.applyFilters(ReportFilters(billable = BillableFilter.BILLABLE))
        route.leave()
        route.choices.openToday()
        route.reopen()
        screen = TestScreen(model.page, scope, scheduler)
        screen.show()
        io.runAll()

        assertEquals(ReportTab.SUMMARY, model.chosen.value.tab)
        assertEquals(ReportFilters(), model.chosen.value.filters)
        assertEquals(RangeSelection(range("2026-10-21", "2026-10-21"), RangePreset.TODAY), loaded().selection)
        assertEquals(1, repository.summaries.size)
    }

    @Test
    fun presetsAndShiftsStartFromTheAccountsToday() {
        screen.show()
        io.runAll()

        model.select(RangePreset.TODAY)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-21", "2026-10-21"), RangePreset.TODAY), loaded().selection)
        assertSearched("2026-10-21", "2026-10-21")

        model.select(RangePreset.LAST_WEEK)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-14", "2026-10-20"), RangePreset.LAST_WEEK), loaded().selection)

        model.shift(1)
        io.runAll()
        assertEquals(
            "a shifted range names no preset",
            RangeSelection(range("2026-10-21", "2026-10-27"), null),
            loaded().selection,
        )

        model.shift(1)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-28", "2026-11-03"), null), loaded().selection)
        assertSearched("2026-10-28", "2026-11-03")
        assertEquals(5, repository.summaries.size)
    }

    @Test
    fun aTripToTheBackgroundAsksOnceForTheRangeOnScreen() {
        screen.show()
        io.runAll()
        model.shift(-1)
        io.runAll()
        val shown = loaded()

        screen.hide()
        app.foreground.value = false
        scheduler.runCurrent()
        assertSame("a stop alone asks nothing", shown, model.state.value)
        app.foreground.value = true
        scheduler.runCurrent()
        screen.show()

        assertEquals(
            "no old numbers while the answer is on its way",
            ReportsState.Loading(shown.selection),
            model.state.value,
        )
        io.runAll()
        assertEquals(shown.selection, loaded().selection)
        assertEquals(3, repository.summaries.size)
        assertSearched("2026-10-14", "2026-10-20")

        val reloaded = model.state.value
        screen.hide()
        screen.show()
        assertSame("a rotation asks nothing", reloaded, model.state.value)
        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(3, repository.summaries.size)
    }

    @Test
    fun aTripWhileTheScreenIsClosedAsksNothing() {
        app.trip()
        screen.show()
        io.runAll()

        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(1, repository.summaries.size)
    }

    /**
     * Another activity, such as the Pomodoro alarm or the tile's dialog, hides the screen for
     * longer than the page state is shared. The range, the tab and the filters stay, and the
     * screen asks again for them when it shows.
     */
    @Test
    fun aScreenHiddenForLongerKeepsItsChoicesAndAsksAgainForThem() {
        screen.show()
        io.runAll()
        val filters = ReportFilters(billable = BillableFilter.BILLABLE)
        model.applyFilters(filters)
        model.select(RangePreset.LAST_WEEK)
        io.runAll()
        val shown = loaded()

        screen.leave()
        assertSame("hiding asks nothing", shown, model.state.value)
        screen.show()

        assertEquals(ReportsState.Loading(shown.selection), model.state.value)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-14", "2026-10-20"), RangePreset.LAST_WEEK), loaded().selection)
        assertSearched("2026-10-14", "2026-10-20")
        assertEquals(filters, model.chosen.value.filters)
        assertEquals("the search asks with the filters", filters, repository.summaries.last().filters)
        assertEquals(3, repository.summaries.size)
    }

    @Test
    fun aScreenThatWentForgetsItsReportAndOpensOnThisWeekAgain() {
        repository.detailedAnswer = { ApiResult.Success(DetailedPage(emptyList(), nextRow = null)) }
        screen.show()
        io.runAll()
        model.selectTab(ReportTab.DETAILED)
        model.shift(-1)
        io.runAll()
        screen.leave()
        route.reopen()
        screen = TestScreen(model.page, scope, scheduler)
        assertEquals(ReportsState.Closed, model.state.value)

        app.trip()
        assertEquals("a closed screen loads nothing", ReportsState.Closed, model.state.value)

        screen.show()
        io.runAll()
        assertEquals(RangePreset.THIS_WEEK, loaded().selection.preset)
        assertEquals("the tab stays", ReportTab.DETAILED, model.chosen.value.tab)
        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals("the summary, last week's details, then this week's", 3, searches())
        assertEquals(range("2026-10-21", "2026-10-27"), repository.detailedPages.last().range)
    }

    @Test
    fun aTripWithoutTheServerShowsWhyAndNoNumbers() {
        screen.show()
        io.runAll()
        val shown = loaded()
        repository.summaryAnswer = { ApiResult.Failure(ApiError.Network(IOException("connection refused"))) }

        screen.hide()
        app.trip()
        screen.show()
        io.runAll()

        assertEquals(ReportsState.Failed(shown.selection, ReportFailure.Unreachable), model.state.value)
    }

    @Test
    fun theNetworksReturnAsksAgainForAReportThatGotNoAnswer() {
        // The 503 of a proxy whose server is down.
        repository.summaryAnswer = { ApiResult.Failure(ApiError.UnexpectedStatus(503)) }
        screen.show()
        io.runAll()
        val failed = model.state.value
        assertTrue("no answer: $failed", failed is ReportsState.Failed && failed.failure == ReportFailure.Unreachable)

        repository.summaryAnswer = { ApiResult.Success(summaryOf(it.range, seconds = 3600)) }
        app.reconnect()
        assertEquals(ReportsState.Loading((failed as ReportsState.Failed).selection), model.state.value)
        io.runAll()

        assertEquals(failed.selection, loaded().selection)
        assertEquals("the failed search, then the one the network's return asked for", 2, repository.summaries.size)
    }

    @Test
    fun theNetworksReturnAsksNothingForAShownOrClosedReport() {
        app.reconnect()
        assertEquals("a closed screen loads nothing", ReportsState.Closed, model.state.value)

        screen.show()
        io.runAll()
        val shown = loaded()
        app.reconnect()

        assertSame(shown, model.state.value)
        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(1, repository.summaries.size)
    }

    private fun loaded(): ReportsState.Loaded {
        val state = model.state.value
        assertTrue("loaded: $state", state is ReportsState.Loaded)
        return state as ReportsState.Loaded
    }

    /** The latest summary asked for [from] to [to]. */
    private fun assertSearched(from: String, to: String) {
        assertEquals(range(from, to), repository.summaries.last().range)
    }

    /** The searches the server would have run: one per summary and one per detailed page. */
    private fun searches(): Int = repository.summaries.size + repository.detailedPages.size

    private companion object {
        /** UTC+14 all year. */
        const val ZONE = "Pacific/Kiritimati"

        /** The account's workspace on a server without Tokkl's app report. */
        val WORKSPACE = reportWorkspace()

        /** Tuesday 20 October in UTC, Wednesday the 21st at UTC+14. */
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneOffset.UTC)
    }
}
