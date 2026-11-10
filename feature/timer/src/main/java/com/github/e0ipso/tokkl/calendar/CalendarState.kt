package com.github.e0ipso.tokkl.calendar

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.entries.WeekSource
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.LocalDate
import java.time.ZoneId

@Immutable
sealed interface CalendarState {

    /** The cache or the account settings are not read yet. */
    data object Loading : CalendarState

    /** The phone cannot resolve the account's timezone, [timeZone], so it cannot place entries. */
    data class NoAccountTime(val timeZone: String) : CalendarState

    /**
     * The week that holds [selected], from [weekStart], the account's first day of the week, as of
     * [today] in the account's timezone [zone]. [entries] are those that touch the week, from the
     * phone or the server as [week] says, with the running one, which ends at the current time.
     * [allDayOffered] is whether the server stores no-time entries, so the calendar has an All day
     * row. [allDay] lists the no-time entries of [entries] by day, for the days of the week.
     */
    data class Shown(
        val selected: LocalDate,
        val weekStart: LocalDate,
        val today: LocalDate,
        val zone: ZoneId,
        val settings: AccountSettings,
        val entries: List<CalendarEntry>,
        val week: WeekSource,
        val allDayOffered: Boolean = false,
    ) : CalendarState {
        val allDay: Map<LocalDate, List<CalendarEntry>> = allDayEntries(entries, weekDays, zone)

        val weekDays: List<LocalDate> get() = (0L until 7L).map(weekStart::plusDays)
    }
}

enum class ResizeEdge { START, END }
