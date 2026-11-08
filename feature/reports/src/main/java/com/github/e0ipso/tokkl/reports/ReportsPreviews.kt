package com.github.e0ipso.tokkl.reports

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Design-time previews of the reports screen, with a sample week on a fixed Thursday.

@PreviewLightDark
@Composable
private fun SummaryReportPreview() {
    ReportsPreview(ReportsState.Loaded(RangeSelection(WEEK, RangePreset.THIS_WEEK), SAMPLE_SUMMARY))
}

@PreviewLightDark
@Composable
private fun ReportFailedPreview() {
    ReportsPreview(ReportsState.Failed(RangeSelection(WEEK, RangePreset.THIS_WEEK), ReportFailure.Unreachable))
}

@Composable
private fun ReportsPreview(state: ReportsState) {
    TokklTheme {
        ReportsScreen(
            state = state,
            tab = ReportTab.SUMMARY,
            settings = SETTINGS,
            time = SETTINGS.time(Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)),
            filters = ReportFilters(),
            filterOptions = FilterOptions.NONE,
            onTab = {},
            onShift = {},
            onPreset = {},
            onCustom = {},
            onFilters = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

private val SETTINGS = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null)

private val WEEK = ReportRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18))

private fun entry(day: LocalDate, hours: Double, billable: Boolean, project: Pair<Long, String>?): ReportEntry {
    val seconds = (hours * SECONDS_PER_HOUR).toLong()
    return ReportEntry(
        seconds = seconds,
        billable = billable,
        amountCents = if (billable) seconds * 8_000 / SECONDS_PER_HOUR else null,
        currency = "EUR",
        projectId = project?.first,
        projectName = project?.second,
        projectColor = if (project?.first == 1L) "#0b83d9" else "#d94182",
        date = day,
    )
}

private const val SECONDS_PER_HOUR = 3_600L

private val SAMPLE_SUMMARY = SummaryReport.of(
    listOf(
        entry(day = WEEK.from.plusDays(0), hours = 3.0, billable = true, project = 1L to "Website"),
        entry(day = WEEK.from.plusDays(1), hours = 1.25, billable = false, project = 2L to "Graffiti wall"),
        entry(day = WEEK.from.plusDays(2), hours = 4.0, billable = true, project = 1L to "Website"),
        entry(day = WEEK.from.plusDays(3), hours = 0.75, billable = false, project = null),
    ),
    WEEK,
    DayOfWeek.MONDAY,
)
