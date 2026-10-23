package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.capabilities.Capabilities
import java.io.File
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Clock
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A range and the preset it came from, null for a custom range or one stepped to. */
data class RangeSelection(val range: ReportRange, val preset: RangePreset?)

/** The reports, one tab each, in the web app's order. */
enum class ReportTab {
    SUMMARY,
    DETAILED,
    WEEKLY,
}

/** What one tab shows once the server answers: a [SummaryReport], a [DetailedReport] or a [WeeklyReport]. */
sealed interface Report

/** What the reports screen shows. No state carries numbers from another range or an earlier answer. */
sealed interface ReportsState {

    /** The screen is closed, and nothing is loaded or kept. */
    data object Closed : ReportsState

    /**
     * The phone cannot turn the account's timezone into dates, so it cannot pick a range.
     * [timeZone] is the account's zone name, or null when no account settings are stored.
     */
    data class NoAccountTime(val timeZone: String?) : ReportsState

    data class Loading(val selection: RangeSelection) : ReportsState

    data class Loaded(val selection: RangeSelection, val report: Report) : ReportsState

    data class Failed(val selection: RangeSelection, val failure: ReportFailure) : ReportsState
}

/** The range this state is about, or null when there is none. */
internal fun ReportsState.selection(): RangeSelection? = when (this) {
    is ReportsState.Loading -> selection
    is ReportsState.Loaded -> selection
    is ReportsState.Failed -> selection
    ReportsState.Closed, is ReportsState.NoAccountTime -> null
}

/** Why there is no report. */
sealed interface ReportFailure {

    /** No answer: the phone is offline, or the server or the proxy in front of it is down. */
    data object Unreachable : ReportFailure

    /** The server refused the stored API token, or the phone could not read it. */
    data object AuthFailed : ReportFailure

    /** The app sends nothing to the server until the user answers its trust prompt. */
    data object TrustRefused : ReportFailure

    /** Any other answer. [status] is the HTTP status, or null when the body was not a report. */
    data class Unexpected(val status: Int?) : ReportFailure
}

/**
 * The reports screen's state. Each time the screen opens, the tab, the range or the filters change
 * or the app comes back to the foreground, it asks the configured server for the report, and it
 * never asks on a timer. Every tab's request carries the filters. The detailed tab asks for its
 * next page when the list nears its end, and the weekly tab always shows one whole week from the
 * account's week start. Reports live in memory while the screen is open; [close] drops them.
 * Explicit CSV sharing uses a separate bounded private export area.
 * When a request fails, the screen shows why and no numbers or entries, even those of pages that
 * loaded before.
 *
 * Call every function on the main thread.
 *
 * @param accountSettings the account's timezone, week start and duration format.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 * @param filterOptions the projects, clients and tags the filter sheet offers, from the phone's cache.
 */
