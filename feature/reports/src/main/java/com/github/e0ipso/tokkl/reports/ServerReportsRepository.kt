package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.AppSummaryBody
import com.github.e0ipso.tokkl.api.DetailedSearchBody
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.api.ReportSearchRow
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.WeeklyReportBody
import com.github.e0ipso.tokkl.api.WithHeaders
import com.github.e0ipso.tokkl.api.flatMap
import com.github.e0ipso.tokkl.api.getOrElse
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.signedIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * The workspace a report covers: the account's default workspace, as in the web app, and its
 * rounding setting. [rounding] is -1 down, 0 to the nearest and 1 up, each entry to
 * [roundingMinutes], with 0 minutes for no rounding.
 */
data class ReportWorkspace(
    val id: WorkspaceId,
    val rounding: Int,
    val roundingMinutes: Int,
    /** A Tokkl role makes the app report worth trying; only its POST establishes support. */
    val appSummaryCandidate: Boolean = false,
)

/**
 * The Reports API v3 calls behind the reports, on the signed-in account's server. Each call
 * suspends while its request runs. Nothing here stores what the server sends.
 */
class ServerReportsRepository(private val client: ApiClientProvider) : ReportsRepository {

    /** The account's default workspace from `GET /api/v9/me`, with its rounding from `GET /api/v9/workspaces/{id}`. */
    override suspend fun workspace(): ApiResult<ReportWorkspace> = client.signedIn { api ->
        val user = api.account.defaultWorkspace().getOrElse { return@signedIn it }
        val id = user.defaultWorkspaceId ?: return@signedIn ApiResult.Failure(
            ApiError.InvalidResponse(IllegalArgumentException("/me has no default_workspace_id")),
        )
        api.account.workspace(id).map { ReportWorkspace(id, it.rounding, it.roundingMinutes, user.role != null) }
    }

    /**
     * Prefer the configured server's app Summary. Only a definitive endpoint absence permits
     * compatibility aggregation. Do not mask auth, quota, transport or malformed-success errors.
     * The app endpoint selects the current default workspace, timezone and rounding itself;
     * sending cached rounding here would override a newer setting on the server.
     */
    override suspend fun summary(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        filters: ReportFilters,
    ): ApiResult<SummaryReport> = client.signedIn { api ->
        if (workspace.appSummaryCandidate) {
            val body = AppSummaryBody(
                startDate = range.from,
                endDate = range.to,
                grouping = "projects",
                subGrouping = "projects",
                filter = filters.apiFilter(),
            )
            val answer = api.reports.appSummary(body)
            val absent = answer is ApiResult.Failure && answer.error.let {
                it is ApiError.UnexpectedStatus && it.status in setOf(HttpStatus.NOT_FOUND, HttpStatus.GONE)
            }
            if (!absent) return@signedIn answer.flatMap(::decodeAppSummary)
        }
        api.entries(workspace, range, zone, filters).map { SummaryReport.of(it, range, firstDayOfWeek) }
    }

    /**
     * Every entry of [range] in [workspace] that passes [filters], oldest first, from the detailed
     * search, with each entry's day in [zone]. Pages follow the `X-Next-Row-Number` the server sends.
     *
     * The request asks for the workspace's rounding. Tokkl's web app always applies it, while the
     * Reports API rounds only when asked, as in Toggl (Tokkl's docs/PRD.md R8).
     */
    internal suspend fun entries(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        filters: ReportFilters,
    ): ApiResult<List<ReportEntry>> = client.signedIn { it.entries(workspace, range, zone, filters) }

