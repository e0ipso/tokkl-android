package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.time.DurationFormat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The app CSV routes, separate from Toggl's v3 export routes and from on-screen pagination. */
enum class ReportCsvKind(val path: String) {
    SUMMARY("summary"),
    DETAILED("detailed");

    companion object {
        fun of(tab: ReportTab): ReportCsvKind? = when (tab) {
            ReportTab.SUMMARY -> SUMMARY
            ReportTab.DETAILED -> DETAILED
            ReportTab.WEEKLY -> null
        }
    }
}

/** Immutable settings captured when Share is pressed. Dates remain inclusive account dates. */
data class ReportCsvRequest(
    val kind: ReportCsvKind,
    val range: ReportRange,
    val filters: ReportFilters,
    val workspace: ReportWorkspace,
    val durationFormat: DurationFormat,
) {
    val filename: String get() = "tokkl-${kind.path}-${range.from}-to-${range.to}.csv"

    fun body(): JsonObject = buildJsonObject {
        put("start_date", range.from.toString())
        put("end_date", range.to.toString())
        filters.writeTo(this)
        put("rounding", workspace.rounding)
        put("rounding_minutes", workspace.roundingMinutes)
        put("duration_format", durationFormat.togglName)
        when (kind) {
            ReportCsvKind.SUMMARY -> {
                put("grouping", "projects")
                put("sub_grouping", "projects")
            }
            ReportCsvKind.DETAILED -> {
                put("sort", "date")
                put("dir", "desc")
            }
        }
    }
}
