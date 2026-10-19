package com.github.e0ipso.tokkl.reports

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The range presets, shifting and the chart's resolution, after the web app's range.ts. The
 * presets take today and the week start as the account has them, so the week follows the
 * account's setting, never the phone's. 2026-10-18 is a Sunday.
 */
class ReportRangeTest {
    private val today = LocalDate.parse("2026-10-18")

    @Test
    fun weekPresetsStartOnTheAccountsWeekStart() {
        assertEquals(range("2026-10-12", "2026-10-18"), RangePreset.THIS_WEEK.range(today, DayOfWeek.MONDAY))
        assertEquals(range("2026-10-18", "2026-10-24"), RangePreset.THIS_WEEK.range(today, DayOfWeek.SUNDAY))
        assertEquals(range("2026-10-14", "2026-10-20"), RangePreset.THIS_WEEK.range(today, DayOfWeek.WEDNESDAY))
        assertEquals(range("2026-10-17", "2026-10-23"), RangePreset.THIS_WEEK.range(today, DayOfWeek.SATURDAY))
        assertEquals(range("2026-10-05", "2026-10-11"), RangePreset.LAST_WEEK.range(today, DayOfWeek.MONDAY))
        assertEquals(range("2026-10-11", "2026-10-17"), RangePreset.LAST_WEEK.range(today, DayOfWeek.SUNDAY))
    }

    @Test
    fun dayMonthAndYearPresets() {
        val monday = DayOfWeek.MONDAY
        assertEquals(range("2026-10-18", "2026-10-18"), RangePreset.TODAY.range(today, monday))
        assertEquals(range("2026-10-17", "2026-10-17"), RangePreset.YESTERDAY.range(today, monday))
        assertEquals(range("2026-10-01", "2026-10-31"), RangePreset.THIS_MONTH.range(today, monday))
        assertEquals(range("2026-09-01", "2026-09-30"), RangePreset.LAST_MONTH.range(today, monday))
        assertEquals(range("2026-09-19", "2026-10-18"), RangePreset.LAST_30_DAYS.range(today, monday))
        assertEquals(range("2026-01-01", "2026-12-31"), RangePreset.THIS_YEAR.range(today, monday))
        assertEquals(range("2025-01-01", "2025-12-31"), RangePreset.LAST_YEAR.range(today, monday))
        assertEquals(range("2024-02-01", "2024-02-29"), RangePreset.THIS_MONTH.range(LocalDate.parse("2024-02-10"), monday))
        assertEquals(range("2025-12-01", "2025-12-31"), RangePreset.LAST_MONTH.range(LocalDate.parse("2026-01-03"), monday))
    }

    @Test
    fun shiftMovesWholeMonthsByMonthsAndOtherRangesByTheirLength() {
        assertEquals(range("2026-08-01", "2026-08-31"), range("2026-09-01", "2026-09-30").shift(-1))
        assertEquals(range("2026-10-01", "2026-10-31"), range("2026-09-01", "2026-09-30").shift(1))
        assertEquals(range("2024-02-01", "2024-02-29"), range("2024-01-01", "2024-01-31").shift(1))
        assertEquals(range("2027-01-01", "2027-12-31"), range("2026-01-01", "2026-12-31").shift(1))
        assertEquals(range("2026-07-01", "2026-08-31"), range("2026-09-01", "2026-10-31").shift(-1))
        assertEquals(range("2026-10-05", "2026-10-11"), range("2026-10-12", "2026-10-18").shift(-1))
        assertEquals(range("2026-10-21", "2026-10-27"), range("2026-10-14", "2026-10-20").shift(1))
        assertEquals(range("2026-08-20", "2026-09-18"), range("2026-09-19", "2026-10-18").shift(-1))
        assertEquals(range("2026-10-17", "2026-10-17"), range("2026-10-18", "2026-10-18").shift(-1))
        // Starts on the 1st but does not end on a month's last day, so it moves by days.
        assertEquals(range("2026-10-11", "2026-10-20"), range("2026-10-01", "2026-10-10").shift(1))
    }

    @Test
    fun theResolutionFollowsTheRangesLength() {
        assertEquals(Resolution.DAY, range("2026-01-01", "2026-03-03").resolution) // 62 days
        assertEquals(Resolution.WEEK, range("2026-01-01", "2026-03-04").resolution) // 63 days
        assertEquals(Resolution.WEEK, range("2024-01-01", "2024-12-31").resolution) // 366 days
        assertEquals(Resolution.MONTH, range("2024-01-01", "2025-01-01").resolution) // 367 days
    }

    @Test
    fun aRangeNamesThePresetItMatches() {
        assertEquals(RangePreset.THIS_WEEK, RangePreset.matching(range("2026-10-12", "2026-10-18"), today, DayOfWeek.MONDAY))
        assertEquals(RangePreset.THIS_WEEK, RangePreset.matching(range("2026-10-14", "2026-10-20"), today, DayOfWeek.WEDNESDAY))
        // A one-day range of today is Today, not a week.
        assertEquals(RangePreset.TODAY, RangePreset.matching(range("2026-10-18", "2026-10-18"), today, DayOfWeek.MONDAY))
        assertNull(RangePreset.matching(range("2026-10-11", "2026-10-17"), today, DayOfWeek.MONDAY))
    }

    private fun range(from: String, to: String) = ReportRange(LocalDate.parse(from), LocalDate.parse(to))
}
