package com.github.e0ipso.tokkl.ui

import android.content.res.Resources
import com.github.e0ipso.tokkl.core.ui.R
import java.time.LocalDate

/** Dates in the words of the string resources, such as `13 Oct` and `Mon 13 Oct`. */
class DateWords(val resources: Resources) {
    private val weekdays = resources.getStringArray(R.array.reports_weekdays_short)
    private val months = resources.getStringArray(R.array.reports_months_short)

    fun weekday(date: LocalDate): String = weekdays[date.dayOfWeek.value - 1]

    fun month(date: LocalDate): String = months[date.monthValue - 1]

    /** `13 Oct`, as shortDate. */
    fun short(date: LocalDate): String = resources.getString(R.string.reports_date, date.dayOfMonth, month(date))

    /** `13 Oct 2026`. */
    fun shortWithYear(date: LocalDate): String =
        resources.getString(R.string.reports_date_year, date.dayOfMonth, month(date), date.year)

    /** `Mon 13 Oct`, or `Mon 13 Oct 2025` when [date] lies in another year than [today]. */
    fun day(date: LocalDate, today: LocalDate): String =
        resources.getString(R.string.reports_bucket_day, weekday(date), if (date.year == today.year) short(date) else shortWithYear(date))
}

/**
 * [totalSeconds] in words for TalkBack, such as `2 hours, 30 minutes`, since a screen reader
 * reads `2:30:00` as a time of day. Zero reads as `no time`.
 */
fun spokenDuration(resources: Resources, totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val parts = listOf(
        R.plurals.reports_spoken_hours to seconds / 3600,
        R.plurals.reports_spoken_minutes to seconds % 3600 / 60,
        R.plurals.reports_spoken_seconds to seconds % 60,
    ).filter { (_, count) -> count > 0 }
        .map { (id, count) -> resources.getQuantityString(id, count.toQuantity(), count) }
    return if (parts.isEmpty()) resources.getString(R.string.reports_spoken_no_time) else parts.joinToString(", ")
}

/** Plurals take an Int. A count past Int.MAX_VALUE only picks the plural form; the number is printed in full. */
private fun Long.toQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
