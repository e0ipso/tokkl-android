package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
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

    data class Loaded(val selection: RangeSelection, val report: SummaryReport) : ReportsState

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
 * The reports screen's state. Each time the screen opens and each time the range changes, it
 * asks the configured server for the report, and it never asks on a timer. Reports live in
 * memory while the screen is open; [close] drops them, and nothing is written to the phone.
 * When a request fails, the screen shows why and no numbers.
 *
 * Call every function on the main thread.
 *
 * @param accountSettings the account's timezone, week start and duration format.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 */
class ReportsModel(
    val accountSettings: StateFlow<AccountSettings?>,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val mutableState = MutableStateFlow<ReportsState>(ReportsState.Closed)
    val state: StateFlow<ReportsState> = mutableState.asStateFlow()

    /** The account's workspace and rounding, read once each time the screen opens. */
    private var workspace: ReportWorkspace? = null

    private var loading: Job? = null

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

    /** Asks for the current range again, or for this week when the account's timezone was unknown. */
    fun retry() {
        val current = selection()
        if (current == null) select(RangePreset.THIS_WEEK) else load(current)
    }

    /** Closes the screen: stops waiting for an answer and forgets the report and the workspace. */
    fun close() {
        loading?.cancel()
        loading = null
        workspace = null
        mutableState.value = ReportsState.Closed
    }

    /** Times in the account's timezone, or null without settings or when the phone cannot resolve the zone. */
    fun accountTime(): AccountTime? = accountSettings.value?.time(clock)

    private fun selection(): RangeSelection? = state.value.selection()

    private fun showNoAccountTime() {
        loading?.cancel()
        mutableState.value = ReportsState.NoAccountTime(accountSettings.value?.timeZone)
    }

    private fun load(selection: RangeSelection) {
        loading?.cancel()
        mutableState.value = ReportsState.Loading(selection)
        val known = workspace
        loading = scope.launch {
            // A cancelled load drops its answer: withContext throws once it returns.
            val (shown, read) = withContext(io) { fetch(selection, known) }
            workspace = read
            mutableState.value = shown
        }
    }

    /** What to show for [selection], and the workspace, [known] or read from the server when null. */
    private fun fetch(selection: RangeSelection, known: ReportWorkspace?): Pair<ReportsState, ReportWorkspace?> {
        val time = accountTime() ?: return ReportsState.NoAccountTime(accountSettings.value?.timeZone) to known
        val api = client() ?: return ReportsState.Failed(selection, ReportFailure.AuthFailed) to known
        val server = ReportsServer(api)
        val workspace = known ?: when (val answer = server.workspace()) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> return ReportsState.Failed(selection, failureOf(answer.error)) to null
        }
        val shown = when (val answer = server.entries(workspace, selection.range, time.zone)) {
            is ApiResult.Success -> ReportsState.Loaded(selection, SummaryReport.of(answer.value, selection.range, time.firstDayOfWeek))
            is ApiResult.Failure -> ReportsState.Failed(selection, failureOf(answer.error))
        }
        return shown to workspace
    }

    private fun failureOf(error: ApiError): ReportFailure = when (error) {
        is ApiError.Network -> ReportFailure.Unreachable
        // A reverse proxy answers these while the Tokkl server behind it is down.
        is ApiError.UnexpectedStatus -> if (error.status in PROXY_UNREACHABLE) ReportFailure.Unreachable else ReportFailure.Unexpected(error.status)
        ApiError.AuthFailed -> ReportFailure.AuthFailed
        is ApiError.TrustRefused -> ReportFailure.TrustRefused
        is ApiError.RateLimited -> ReportFailure.Unexpected(error.status)
        is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> ReportFailure.Unexpected(null)
    }

    private companion object {
        /** Bad gateway, service unavailable and gateway timeout. */
        val PROXY_UNREACHABLE = setOf(502, 503, 504)
    }
}
