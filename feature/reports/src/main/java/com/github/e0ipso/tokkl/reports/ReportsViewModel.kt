package com.github.e0ipso.tokkl.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.capabilities.supports
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.platform.PageLoads
import com.github.e0ipso.tokkl.platform.stateInPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * The reports screen's state. Each time the screen opens, the tab, the range or the filters change
 * or the app comes back to the foreground, it asks the configured server for the report, and it
 * never asks on a timer. Every tab's request carries the filters. The detailed tab asks for its
 * next page when the list nears its end, and the weekly tab always shows one whole week from the
 * account's week start. It is the Reports route's own view model: the range and the reports live
 * in memory while the route is on the back stack, and go with it, while the tab and the filters
 * stay in [choices] for the next visit. Explicit CSV sharing uses a separate bounded private
 * export area. When a request fails, the screen shows why and no numbers or entries, even those of
 * pages that loaded before.
 *
 * Call every function on the main thread.
 *
 * @param choices the tab and the filters, which outlive the page, and a widget's ask for today.
 * @param accountSettings the account's timezone, week start and duration format.
 * @param repository the Reports API calls. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 * @param filterOptions the projects, clients and tags the filter sheet offers, from the phone's cache.
 * @param exporter the CSV export, which runs on [scope] and is cancelled when this view model is.
 * @param app the app's trips to the background, which ask again for the range on screen, and the
 *   network's returns, which ask again for a report that could not reach the server, while the
 *   screen shows ([stateInPage]).
 */
