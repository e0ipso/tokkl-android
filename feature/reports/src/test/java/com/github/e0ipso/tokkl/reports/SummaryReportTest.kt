package com.github.e0ipso.tokkl.reports

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The summary's sums, ported from the web app's totalsOf, timeSeries and groupEntries. */
class SummaryReportTest {

    @Test
    fun totalsSeriesAndProjects() {
        val entries = listOf(
            entry(3600, billable = true, cents = 5000, currency = "EUR", project = 1, name = "Web", date = "2026-10-12"),
            entry(1800, billable = false, cents = null, currency = "EUR", project = null, name = null, date = "2026-10-12"),
            entry(900, billable = true, cents = 2500, currency = "USD", project = 2, name = "Consult", date = "2026-10-14"),
            entry(3600, billable = true, cents = 0, currency = "EUR", project = 1, name = "Web", date = "2026-10-18"),
            entry(2700, billable = false, cents = null, currency = "EUR", project = 2, name = "Consult", date = "2026-10-18"),
        )
        val report = SummaryReport.of(entries, ReportRange(date("2026-10-12"), date("2026-10-18")), DayOfWeek.MONDAY)

        assertEquals(ReportTotals(12600, 8100, 5, listOf(Amount("EUR", 5000), Amount("USD", 2500))), report.totals)
        // Two currencies give no single currency for the report.
        assertNull(report.currency)
        assertEquals(7, report.series.size)
        assertEquals(SeriesPoint(date("2026-10-12"), 5400, 3600), report.series[0])
        assertEquals(SeriesPoint(date("2026-10-13"), 0, 0), report.series[1])
        assertEquals(SeriesPoint(date("2026-10-14"), 900, 900), report.series[2])
        assertEquals(SeriesPoint(date("2026-10-18"), 6300, 3600), report.series[6])
        assertEquals(
            listOf(
                ProjectShare(1, "Web", COLOUR, 7200, 7200, 5000),
                ProjectShare(2, "Consult", COLOUR, 3600, 900, 2500),
                ProjectShare(null, "", null, 1800, 0, null),
            ),
            report.projects,
        )
    }

    @Test
    fun tiedProjectsKeepTheOrderOfFirstUseAndAmountsNeedARate() {
        val entries = listOf(
            entry(60, billable = false, cents = null, currency = "EUR", project = 5, name = "B", date = "2026-10-12"),
            entry(60, billable = false, cents = null, currency = "EUR", project = 4, name = "A", date = "2026-10-12"),
        )
        val report = SummaryReport.of(entries, ReportRange(date("2026-10-12"), date("2026-10-12")), DayOfWeek.MONDAY)

        assertEquals(listOf(5L, 4L), report.projects.map { it.id })
        assertEquals(emptyList<Amount>(), report.totals.amounts)
        assertEquals(listOf(null, null), report.projects.map { it.amountCents })
    }

    @Test
    fun fallbackDistinguishesAMissingGroupAmountFromAnExplicitZero() {
        val report = SummaryReport.of(listOf(
            entry(60, billable = false, cents = null, currency = "EUR", project = 1, name = "Missing", date = "2026-10-12"),
            entry(60, billable = true, cents = 0, currency = "EUR", project = 2, name = "Zero", date = "2026-10-12"),
        ), ReportRange(date("2026-10-12"), date("2026-10-12")), DayOfWeek.MONDAY)
        assertEquals(listOf(Amount("EUR", 0)), report.totals.amounts)
        assertEquals(listOf(null, 0L), report.projects.map { it.amountCents })
    }

    @Test
    fun weekBucketsStartOnTheWeekStartAndEntriesOutsideCountInTheTotalsOnly() {
        // 90 days, so the chart has a bar per week.
        val range = ReportRange(date("2026-01-01"), date("2026-03-31"))
        val entries = listOf(
            entry(100, billable = true, cents = null, currency = "EUR", project = 1, name = "W", date = "2026-01-01"),
            entry(200, billable = false, cents = null, currency = "EUR", project = 1, name = "W", date = "2026-01-05"),
            entry(400, billable = false, cents = null, currency = "EUR", project = 1, name = "W", date = "2026-04-02"),
        )
        val report = SummaryReport.of(entries, range, DayOfWeek.MONDAY)

        assertEquals(Resolution.WEEK, report.resolution)
        assertEquals(14, report.series.size)
        assertEquals(SeriesPoint(date("2025-12-29"), 100, 100), report.series[0])
        assertEquals(SeriesPoint(date("2026-01-05"), 200, 0), report.series[1])
        assertEquals(date("2026-03-30"), report.series.last().start)
        // The totals count every entry the server sent, as totalsOf does.
        assertEquals(700, report.totals.seconds)
    }

    @Test
    fun weekBucketsFollowAnotherWeekStart() {
        val range = ReportRange(date("2026-01-01"), date("2026-03-31"))
        val entries = listOf(entry(100, billable = false, cents = null, currency = "EUR", project = null, name = null, date = "2026-01-06"))
        val report = SummaryReport.of(entries, range, DayOfWeek.WEDNESDAY)

        assertEquals(date("2025-12-31"), report.series.first().start)
        assertEquals(SeriesPoint(date("2025-12-31"), 100, 0), report.series[0])
    }

    private fun date(text: String) = LocalDate.parse(text)

    private fun entry(seconds: Long, billable: Boolean, cents: Long?, currency: String, project: Long?, name: String?, date: String) =
        ReportEntry(seconds, billable, cents, currency, project, name, if (project == null) null else COLOUR, date(date))

    private companion object {
        const val COLOUR = "#06aaf5"
    }
}
