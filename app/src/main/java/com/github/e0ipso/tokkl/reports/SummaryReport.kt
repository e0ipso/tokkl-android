package com.github.e0ipso.tokkl.reports

import java.time.DayOfWeek
import java.time.LocalDate

// The summary the web app shows, built from the entries of a Reports API v3 detailed search. The
// sums port totalsOf, timeSeries and groupEntries in ../tokkl/packages/api/src/services/reports.ts,
// which build the web app's POST /api/app/report answer from the same entries. Tokkl's v3 totals
// and summary answers carry no billable time per group, no amounts per currency and no amounts
// per group, so only the entries give every number the web app shows.

/**
 * One time entry of a report, after the server rounded it. [date] is the day it starts on in the
 * account's timezone. [amountCents] is null when no rate applies, as for a non-billable entry.
 */
data class ReportEntry(
    val seconds: Long,
    val billable: Boolean,
    val amountCents: Long?,
    val currency: String,
    val projectId: Long?,
    val projectName: String?,
    val projectColor: String?,
    val date: LocalDate,
)

/** The money billed in one [currency], in cents. */
data class Amount(val currency: String, val cents: Long)

/** [amounts] has one row per currency, in the order the entries first use them. */
data class ReportTotals(val seconds: Long, val billableSeconds: Long, val entryCount: Int, val amounts: List<Amount>)

/** One bar of the chart: the bucket that starts on [start]. */
data class SeriesPoint(val start: LocalDate, val seconds: Long, val billableSeconds: Long)

/**
 * One project's share of the range. [id] is null for the entries without a project, and
 * [title] is empty when the server sent no name. [amountCents] adds up every currency, as the
 * server's groups do, so show it only when the report has a single currency.
 */
data class ProjectShare(
    val id: Long?,
    val title: String,
    val color: String?,
    val seconds: Long,
    val billableSeconds: Long,
    val amountCents: Long,
)

/** What the summary tab shows for [range]. [projects] has the most time first. */
data class SummaryReport(
    val range: ReportRange,
    val resolution: Resolution,
    val totals: ReportTotals,
    val series: List<SeriesPoint>,
    val projects: List<ProjectShare>,
) : Report {

    /** The one currency of the amounts, or null with none or several. Port of reportCurrency. */
    val currency: String? get() = totals.amounts.singleOrNull()?.currency

    companion object {
        /**
         * The summary of [entries], oldest first as the server sorts them, for [range] with weeks
         * that start on [firstDayOfWeek].
         */
        fun of(entries: List<ReportEntry>, range: ReportRange, firstDayOfWeek: DayOfWeek): SummaryReport {
            val resolution = range.resolution
            return SummaryReport(
                range = range,
                resolution = resolution,
                totals = totalsOf(entries),
                series = series(entries, range, resolution, firstDayOfWeek),
                projects = projects(entries),
            )
        }

        private fun totalsOf(entries: List<ReportEntry>): ReportTotals {
            // LinkedHashMap keeps the order currencies first appear in, as a JavaScript Map does.
            val amounts = LinkedHashMap<String, Long>()
            for (entry in entries) {
                entry.amountCents?.let { amounts[entry.currency] = (amounts[entry.currency] ?: 0) + it }
            }
            return ReportTotals(
                seconds = entries.sumOf { it.seconds },
                billableSeconds = entries.filter { it.billable }.sumOf { it.seconds },
                entryCount = entries.size,
                amounts = amounts.map { (currency, cents) -> Amount(currency, cents) },
            )
        }

        /**
         * A point for every bucket from the one holding the range's first day up to its last day,
         * empty ones included. An entry outside every bucket counts in none.
         */
        private fun series(entries: List<ReportEntry>, range: ReportRange, resolution: Resolution, firstDayOfWeek: DayOfWeek): List<SeriesPoint> {
            val buckets = LinkedHashMap<LocalDate, LongArray>()
            var start = resolution.bucketOf(range.from, firstDayOfWeek)
            while (!start.isAfter(range.to)) {
                buckets[start] = LongArray(2)
                start = resolution.next(start)
            }
            for (entry in entries) {
                val bucket = buckets[resolution.bucketOf(entry.date, firstDayOfWeek)] ?: continue
                bucket[0] += entry.seconds
                if (entry.billable) bucket[1] += entry.seconds
            }
            return buckets.map { (day, sums) -> SeriesPoint(day, seconds = sums[0], billableSeconds = sums[1]) }
        }

        /** One share per project, in the order of first use, then sorted by time with ties kept in that order. */
        private fun projects(entries: List<ReportEntry>): List<ProjectShare> =
            entries.groupBy { it.projectId }
                .values
                .map { members ->
                    val first = members.first()
                    val totals = totalsOf(members)
                    ProjectShare(
                        id = first.projectId,
                        title = first.projectName ?: "",
                        color = first.projectColor,
                        seconds = totals.seconds,
                        billableSeconds = totals.billableSeconds,
                        amountCents = totals.amounts.sumOf { it.cents },
                    )
                }
                .sortedByDescending { it.seconds }
    }
}
