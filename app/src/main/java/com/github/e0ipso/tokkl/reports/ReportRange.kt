package com.github.e0ipso.tokkl.reports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Ports of the range helpers in ../tokkl/packages/web/src/pages/reports/range.ts and of
// resolutionFor and timeSeries's buckets in ../tokkl/packages/api/src/services/reports.ts, so a
// range and its chart cover the same days as in the web app.

/** The longest range the Reports API v3 accepts, in days: Tokkl's TOGGL_MAX_DAYS, as in Toggl. */
const val MAX_RANGE_DAYS = 366

/**
 * An inclusive range of calendar days in the account's timezone, as range.ts's DateRange. The
 * server reads both days in the account's timezone too.
 */
data class ReportRange(val from: LocalDate, val to: LocalDate) {

    init {
        require(!to.isBefore(from)) { "the range ends before it starts" }
    }

    /** How many days the range covers, both ends included. */
    val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1

    /** The chart's buckets for this range, as resolutionFor on the server. */
    val resolution: Resolution
        get() = when {
            days <= 62 -> Resolution.DAY
            days <= 366 -> Resolution.WEEK
            else -> Resolution.MONTH
        }

    /**
     * The range moved by its own length, earlier for a negative [direction]. A range of whole
     * months moves by calendar months, so last month steps to the month before it; any other
     * range moves by its number of days. Port of shiftRange.
     */
    fun shift(direction: Int): ReportRange {
        val step = direction.toLong()
        val months = wholeMonths()
        if (months > 0) {
            val start = from.plusMonths(step * months)
            return ReportRange(start, start.plusMonths(months).minusDays(1))
        }
        return ReportRange(from.plusDays(days * step), to.plusDays(days * step))
    }

    /**
     * The week the weekly report shows for this range: seven days from [firstDayOfWeek], the
     * week that holds [today] when the range does, otherwise the one that holds its first day.
     */
    fun week(today: LocalDate, firstDayOfWeek: DayOfWeek): ReportRange {
        val day = if (today.isBefore(from) || today.isAfter(to)) from else today
        val start = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return ReportRange(start, start.plusDays(DAYS_IN_WEEK - 1L))
    }

    /** The number of calendar months the range covers, or 0 when it does not start and end on month edges. */
    private fun wholeMonths(): Long {
        if (from.dayOfMonth != 1 || to != to.with(TemporalAdjusters.lastDayOfMonth())) return 0
        return (to.year - from.year) * 12L + (to.monthValue - from.monthValue) + 1
    }
}

/**
 * The web app's presets, in its order, without All time: that one needs Tokkl's own
 * `/api/app/entries/bounds` and spans up to ten years, more than the Reports API v3 accepts.
 */
enum class RangePreset {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    LAST_30_DAYS,
    THIS_YEAR,
    LAST_YEAR,
    ;

    /** The preset's days around [today], with weeks that start on [firstDayOfWeek]. Port of presetRange. */
    fun range(today: LocalDate, firstDayOfWeek: DayOfWeek): ReportRange {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val monthStart = today.withDayOfMonth(1)
        return when (this) {
            TODAY -> ReportRange(today, today)
            YESTERDAY -> today.minusDays(1).let { ReportRange(it, it) }
            THIS_WEEK -> ReportRange(weekStart, weekStart.plusDays(6))
            LAST_WEEK -> ReportRange(weekStart.minusDays(7), weekStart.minusDays(1))
            THIS_MONTH -> ReportRange(monthStart, monthStart.plusMonths(1).minusDays(1))
            LAST_MONTH -> monthStart.minusMonths(1).let { ReportRange(it, monthStart.minusDays(1)) }
            LAST_30_DAYS -> ReportRange(today.minusDays(29), today)
            THIS_YEAR -> year(today.year)
            LAST_YEAR -> year(today.year - 1)
        }
    }

    private fun year(year: Int) = ReportRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    companion object {
        /** The preset whose range is [range] on [today], if any. Port of matchingPreset. */
        fun matching(range: ReportRange, today: LocalDate, firstDayOfWeek: DayOfWeek): RangePreset? =
            entries.firstOrNull { it.range(today, firstDayOfWeek) == range }
    }
}

/** The length of the chart's buckets. */
enum class Resolution {
    DAY,
    WEEK,
    MONTH,
    ;

    /** The first day of the bucket that holds [date]. */
    fun bucketOf(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = when (this) {
        DAY -> date
        WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        MONTH -> date.withDayOfMonth(1)
    }

    /** The first day of the bucket after the one starting on [start]. */
    fun next(start: LocalDate): LocalDate = when (this) {
        DAY -> start.plusDays(1)
        WEEK -> start.plusDays(7)
        MONTH -> start.plusMonths(1)
    }
}