class ReportsModel(
    val accountSettings: StateFlow<AccountSettings?>,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
    val filterOptions: StateFlow<FilterOptions> = MutableStateFlow(FilterOptions.NONE),
    val exportCapabilities: StateFlow<Capabilities> = MutableStateFlow(Capabilities.NONE),
    private val exporter: ReportExportController? = null,
) {

    val exportState: StateFlow<ReportExportState> = exporter?.state ?: MutableStateFlow(ReportExportState.Idle)

    /** Snapshot every export setting now, independently of the rows loaded by Detailed. */
    fun exportCsv() {
        val shown = state.value as? ReportsState.Loaded ?: return
        val kind = ReportCsvKind.of(tab.value) ?: return
        if (!exportCapabilities.value.supports(kind) || accountTime() == null) return
        val settings = accountSettings.value ?: return
        val knownWorkspace = workspace ?: return
        val selectedFilters = filters.value.let {
            it.copy(projects = it.projects.toList(), clients = it.clients.toList(), tags = it.tags.toList())
        }
        exporter?.start(ReportCsvRequest(kind, shown.selection.range, selectedFilters, knownWorkspace, settings.durationFormat))
    }

    fun launchExportShare(launch: (File) -> Unit) { exporter?.launchShare(launch) }
    fun exportShareFinished() { exporter?.shareFinished() }
    fun exportNoShareTarget() { exporter?.noShareTarget() }
    fun exportShareFailed() { exporter?.shareFailed() }

    private val mutableState = MutableStateFlow<ReportsState>(ReportsState.Closed)
    val state: StateFlow<ReportsState> = mutableState.asStateFlow()

    private val mutableTab = MutableStateFlow(ReportTab.SUMMARY)

    /** The tab on screen. It stays when the screen closes and opens again. */
    val tab: StateFlow<ReportTab> = mutableTab.asStateFlow()

    private val mutableFilters = MutableStateFlow(ReportFilters())

    /** The filters every tab's report applies. They stay when the screen closes and opens again, as the tab does. */
    val filters: StateFlow<ReportFilters> = mutableFilters.asStateFlow()

    /** The account's workspace and rounding, read once each time the screen opens. */
    private var workspace: ReportWorkspace? = null

    private var loading: Job? = null

    /** The app went to the background with the screen open, so what it shows may be old. */
    private var backgrounded = false

    /**
     * Opens the screen on this week. Does nothing while the screen is open, so a configuration
     * change keeps the report it shows instead of asking the server again.
     */
    fun open() {
        if (state.value == ReportsState.Closed) select(RangePreset.THIS_WEEK)
    }

    /** Loads [preset]'s range around today in the account's timezone. */
    fun select(preset: RangePreset) {
        val time = accountTime() ?: return showNoAccountTime()
        load(RangeSelection(preset.range(time.today(), time.firstDayOfWeek), preset))
    }

    /** Loads [range], which may span at most [MAX_RANGE_DAYS] days. */
    fun selectCustom(range: ReportRange) {
        require(range.days <= MAX_RANGE_DAYS) { "the Reports API accepts at most $MAX_RANGE_DAYS days" }
        load(RangeSelection(range, preset = null))
    }

    /** Loads the range before the current one for a negative [direction], the one after for a positive one. */
    fun shift(direction: Int) {
        val current = selection() ?: return
        load(RangeSelection(current.range.shift(direction), preset = null))
    }

    /**
     * Shows [tab] and asks for its report of the range on screen. The weekly tab takes the week
     * that [ReportRange.week] picks from that range.
     */
    fun selectTab(tab: ReportTab) {
        if (tab == mutableTab.value) return
        mutableTab.value = tab
        selection()?.let(::load)
    }

    /** Applies [filters] to every tab and asks for the report on screen again when they change. */
    fun applyFilters(filters: ReportFilters) {
        if (filters == mutableFilters.value) return
        mutableFilters.value = filters
        selection()?.let(::load)
    }

    /**
     * Asks for the detailed report's next page, at the row the server named for it. Does nothing
     * while a page is on its way, after the last page, or on another tab.
     */
    fun loadMore() {
        val shown = state.value as? ReportsState.Loaded ?: return
        val report = shown.report as? DetailedReport ?: return
        val next = report.nextRow ?: return
        val known = workspace ?: return
        if (report.loadingMore) return
        mutableState.value = shown.copy(report = report.copy(loadingMore = true))
        loading = scope.launch {
            // A cancelled page drops its answer, as a cancelled load does.
            mutableState.value = withContext(io) { nextPage(shown.selection, known, report, next, mutableFilters.value) }
        }
    }

    /** Asks for the current range again, or for this week when the account's timezone was unknown. */
    fun retry() {
        val current = selection()
        if (current == null) select(RangePreset.THIS_WEEK) else load(current)
    }

    /**
     * The app went to the background with the screen open. Call it when the activity stops, but
     * not for a configuration change, which is no trip to the background.
     */
    fun wentToBackground() {
        if (state.value != ReportsState.Closed) backgrounded = true
    }

    /**
     * The app came back to the foreground. After [wentToBackground], asks again for the range on
     * screen, and shows no numbers until the answer comes, so a phone that lost its connection in
     * the background says that reports need the server instead of showing old numbers.
     */
    fun cameToForeground() {
        if (!backgrounded) return
        backgrounded = false
        retry()
    }

    /**
     * The network came back while the app is in the foreground. A report that failed for want of
     * an answer asks again; any other state asks nothing.
     */
    fun reconnected() {
        val failed = state.value as? ReportsState.Failed ?: return
        if (failed.failure == ReportFailure.Unreachable) load(failed.selection)
    }

    /** Closes the screen: stops waiting for an answer and forgets the report and the workspace. */
    fun close() {
        exporter?.cancel()
        loading?.cancel()
        loading = null
        workspace = null
        backgrounded = false
        mutableState.value = ReportsState.Closed
    }

    /** Times in the account's timezone, or null without settings or when the phone cannot resolve the zone. */
    fun accountTime(): AccountTime? = accountSettings.value?.time(clock)

    private fun selection(): RangeSelection? = state.value.selection()

    private fun showNoAccountTime() {
        loading?.cancel()
        mutableState.value = ReportsState.NoAccountTime(accountSettings.value?.timeZone)
    }

    private fun load(requested: RangeSelection) {
        val tab = mutableTab.value
        val filters = mutableFilters.value
        val selection = if (tab == ReportTab.WEEKLY) weekOf(requested) ?: return showNoAccountTime() else requested
        loading?.cancel()
        mutableState.value = ReportsState.Loading(selection)
        val known = workspace
        loading = scope.launch {
            // A cancelled load drops its answer: withContext throws once it returns.
            val (shown, read) = withContext(io) { fetch(selection, known, tab, filters) }
            workspace = read
            mutableState.value = shown
        }
    }

    /**
     * The week the weekly tab shows for [selection], with no preset when that changes its range,
     * or null when the phone cannot resolve the account's timezone.
     */
    private fun weekOf(selection: RangeSelection): RangeSelection? {
        val time = accountTime() ?: return null
        val week = selection.range.week(time.today(), time.firstDayOfWeek)
        return if (week == selection.range) selection else RangeSelection(week, preset = null)
    }

    /** What [tab] shows for [selection] with [filters], and the workspace, [known] or read from the server when null. */
    private fun fetch(
        selection: RangeSelection,
        known: ReportWorkspace?,
        tab: ReportTab,
        filters: ReportFilters,
    ): Pair<ReportsState, ReportWorkspace?> {
        val time = accountTime() ?: return ReportsState.NoAccountTime(accountSettings.value?.timeZone) to known
        val api = client() ?: return ReportsState.Failed(selection, ReportFailure.AuthFailed) to known
        val server = ReportsServer(api)
        val workspace = known ?: when (val answer = server.workspace()) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> return ReportsState.Failed(selection, failureOf(answer.error)) to null
        }
        val shown = when (tab) {
            ReportTab.SUMMARY -> when (val answer = server.entries(workspace, selection.range, time.zone, filters)) {
                is ApiResult.Success -> ReportsState.Loaded(selection, SummaryReport.of(answer.value, selection.range, time.firstDayOfWeek))
                is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
            }
            ReportTab.DETAILED -> when (val answer = server.detailedPage(workspace, selection.range, time.zone, firstRow = 1, filters)) {
                is ApiResult.Success -> ReportsState.Loaded(selection, DetailedReport(answer.value.entries, answer.value.nextRow))
                is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
            }
            ReportTab.WEEKLY -> when (val answer = server.weekly(workspace, selection.range.from, filters)) {
                is ApiResult.Success -> ReportsState.Loaded(selection, WeeklyReport.of(selection.range.from, answer.value))
                is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
            }
        }
        return shown to workspace
    }

    /** [report] with the page that starts at [next] added, or why there is none. */
    private fun nextPage(
        selection: RangeSelection,
        workspace: ReportWorkspace,
        report: DetailedReport,
        next: Int,
        filters: ReportFilters,
    ): ReportsState {
        val time = accountTime() ?: return ReportsState.NoAccountTime(accountSettings.value?.timeZone)
        val api = client() ?: return ReportsState.Failed(selection, ReportFailure.AuthFailed)
        return when (val answer = ReportsServer(api).detailedPage(workspace, selection.range, time.zone, next, filters)) {
            is ApiResult.Success -> ReportsState.Loaded(selection, DetailedReport(report.entries + answer.value.entries, answer.value.nextRow))
            is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
        }
    }

    private fun failureOf(error: ApiError): ReportFailure = reportFailureOf(error)
}

internal fun reportFailureOf(error: ApiError): ReportFailure = when (error) {
    is ApiError.Network -> ReportFailure.Unreachable
    // A reverse proxy answers these while the Tokkl server behind it is down.
    is ApiError.UnexpectedStatus -> if (error.status in setOf(502, 503, 504)) ReportFailure.Unreachable else ReportFailure.Unexpected(error.status)
    ApiError.AuthFailed -> ReportFailure.AuthFailed
    is ApiError.TrustRefused -> ReportFailure.TrustRefused
    is ApiError.RateLimited -> ReportFailure.Unexpected(error.status)
    is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> ReportFailure.Unexpected(null)
}
