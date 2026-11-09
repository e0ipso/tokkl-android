package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/**
 * The Reports API calls as a test answers them, with no server. Each call is recorded in [calls]
 * with the arguments the view model passed, and answered by the matching answer the test set. A
 * call the test set no answer for fails the test with an [AssertionError] that names it.
 */
internal class FakeReportsRepository : ReportsRepository {

    /** Every call, in the order the view model made them. */
    val calls = CopyOnWriteArrayList<ReportCall>()

    var workspaceAnswer: (suspend () -> ApiResult<ReportWorkspace>)? = null
    var summaryAnswer: (suspend (ReportCall.Summary) -> ApiResult<SummaryReport>)? = null
    var detailedAnswer: (suspend (ReportCall.Detailed) -> ApiResult<DetailedPage>)? = null
    var weeklyAnswer: (suspend (ReportCall.Weekly) -> ApiResult<List<WeeklyRow>>)? = null

    val workspaceCalls: List<ReportCall.Workspace> get() = calls.filterIsInstance<ReportCall.Workspace>()
    val summaries: List<ReportCall.Summary> get() = calls.filterIsInstance<ReportCall.Summary>()
    val detailedPages: List<ReportCall.Detailed> get() = calls.filterIsInstance<ReportCall.Detailed>()
    val weeklies: List<ReportCall.Weekly> get() = calls.filterIsInstance<ReportCall.Weekly>()

    override suspend fun workspace(): ApiResult<ReportWorkspace> {
        calls += ReportCall.Workspace
        return answer(workspaceAnswer, ReportCall.Workspace).invoke()
    }

    override suspend fun summary(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        filters: ReportFilters,
    ): ApiResult<SummaryReport> {
        val call = ReportCall.Summary(workspace, range, zone, firstDayOfWeek, filters)
        calls += call
        return answer(summaryAnswer, call).invoke(call)
    }

    override suspend fun detailedPage(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstRow: Int,
        filters: ReportFilters,
    ): ApiResult<DetailedPage> {
        val call = ReportCall.Detailed(workspace, range, zone, firstRow, filters)
        calls += call
        return answer(detailedAnswer, call).invoke(call)
    }

    override suspend fun weekly(
        workspace: ReportWorkspace,
        weekStart: LocalDate,
        filters: ReportFilters,
    ): ApiResult<List<WeeklyRow>> {
        val call = ReportCall.Weekly(workspace, weekStart, filters)
        calls += call
        return answer(weeklyAnswer, call).invoke(call)
    }

    private fun <A> answer(answer: A?, call: ReportCall): A =
        answer ?: throw AssertionError("the test set no answer for $call")
}

/** A call the view model made to [FakeReportsRepository], with its arguments. */
internal sealed interface ReportCall {
    data object Workspace : ReportCall

    data class Summary(
        val workspace: ReportWorkspace,
        val range: ReportRange,
        val zone: ZoneId,
        val firstDayOfWeek: DayOfWeek,
        val filters: ReportFilters,
    ) : ReportCall

    data class Detailed(
        val workspace: ReportWorkspace,
        val range: ReportRange,
        val zone: ZoneId,
        val firstRow: Int,
        val filters: ReportFilters,
    ) : ReportCall

    data class Weekly(val workspace: ReportWorkspace, val weekStart: LocalDate, val filters: ReportFilters) : ReportCall
}

/** Workspace 7 with [rounding] and [roundingMinutes], as the account's default workspace. */
internal fun reportWorkspace(rounding: Int = 0, roundingMinutes: Int = 0, appSummaryCandidate: Boolean = false) =
    ReportWorkspace(WorkspaceId(7), rounding, roundingMinutes, appSummaryCandidate)

/** A summary of [range] whose only number is its total of [seconds]. */
internal fun summaryOf(range: ReportRange, seconds: Long) = SummaryReport(
    range = range,
    resolution = range.resolution,
    totals = ReportTotals(seconds, billableSeconds = 0, entryCount = 1, amounts = emptyList()),
    series = emptyList(),
    projects = emptyList(),
)

/** The range from [from] to [to], both inclusive. */
internal fun range(from: String, to: String) = ReportRange(LocalDate.parse(from), LocalDate.parse(to))

/**
 * Holds the work the view model sends off the main thread until [runAll] runs it on the calling
 * thread, so a test sees what the screen shows while an answer is on its way.
 */
internal class QueuedDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runAll() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    fun isEmpty(): Boolean = queue.isEmpty()
}
