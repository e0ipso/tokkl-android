package com.github.e0ipso.tokkl.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.OutputStream
import java.time.LocalDate

// The filter's fields sit among a report body's other fields at a place that differs by report,
// as the web app sends them, so the report bodies are written here field by field.

/**
 * The web app's report filters. A list goes only when it holds an id, and a null in it stands for
 * none: no project, a project without a client, no tags. An `exclude` flag sends its list as the
 * matching `exclude_` field, the web app's "Is not", which Toggl Track does not have. [billable]
 * goes only when set.
 */
data class ReportFilter(
    val projectIds: List<Long?> = emptyList(),
    val excludeProjects: Boolean = false,
    val clientIds: List<Long?> = emptyList(),
    val excludeClients: Boolean = false,
    val tagIds: List<Long?> = emptyList(),
    val excludeTags: Boolean = false,
    val billable: Boolean? = null,
)

/** The body of [ReportsApi.appSummary]: the range, the grouping and the filters. */
data class AppSummaryBody(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val grouping: String,
    val subGrouping: String,
    val filter: ReportFilter,
)

/** The body of [ReportsApi.search]: a page of [pageSize] rows from [firstRowNumber], in [orderDir]. */
data class DetailedSearchBody(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rounding: Int,
    val roundingMinutes: Int,
    val orderBy: String,
    val orderDir: String,
    val pageSize: Int,
    val firstRowNumber: Int,
    val filter: ReportFilter,
)

/** The body of [ReportsApi.weekly]: seven days from [startDate] to [endDate]. */
data class WeeklyReportBody(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rounding: Int,
    val roundingMinutes: Int,
    val filter: ReportFilter,
)

/**
 * The body of [ReportsApi.exportCsv]. The summary sends [grouping] and [subGrouping], the
 * detailed export [sort] and [dir]; each goes only when set.
 */
data class ReportCsvBody(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val filter: ReportFilter,
    val rounding: Int,
    val roundingMinutes: Int,
    val durationFormat: String,
    val grouping: String? = null,
    val subGrouping: String? = null,
    val sort: String? = null,
    val dir: String? = null,
)

/**
 * The Reports API v3 and Tokkl's own report routes. Dates are the account's inclusive dates. The
 * report rows read as the caller's [DeserializationStrategy], since the Reports page owns them.
 */
class ReportsApi internal constructor(private val client: TogglApiClient) {

    /** `POST /api/app/report`, the summary as Tokkl's web app asks for it. */
    suspend fun appSummary(body: AppSummaryBody): ApiResult<JsonObject> =
        client.send(HttpMethod.POST, client.server.app("report"), body.json(), JsonObject.serializer())

    /**
     * `POST /api/app/report/{report}.csv`, where [report] is `summary` or `detailed`, streamed
     * into [output] as [TogglApiClient.downloadCsv] describes: at most [maxBytes], cancelled
     * through [cancellation]. Answers with the number of bytes written.
     */
    suspend fun exportCsv(
        report: String,
        body: ReportCsvBody,
        output: OutputStream,
        maxBytes: Long,
        cancellation: ApiCallCancellation,
    ): ApiResult<Long> =
        client.downloadCsv(client.server.app("report", "$report.csv"), body.json(), output, maxBytes, cancellation)

    /**
     * `POST /reports/api/v3/workspace/{workspaceId}/search/time_entries`, one page of the
     * detailed report, with the answer's headers, which name the next page's first row.
     */
    suspend fun <T> search(
        workspaceId: Long,
        body: DetailedSearchBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<WithHeaders<T>> =
        client.sendWithHeaders(HttpMethod.POST, timeEntries(workspaceId, "search"), body.json(), deserializer)

    /** `POST /reports/api/v3/workspace/{workspaceId}/weekly/time_entries`. */
    suspend fun <T> weekly(
        workspaceId: Long,
        body: WeeklyReportBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(HttpMethod.POST, timeEntries(workspaceId, "weekly"), body.json(), deserializer)

    private fun timeEntries(workspaceId: Long, report: String) =
        client.server.reports("workspace", workspaceId.toString(), report, "time_entries")
}

private fun AppSummaryBody.json(): JsonObject = buildJsonObject {
    put("start_date", startDate.toString())
    put("end_date", endDate.toString())
    put("grouping", grouping)
    put("sub_grouping", subGrouping)
    filter.writeTo(this)
}

private fun DetailedSearchBody.json(): JsonObject = buildJsonObject {
    put("start_date", startDate.toString())
    put("end_date", endDate.toString())
    put("rounding", rounding)
    put("rounding_minutes", roundingMinutes)
    put("order_by", orderBy)
    put("order_dir", orderDir)
    put("page_size", pageSize)
    put("first_row_number", firstRowNumber)
    filter.writeTo(this)
}

private fun WeeklyReportBody.json(): JsonObject = buildJsonObject {
    put("start_date", startDate.toString())
    put("end_date", endDate.toString())
    put("rounding", rounding)
    put("rounding_minutes", roundingMinutes)
    filter.writeTo(this)
}

private fun ReportCsvBody.json(): JsonObject = buildJsonObject {
    put("start_date", startDate.toString())
    put("end_date", endDate.toString())
    filter.writeTo(this)
    put("rounding", rounding)
    put("rounding_minutes", roundingMinutes)
    put("duration_format", durationFormat)
    grouping?.let { put("grouping", it) }
    subGrouping?.let { put("sub_grouping", it) }
    sort?.let { put("sort", it) }
    dir?.let { put("dir", it) }
}

private fun ReportFilter.writeTo(body: JsonObjectBuilder) {
    writeIds(body, if (excludeProjects) "exclude_project_ids" else "project_ids", projectIds)
    writeIds(body, if (excludeClients) "exclude_client_ids" else "client_ids", clientIds)
    writeIds(body, if (excludeTags) "exclude_tag_ids" else "tag_ids", tagIds)
    billable?.let { body.put("billable", it) }
}

private fun writeIds(body: JsonObjectBuilder, field: String, ids: List<Long?>) {
    if (ids.isEmpty()) return
    body.putJsonArray(field) { for (id in ids) add(if (id == null) JsonNull else JsonPrimitive(id)) }
}