class ReportsViewModel(
    private val choices: ReportChoices,
    val accountSettings: StateFlow<AccountSettings?>,
    private val repository: ReportsRepository,
    scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock,
    val filterOptions: StateFlow<FilterOptions>,
    val exportCapabilities: StateFlow<Capabilities>,
    private val exporter: ReportExportController,
    app: AppForeground,
) : ViewModel(scope) {

    val exportState: StateFlow<ReportExportState> = exporter.state

    /** Snapshot every export setting now, independently of the rows loaded by Detailed. */
    fun exportCsv() {
        val shown = state.value as? ReportsState.Loaded ?: return
        val kind = tab.value.csvKind() ?: return
        if (!exportCapabilities.value.supports(kind) || accountTime() == null) return
        val settings = accountSettings.value ?: return
        val knownWorkspace = workspace ?: return
        val selectedFilters = filters.value.let {
            it.copy(projects = it.projects.toList(), clients = it.clients.toList(), tags = it.tags.toList())
        }
        exporter.start(
            ReportCsvRequest(kind, shown.selection.range, selectedFilters, knownWorkspace, settings.durationFormat),
        )
    }

    fun launchExportShare(launch: (File) -> Unit) {
        exporter.launchShare(launch)
    }
    fun exportShareFinished() {
        exporter.shareFinished()
    }
    fun exportNoShareTarget() {
        exporter.noShareTarget()
    }
    fun exportShareFailed() {
        exporter.shareFailed()
    }

    override fun onCleared() {
        exporter.cancel()
        super.onCleared()
    }

    private val mutableState = MutableStateFlow<ReportsState>(ReportsState.Closed)
    val state: StateFlow<ReportsState> = mutableState.asStateFlow()

    private val mutableTab = choices.tab

    /** The tab on screen. It stays when the screen closes and opens again. */
    val tab: StateFlow<ReportTab> = mutableTab.asStateFlow()

    private val mutableFilters = choices.filters

    /** The filters every tab's report applies. They stay when the screen closes and opens again, as the tab does. */
    val filters: StateFlow<ReportFilters> = mutableFilters.asStateFlow()

    /** Whether the screen has opened: from its first show, or a widget's day tap. */
    private var opened = false

    /** A widget's day tap asked for the report the screen is about to show, so that show asks nothing. */
    private var openedLoading = false

    /** What the screen shows, while it reads it. */
    val page: StateFlow<ReportsPageState> = combine(
        combine(state, tab, filters, ::Triple),
        accountSettings,
        filterOptions,
        exportState,
        exportCapabilities,
    ) { (report, shownTab, shownFilters), settings, options, export, capabilities ->
        ReportsPageState(
            report = report,
            tab = shownTab,
            settings = settings,
            time = settings?.time(clock),
            filters = shownFilters,
            filterOptions = options,
            exportSupported = shownTab.csvKind()?.let(capabilities::supports) == true,
            exportState = export,
        )
    }.stateInPage(
        viewModelScope,
        pageNow(),
        app,
        object : PageLoads {
            /**
             * Opens the screen on this week. A screen that shows again, after a trip to the
             * background or with another activity over it, asks again for the range on screen.
             */
            override fun shown() {
                when {
                    openedLoading -> openedLoading = false

                    opened -> retry()

                    else -> {
                        opened = true
                        select(RangePreset.THIS_WEEK)
                    }
                }
            }

            /**
             * Back from the background: asks again for the range on screen, and shows no numbers
             * until the answer comes, so a phone that lost its connection in the background says
             * that reports need the server instead of showing old numbers.
             */
            override fun returned() = retry()

            /**
             * The network came back. A report that failed for want of an answer asks again; any
             * other state asks nothing.
             */
            override fun reconnected() {
                val failed = state.value as? ReportsState.Failed ?: return
                if (failed.failure == ReportFailure.Unreachable) load(failed.selection)
            }
        },
    )

    /** The account's workspace and rounding, read once each time the screen opens. */
    private var workspace: ReportWorkspace? = null

    private var loading: Job? = null

    /** Opens the widget day report, which [ReportChoices.openToday] asked for with its tab and filters. */
    private fun openToday() {
        opened = true
        openedLoading = true
        select(RangePreset.TODAY)
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
        loading = viewModelScope.launch {
            // A cancelled page drops its answer, as a cancelled load does.
            mutableState.value = withContext(
                io,
            ) { nextPage(shown.selection, known, report, next, mutableFilters.value) }
        }
    }

    /** Asks for the current range again, or for this week when the account's timezone was unknown. */
    fun retry() {
        val current = selection()
        if (current == null) select(RangePreset.THIS_WEEK) else load(current)
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
        loading = viewModelScope.launch {
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

    /**
     * What [tab] shows for [selection] with [filters], and the workspace, [known] or read from the server when null.
     */
    private suspend fun fetch(
        selection: RangeSelection,
        known: ReportWorkspace?,
        tab: ReportTab,
        filters: ReportFilters,
    ): Pair<ReportsState, ReportWorkspace?> {
        val time = accountTime() ?: return ReportsState.NoAccountTime(accountSettings.value?.timeZone) to known
        val workspace = known ?: when (val answer = repository.workspace()) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> return ReportsState.Failed(selection, failureOf(answer.error)) to null
        }
        val shown = when (tab) {
            ReportTab.SUMMARY -> when (
                val answer = repository.summary(
                    workspace,
                    selection.range,
                    time.zone,
                    time.firstDayOfWeek,
                    filters,
                )
            ) {
                is ApiResult.Success -> ReportsState.Loaded(selection, answer.value)
                is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
            }

            ReportTab.DETAILED -> when (
                val answer = repository.detailedPage(
                    workspace,
                    selection.range,
                    time.zone,
                    firstRow = 1,
                    filters,
                )
            ) {
                is ApiResult.Success -> ReportsState.Loaded(
                    selection,
                    DetailedReport(answer.value.entries, answer.value.nextRow),
                )

                is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
            }

            ReportTab.WEEKLY -> when (val answer = repository.weekly(workspace, selection.range.from, filters)) {
                is ApiResult.Success -> ReportsState.Loaded(
                    selection,
                    WeeklyReport.of(selection.range.from, answer.value),
                )

                is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
            }
        }
        return shown to workspace
    }

    /** [report] with the page that starts at [next] added, or why there is none. */
    private suspend fun nextPage(
        selection: RangeSelection,
        workspace: ReportWorkspace,
        report: DetailedReport,
        next: Int,
        filters: ReportFilters,
    ): ReportsState {
        val time = accountTime() ?: return ReportsState.NoAccountTime(accountSettings.value?.timeZone)
        return when (val answer = repository.detailedPage(workspace, selection.range, time.zone, next, filters)) {
            is ApiResult.Success -> ReportsState.Loaded(
                selection,
                DetailedReport(report.entries + answer.value.entries, answer.value.nextRow),
            )

            is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
        }
    }

    private fun failureOf(error: ApiError): ReportFailure = reportFailureOf(error)

    init {
        // A widget's day tap, from before the page opened or while it shows.
        viewModelScope.launch {
            choices.todayAsked.collect { asked -> if (asked && choices.takeToday()) openToday() }
        }
    }

    /** What [page] starts with, from the flows as they are now. */
    private fun pageNow() = ReportsPageState(
        report = state.value,
        tab = tab.value,
        settings = accountSettings.value,
        time = accountTime(),
        filters = filters.value,
        filterOptions = filterOptions.value,
        exportSupported = tab.value.csvKind()?.let(exportCapabilities.value::supports) == true,
        exportState = exportState.value,
    )
}
