package com.github.e0ipso.tokkl.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.fold
import com.github.e0ipso.tokkl.api.getOrElse
import com.github.e0ipso.tokkl.api.map
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
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
 * stay in [choices] for the next visit. The route's saved state keeps the range too, so a new
 * process that restores the page asks for the range it showed. Explicit CSV sharing uses a separate bounded private
 * export area. When a request fails, the screen shows why and no numbers or entries, even those of
 * pages that loaded before. The screen reads one state, [page]; [state], [tab] and [filters] are
 * its parts, which the module's tests read too.
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
 * @param saved the route's saved state, which keeps the tab and the filters ([ReportChoices.restore])
 *   and the range ([SavedRange]) for a new process that restores the page.
 */
class ReportsViewModel(
    private val choices: ReportChoices,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val repository: ReportsRepository,
    scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock,
    private val filterOptions: StateFlow<FilterOptions>,
    private val exportCapabilities: StateFlow<Capabilities>,
    private val exporter: ReportExportController,
    app: AppForeground,
    private val saved: SavedStateHandle,
) : ViewModel(scope) {

    private val exportState: StateFlow<ReportExportState> = exporter.state

    /** Snapshot every export setting now, independently of the rows loaded by Detailed. */
    fun exportCsv() {
        val shown = state.value as? ReportsState.Loaded ?: return
        val choice = chosen.value
        val kind = choice.tab.csvKind() ?: return
        if (!exportCapabilities.value.supports(kind) || accountTime() == null) return
        val settings = accountSettings.value ?: return
        val knownWorkspace = workspace ?: return
        val selectedFilters = choice.filters.let {
            it.copy(projects = it.projects.toList(), clients = it.clients.toList(), tags = it.tags.toList())
        }
        exporter.start(
            ReportCsvRequest(kind, shown.selection.range, selectedFilters, knownWorkspace, settings.durationFormat),
        )
    }

    /** What happened to the share of the ready export, as the page reports it. */
    fun onExportShare(event: ExportShareEvent) = when (event) {
        is ExportShareEvent.Launch -> exporter.launchShare(event.launch)
        ExportShareEvent.Finished -> exporter.shareFinished()
        ExportShareEvent.NoTarget -> exporter.noShareTarget()
        ExportShareEvent.Failed -> exporter.shareFailed()
    }

    override fun onCleared() {
        exporter.cancel()
        super.onCleared()
    }

    private val mutableState = MutableStateFlow<ReportsState>(ReportsState.Closed)

    /** The report on screen. */
    internal val state: StateFlow<ReportsState> = mutableState.asStateFlow()

    /**
     * The tab on screen and the filters every tab's report applies. They stay when the screen
     * closes and opens again.
     */
    internal val chosen: StateFlow<ReportChoice> = choices.chosen

    /** Whether the screen has opened: from its first show, or a widget's day tap. */
    private var opened = false

    /** The range the route's saved state kept, which the first show asks for in place of this week. */
    private var restoredRange: RangeSelection? = saved.get<String>(SAVED_RANGE)?.let(SavedRange::decode)

    /** A widget's day tap asked for the report the screen is about to show, so that show asks nothing. */
    private var openedLoading = false

    /** What the screen shows, while it reads it. */
    val page: StateFlow<ReportsPageState> = combine(
        combine(state, chosen, ::Pair),
        accountSettings,
        filterOptions,
        exportState,
        exportCapabilities,
    ) { (report, choice), settings, options, export, capabilities ->
        ReportsPageState(
            report = report,
            tab = choice.tab,
            settings = settings,
            time = settings?.time(clock),
            filters = choice.filters,
            filterOptions = options,
            exportSupported = choice.tab.csvKind()?.let(capabilities::supports) == true,
            exportState = export,
        )
    }.stateInPage(
        viewModelScope,
        pageNow(),
        app,
        object : PageLoads {
            /**
             * Opens the screen on this week, or on the range a new process restored. A screen that
             * shows again, after a trip to the background or with another activity over it, asks
             * again for the range on screen.
             */
            override fun shown() {
                when {
                    openedLoading -> openedLoading = false

                    opened -> retry()

                    else -> {
                        opened = true
                        val range = restoredRange
                        restoredRange = null
                        if (range != null) load(range) else select(RangePreset.THIS_WEEK)
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
        if (choices.selectTab(tab)) selection()?.let(::load)
    }

    /** Applies [filters] to every tab and asks for the report on screen again when they change. */
    fun applyFilters(filters: ReportFilters) {
        if (choices.applyFilters(filters)) selection()?.let(::load)
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
            ) { nextPage(shown.selection, known, report, next, chosen.value.filters) }
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
        val (tab, filters) = chosen.value
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
        val workspace = known
            ?: repository.workspace().getOrElse { return ReportsState.Failed(selection, failureOf(it.error)) to null }
        val report = when (tab) {
            ReportTab.SUMMARY ->
                repository.summary(workspace, selection.range, time.zone, time.firstDayOfWeek, filters)

            ReportTab.DETAILED -> repository.detailedPage(workspace, selection.range, time.zone, firstRow = 1, filters)
                .map { DetailedReport(it.entries, it.nextRow) }

            ReportTab.WEEKLY -> repository.weekly(workspace, selection.range.from, filters)
                .map { WeeklyReport.of(selection.range.from, it) }
        }
        return shownAs(selection, report) to workspace
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
        val page = repository.detailedPage(workspace, selection.range, time.zone, next, filters)
        return shownAs(selection, page.map { DetailedReport(report.entries + it.entries, it.nextRow) })
    }

    /** The loaded [report] of [selection], or why it did not load. */
    private fun shownAs(selection: RangeSelection, report: ApiResult<Report>): ReportsState =
        report.fold({ ReportsState.Loaded(selection, it) }, { ReportsState.Failed(selection, failureOf(it)) })

    private fun failureOf(error: ApiError): ReportFailure = reportFailureOf(error)

    init {
        // A new process that restored the page brings back what the page kept, and keeps it from then on.
        saved.get<String>(SAVED_CHOICES)?.let(choices::restore)
        viewModelScope.launch { chosen.collect { saved[SAVED_CHOICES] = choices.saved() } }
        viewModelScope.launch {
            state.mapNotNull { it.selection() }.distinctUntilChanged().collect {
                saved[SAVED_RANGE] = SavedRange.of(it).encode()
            }
        }
        // A widget's day tap, from before the page opened or while it shows.
        viewModelScope.launch {
            choices.todayAsked.collect { asked -> if (asked && choices.takeToday()) openToday() }
        }
    }

    /** What [page] starts with, from the flows as they are now. */
    private fun pageNow() = ReportsPageState(
        report = state.value,
        tab = chosen.value.tab,
        settings = accountSettings.value,
        time = accountTime(),
        filters = chosen.value.filters,
        filterOptions = filterOptions.value,
        exportSupported = chosen.value.tab.csvKind()?.let(exportCapabilities.value::supports) == true,
        exportState = exportState.value,
    )

    private companion object {
        /** The key of the tab and the filters in the route's saved state. */
        const val SAVED_CHOICES = "report-choices"

        /** The key of the range's [SavedRange] in the route's saved state. */
        const val SAVED_RANGE = "report-range"
    }
}

/** What happened to the share of a ready CSV export, as the Reports page reports it. */
sealed interface ExportShareEvent {

    /** The share chooser is about to open: [launch] opens it with the export's file. */
    class Launch(val launch: (File) -> Unit) : ExportShareEvent

    /** The chooser closed, whatever the user picked. */
    data object Finished : ExportShareEvent

    /** No app takes the file. */
    data object NoTarget : ExportShareEvent

    /** The share could not open. */
    data object Failed : ExportShareEvent
}
