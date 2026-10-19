package com.github.e0ipso.tokkl.reports

import android.content.res.Resources
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.time.jsShortestDecimal
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

// How the reports screen writes numbers and dates, after the web app's helpers in
// ../tokkl/packages/web/src/pages/reports/format.ts, range.ts and lib/settings.ts. Words and
// date patterns come from string resources.

/**
 * [part] as a percentage of [whole] with one decimal, as formatPercent(percent(part, whole)).
 * Intl.NumberFormat rounds the shortest decimal that reads back as the double, with halves away
 * from zero, so 15300 of 600000 gives 2.6 where toFixed gives 2.5. Zero when [whole] is.
 */
internal fun sharePercent(part: Long, whole: Long): String {
    val percent = if (whole > 0) part.toDouble() / whole * 100 else 0.0
    return jsShortestDecimal(percent).setScale(1, RoundingMode.HALF_UP).toPlainString()
}

/**
 * [cents] of [currency] in [locale]'s currency format with the currency's own decimals, as
 * formatMoney does with Intl.NumberFormat. A code the phone does not know gives `12.50 XYZ`.
 */
internal fun formatMoney(cents: Long, currency: String, locale: Locale): String {
    val amount = BigDecimal.valueOf(cents, 2)
    val known = try {
        Currency.getInstance(currency)
    } catch (e: IllegalArgumentException) {
        null
    }
    if (known == null) return "${amount.toPlainString()} $currency"
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = known
    if (known.defaultFractionDigits >= 0) {
        format.minimumFractionDigits = known.defaultFractionDigits
        format.maximumFractionDigits = known.defaultFractionDigits
    }
    // Intl rounds halves away from zero; NumberFormat's default rounds them to even.
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(amount)
}

/** Dates in the words of the string resources, such as `13 Oct` and `Mon 13 Oct`. */
internal class DateWords(private val resources: Resources) {
    private val weekdays = resources.getStringArray(R.array.reports_weekdays_short)
    private val months = resources.getStringArray(R.array.reports_months_short)

    fun weekday(date: LocalDate): String = weekdays[date.dayOfWeek.value - 1]

    fun month(date: LocalDate): String = months[date.monthValue - 1]

    /** `13 Oct`, as shortDate. */
    fun short(date: LocalDate): String = resources.getString(R.string.reports_date, date.dayOfMonth, month(date))

    /** `13 Oct 2026`. */
    fun shortWithYear(date: LocalDate): String =
        resources.getString(R.string.reports_date_year, date.dayOfMonth, month(date), date.year)

    /**
     * The range as formatRange writes it, such as `13 Oct to 19 Oct`. A year shows where the
     * range spans two years, and on both ends when it lies in another year than [today].
     */
    fun range(range: ReportRange, today: LocalDate): String {
        val sameYear = range.from.year == range.to.year
        val showYear = !sameYear || range.from.year != today.year
        fun label(date: LocalDate, withYear: Boolean) = if (withYear) shortWithYear(date) else short(date)
        if (range.from == range.to) return label(range.from, showYear)
        return resources.getString(R.string.reports_range, label(range.from, !sameYear), label(range.to, showYear))
    }

    /** `Mon 13 Oct`, or `Mon 13 Oct 2025` when [date] lies in another year than [today]. */
    fun day(date: LocalDate, today: LocalDate): String =
        resources.getString(R.string.reports_bucket_day, weekday(date), if (date.year == today.year) short(date) else shortWithYear(date))

    /** A bar's label: `Mon 13 Oct` for a day, `Week of 13 Oct` for a week, `Oct 2026` for a month. */
    fun bucket(start: LocalDate, resolution: Resolution): String = when (resolution) {
        Resolution.DAY -> resources.getString(R.string.reports_bucket_day, weekday(start), short(start))
        Resolution.WEEK -> resources.getString(R.string.reports_bucket_week, short(start))
        Resolution.MONTH -> resources.getString(R.string.reports_bucket_month, month(start), start.year)
    }
}

/**
 * [totalSeconds] in words for TalkBack, such as `2 hours, 30 minutes`, since a screen reader
 * reads `2:30:00` as a time of day. Zero reads as `no time`.
 */
internal fun spokenDuration(resources: Resources, totalSeconds: Long): String {
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
