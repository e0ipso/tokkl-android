package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.feature.reports.R
import com.github.e0ipso.tokkl.time.jsShortestDecimal
import com.github.e0ipso.tokkl.ui.DateWords
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import com.github.e0ipso.tokkl.core.ui.R as UiR

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
 *
 * Desktop Java and current Android reject a code that is not ISO 4217. Android 12's ICU
 * accepts every three-letter code and returns numeric code 0 when the code is absent from
 * the ISO table. Both mean the code is unknown.
 */
fun formatMoney(cents: Long, currency: String, locale: Locale): String {
    val amount = BigDecimal.valueOf(cents, 2)
    val known = try {
        Currency.getInstance(currency).takeIf { it.numericCode > 0 }
    } catch (_: IllegalArgumentException) {
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

/**
 * The range as formatRange writes it, such as `13 Oct to 19 Oct`. A year shows where the
 * range spans two years, and on both ends when it lies in another year than [today].
 */
fun DateWords.range(range: ReportRange, today: LocalDate): String {
    val sameYear = range.from.year == range.to.year
    val showYear = !sameYear || range.from.year != today.year
    fun label(date: LocalDate, withYear: Boolean) = if (withYear) shortWithYear(date) else short(date)
    if (range.from == range.to) return label(range.from, showYear)
    return resources.getString(UiR.string.reports_range, label(range.from, !sameYear), label(range.to, showYear))
}

/** A bar's label: `Mon 13 Oct` for a day, `Week of 13 Oct` for a week, `Oct 2026` for a month. */
fun DateWords.bucket(start: LocalDate, resolution: Resolution): String = when (resolution) {
    Resolution.DAY -> resources.getString(UiR.string.reports_bucket_day, weekday(start), short(start))
    Resolution.WEEK -> resources.getString(R.string.reports_bucket_week, short(start))
    Resolution.MONTH -> resources.getString(R.string.reports_bucket_month, month(start), start.year)
}
