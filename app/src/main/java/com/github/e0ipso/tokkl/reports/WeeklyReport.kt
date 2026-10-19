package com.github.e0ipso.tokkl.reports

import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/** The days the weekly report covers, as the Reports API v3 weekly endpoint does. */
const val DAYS_IN_WEEK = 7

/**
 * One project's time on each of the week's seven days, in seconds after rounding. [id] is null,
 * and [title] empty, for time without a project.
 */
data class WeeklyRow(val id: Long?, val title: String, val color: String?, val seconds: List<Long>) {

    init {
        require(seconds.size == DAYS_IN_WEEK) { "a week has $DAYS_IN_WEEK days" }
    }

    /** The project's time over the whole week. */
    val total: Long get() = seconds.sum()
}

/**
 * What the weekly tab shows: one row per project and one column per day of the week that starts
 * on [weekStart], with the totals of each row, each day and the week. The rows sort as the web
 * app's weekly grid sorts them.
 */
data class WeeklyReport(val weekStart: LocalDate, val rows: List<WeeklyRow>) : Report {

    /** The week's days, in order. */
    val days: List<LocalDate> get() = (0 until DAYS_IN_WEEK).map { weekStart.plusDays(it.toLong()) }

    /** Each day's time over every project. */
    val dayTotals: List<Long> get() = (0 until DAYS_IN_WEEK).map { day -> rows.sumOf { it.seconds[day] } }

    /** The week's time over every project. */
    val total: Long get() = rows.sumOf { it.total }

    companion object {
        /**
         * The report of [rows] as the server sends them, with the rows of one project added up,
         * as a server that sends a row per person and project does. Time without a project goes
         * last, the rest by total time, most first, then by title. Port of the sort in weeklyGrid
         * in ../tokkl/packages/api/src/services/reports.ts, whose localeCompare compares the
         * titles in the root locale's collation.
         */
        fun of(weekStart: LocalDate, rows: List<WeeklyRow>): WeeklyReport {
            val merged = rows.groupBy { it.id }.values.map { same ->
                val first = same.first()
                first.copy(seconds = (0 until DAYS_IN_WEEK).map { day -> same.sumOf { it.seconds[day] } })
            }
            val titles = Collator.getInstance(Locale.ROOT)
            val sorted = merged.sortedWith(
                compareBy<WeeklyRow> { it.id == null }
                    .thenByDescending { it.total }
                    .thenComparator { a, b -> titles.compare(a.title, b.title) },
            )
            return WeeklyReport(weekStart, sorted)
        }
    }
}
