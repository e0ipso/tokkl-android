package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.WithHeaders
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The workspace a report covers: the account's default workspace, as in the web app, and its
 * rounding setting. [rounding] is -1 down, 0 to the nearest and 1 up, each entry to
 * [roundingMinutes], with 0 minutes for no rounding.
 */
data class ReportWorkspace(val id: Long, val rounding: Int, val roundingMinutes: Int)

/**
 * The Reports API v3 calls behind the reports, on the configured server. Every call blocks, so
 * make them off the main thread. Nothing here stores what the server sends.
 */
class ReportsServer(private val api: TogglApiClient) {

    /** The account's default workspace from `GET /api/v9/me`, with its rounding from `GET /api/v9/workspaces/{id}`. */
    fun workspace(): ApiResult<ReportWorkspace> {
        val user = when (val answer = api.get(api.server.api("me"), ReportUser.serializer())) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> return answer
        }
        val id = user.defaultWorkspaceId
        return when (val answer = api.get(api.server.api("workspaces", id.toString()), WorkspaceRounding.serializer())) {
            is ApiResult.Success -> ApiResult.Success(ReportWorkspace(id, answer.value.rounding, answer.value.roundingMinutes))
            is ApiResult.Failure -> answer
        }
    }

    /**
     * Every entry of [range] in [workspace], oldest first, from the detailed search, with each
     * entry's day in [zone]. Pages follow the `X-Next-Row-Number` the server sends.
     *
     * The request asks for the workspace's rounding. Tokkl's web app always applies it, while the
     * Reports API rounds only when asked, as in Toggl (Tokkl's docs/PRD.md R8).
     */
    fun entries(workspace: ReportWorkspace, range: ReportRange, zone: ZoneId): ApiResult<List<ReportEntry>> {
        val entries = mutableListOf<ReportEntry>()
        var firstRow = 1
        repeat(MAX_PAGES) {
            val page = when (val answer = search(workspace, range, "ASC", PAGE_SIZE, firstRow)) {
                is ApiResult.Success -> answer.value
                is ApiResult.Failure -> return answer
            }
            for (row in page.value) {
                try {
                    entries += row.entries(zone)
                } catch (e: DateTimeParseException) {
                    return notADateAndTime(e)
                }
            }
            firstRow = when (val next = nextRow(page, firstRow)) {
                is ApiResult.Success -> next.value ?: return ApiResult.Success(entries)
                is ApiResult.Failure -> return next
            }
        }
        return ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("more than $MAX_PAGES pages")))
    }

    /**
     * One page of the detailed report of [range] in [workspace], newest first as the web app's
     * detailed report lists them, starting at [firstRow]: 1 for the first page, then the row the
     * server named for the next one. Times are in [zone]. The phone never works out a page itself.
     */
    fun detailedPage(workspace: ReportWorkspace, range: ReportRange, zone: ZoneId, firstRow: Int): ApiResult<DetailedPage> {
        val page = when (val answer = search(workspace, range, "DESC", DETAILED_PAGE_SIZE, firstRow)) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> return answer
        }
        val entries = try {
            page.value.flatMap { it.detailed(zone) }
        } catch (e: DateTimeParseException) {
            return notADateAndTime(e)
        }
        return when (val next = nextRow(page, firstRow)) {
            is ApiResult.Success -> ApiResult.Success(DetailedPage(entries, next.value))
            is ApiResult.Failure -> next
        }
    }

    /**
     * A page of `POST /reports/api/v3/workspace/{id}/search/time_entries` by date in [direction],
     * `ASC` or `DESC`, with the workspace's rounding.
     */
    private fun search(
        workspace: ReportWorkspace,
        range: ReportRange,
        direction: String,
        pageSize: Int,
        firstRow: Int,
    ): ApiResult<WithHeaders<List<DetailedRow>>> {
        val url = api.server.reports("workspace", workspace.id.toString(), "search", "time_entries")
        val body = buildJsonObject {
            put("start_date", range.from.toString())
            put("end_date", range.to.toString())
            put("rounding", workspace.rounding)
            put("rounding_minutes", workspace.roundingMinutes)
            put("order_by", "date")
            put("order_dir", direction)
            put("page_size", pageSize)
            put("first_row_number", firstRow)
        }
        return api.sendWithHeaders("POST", url, body, ROWS)
    }

    /**
     * The row the page after [page] starts on, from its `X-Next-Row-Number`, or null when the
     * server sent none, after the last page. A row that does not come after [firstRow] fails.
     */
    private fun nextRow(page: WithHeaders<*>, firstRow: Int): ApiResult<Int?> {
        val next = page.headers[NEXT_ROW_HEADER]?.trim()?.toIntOrNull() ?: return ApiResult.Success(null)
        if (next <= firstRow) return ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("the next page does not move forward")))
        return ApiResult.Success(next)
    }

    private fun notADateAndTime(e: DateTimeParseException): ApiResult.Failure =
        ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("a time entry's start or stop is not a date and time", e)))

    private companion object {
        /** Tokkl's largest page. */
        const val PAGE_SIZE = 1000

        /** Toggl's default page, so a scroll near the end of the list asks for little at a time. */
        const val DETAILED_PAGE_SIZE = 50

        /** A year at a thousand entries a page needs far fewer; the cap stops a server that pages forever. */
        const val MAX_PAGES = 100

        const val NEXT_ROW_HEADER = "X-Next-Row-Number"

        val ROWS = ListSerializer(DetailedRow.serializer())
    }
}

@Serializable
private class ReportUser(@SerialName("default_workspace_id") val defaultWorkspaceId: Long)

/** Toggl and Tokkl send a workspace's rounding as whole numbers. */
@Serializable
private class WorkspaceRounding(
    val rounding: Int = 0,
    @SerialName("rounding_minutes") val roundingMinutes: Int = 0,
)

/**
 * One row of a detailed search. Tokkl sends one time entry per row. A row that holds several, as
 * Toggl's grouped rows do, counts its amount once, on its first entry.
 */
@Serializable
private class DetailedRow(
    val description: String? = null,
    val billable: Boolean = false,
    @SerialName("project_id") val projectId: Long? = null,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("project_color") val projectColor: String? = null,
    @SerialName("task_name") val taskName: String? = null,
    @SerialName("billable_amount_in_cents") val billableAmountInCents: Long? = null,
    val currency: String? = null,
    @SerialName("time_entries") val timeEntries: List<DetailedTimeEntry> = emptyList(),
) {
    /** Throws [DateTimeParseException] for a start that is not an RFC 3339 date and time. */
    fun entries(zone: ZoneId): List<ReportEntry> = timeEntries.mapIndexed { index, entry ->
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
    fun detailed(zone: ZoneId): List<DetailedEntry> = timeEntries.map { entry ->
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
}

/**
 * [start] and [stop] are RFC 3339 with an offset, such as `2026-10-18T09:30:00+00:00`. [seconds]
 * is after rounding.
 */
@Serializable
private class DetailedTimeEntry(val id: Long? = null, val seconds: Long, val start: String, val stop: String? = null)