    private suspend fun TokklApi.entries(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        filters: ReportFilters,
    ): ApiResult<List<ReportEntry>> {
        val entries = mutableListOf<ReportEntry>()
        var firstRow = 1
        repeat(MAX_PAGES) {
            val page = search(workspace, range, filters, "ASC", PAGE_SIZE, firstRow).getOrElse { return it }
            for (row in page.value) {
                try {
                    entries += row.entries(zone)
                } catch (e: DateTimeParseException) {
                    return notADateAndTime(e)
                }
            }
            firstRow = nextRow(page, firstRow).getOrElse { return it } ?: return ApiResult.Success(entries)
        }
        return ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("more than $MAX_PAGES pages")))
    }

    /**
     * One page of the detailed report of [range] in [workspace], of the entries that pass
     * [filters], newest first as the web app's detailed report lists them, starting at [firstRow]:
     * 1 for the first page, then the row the server named for the next one. Times are in [zone].
     * The phone never works out a page itself.
     */
    override suspend fun detailedPage(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstRow: Int,
        filters: ReportFilters,
    ): ApiResult<DetailedPage> = client.signedIn { api ->
        val page = api.search(workspace, range, filters, "DESC", DETAILED_PAGE_SIZE, firstRow)
            .getOrElse { return@signedIn it }
        val entries = try {
            page.value.flatMap { it.detailed(zone) }
        } catch (e: DateTimeParseException) {
            return@signedIn notADateAndTime(e)
        }
        nextRow(page, firstRow).map { DetailedPage(entries, it) }
    }

    /**
     * The weekly report of the seven days from [weekStart] in [workspace], of the entries that pass
     * [filters], from `POST /reports/api/v3/workspace/{id}/weekly/time_entries`: a row per project
     * with its seconds on each day. The request asks for the workspace's rounding, as [entries] does.
     */
    override suspend fun weekly(
        workspace: ReportWorkspace,
        weekStart: LocalDate,
        filters: ReportFilters,
    ): ApiResult<List<WeeklyRow>> = client.signedIn { api ->
        val body = WeeklyReportBody(
            startDate = weekStart,
            endDate = weekStart.plusDays(DAYS_IN_WEEK - 1L),
            rounding = workspace.rounding,
            roundingMinutes = workspace.roundingMinutes,
            filter = filters.apiFilter(),
        )
        val rows = api.reports.weekly(workspace.id, body).getOrElse { return@signedIn it }
        if (rows.any { it.seconds.size != DAYS_IN_WEEK }) {
            return@signedIn ApiResult.Failure(
                ApiError.InvalidResponse(IllegalArgumentException("a weekly row does not hold $DAYS_IN_WEEK days")),
            )
        }
        ApiResult.Success(
            rows.map {
                WeeklyRow(
                    it.projectId,
                    it.projectName ?: "",
                    it.projectColor ?: it.projectHexColor,
                    it.seconds.map { day -> day ?: 0 },
                )
            },
        )
    }

    /**
     * A page of `POST /reports/api/v3/workspace/{id}/search/time_entries` of the entries that pass
     * [filters], by date in [direction], `ASC` or `DESC`, with the workspace's rounding.
     */
    private suspend fun TokklApi.search(
        workspace: ReportWorkspace,
        range: ReportRange,
        filters: ReportFilters,
        direction: String,
        pageSize: Int,
        firstRow: Int,
    ): ApiResult<WithHeaders<List<ReportSearchRow>>> {
        val body = DetailedSearchBody(
            startDate = range.from,
            endDate = range.to,
            rounding = workspace.rounding,
            roundingMinutes = workspace.roundingMinutes,
            orderBy = "date",
            orderDir = direction,
            pageSize = pageSize,
            firstRowNumber = firstRow,
            filter = filters.apiFilter(),
        )
        return reports.search(workspace.id, body)
    }

    /**
     * The row the page after [page] starts on, from its `X-Next-Row-Number`, or null when the
     * server sent none, after the last page. A row that does not come after [firstRow] fails.
     */
    private fun nextRow(page: WithHeaders<*>, firstRow: Int): ApiResult<Int?> {
        val next = page.header(NEXT_ROW_HEADER)?.trim()?.toIntOrNull() ?: return ApiResult.Success(null)
        if (next <= firstRow) {
            return ApiResult.Failure(
                ApiError.InvalidResponse(IllegalArgumentException("the next page does not move forward")),
            )
        }
        return ApiResult.Success(next)
    }

    private fun notADateAndTime(e: DateTimeParseException): ApiResult.Failure = ApiResult.Failure(
        ApiError.InvalidResponse(IllegalArgumentException("a time entry's start or stop is not a date and time", e)),
    )

    private companion object {
        /** Tokkl's largest page. */
        const val PAGE_SIZE = 1000

        /** Toggl's default page, so a scroll near the end of the list asks for little at a time. */
        const val DETAILED_PAGE_SIZE = 50

        /** A year at a thousand entries a page needs far fewer; the cap stops a server that pages forever. */
        const val MAX_PAGES = 100

        const val NEXT_ROW_HEADER = "X-Next-Row-Number"
    }
}

/**
 * The row's entries, each on its day in [zone]. A row that holds several entries, as Toggl's
 * grouped rows do, counts its amount once, on its first entry. Throws [DateTimeParseException] for
 * a start that is not an RFC 3339 date and time.
 */
private fun ReportSearchRow.entries(zone: ZoneId): List<ReportEntry> = timeEntries.mapIndexed { index, entry ->
    ReportEntry(
        seconds = entry.seconds,
        billable = billable,
        amountCents = if (index == 0) billableAmountInCents else billableAmountInCents?.let { 0L },
        currency = currency ?: "",
        projectId = projectId,
        projectName = projectName,
        projectColor = projectColor,
        date = OffsetDateTime.parse(entry.start).atZoneSameInstant(zone).toLocalDate(),
    )
}

/** Throws [DateTimeParseException] for a start or stop that is not an RFC 3339 date and time. */
private fun ReportSearchRow.detailed(zone: ZoneId): List<DetailedEntry> = timeEntries.map { entry ->
    DetailedEntry(
        id = entry.id,
        description = description ?: "",
        projectName = projectName,
        projectColor = projectColor,
        taskName = taskName,
        start = OffsetDateTime.parse(entry.start).atZoneSameInstant(zone),
        stop = entry.stop?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) },
        seconds = entry.seconds,
    )
}
