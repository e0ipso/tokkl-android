package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ReportCsvBody
import com.github.e0ipso.tokkl.capabilities.ReportCsvKind
import com.github.e0ipso.tokkl.time.DurationFormat

/** The CSV route of [this] tab, or null for a tab without one. */
fun ReportTab.csvKind(): ReportCsvKind? = when (this) {
    ReportTab.SUMMARY -> ReportCsvKind.SUMMARY
    ReportTab.DETAILED -> ReportCsvKind.DETAILED
    ReportTab.WEEKLY -> null
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

    /** The export's body: the summary groups by project, and the detailed export lists the newest first. */
    fun body(): ReportCsvBody {
        val body = ReportCsvBody(
            startDate = range.from,
            endDate = range.to,
            filter = filters.apiFilter(),
            rounding = workspace.rounding,
            roundingMinutes = workspace.roundingMinutes,
            durationFormat = durationFormat.togglName,
        )
        return when (kind) {
            ReportCsvKind.SUMMARY -> body.copy(grouping = "projects", subGrouping = "projects")
            ReportCsvKind.DETAILED -> body.copy(sort = "date", dir = "desc")
        }
    }
}
