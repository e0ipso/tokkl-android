package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.LocalDate
import java.time.ZoneId

/** What the calendar shows. */
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

        /** The seven days of the week, from [weekStart]. */
        val weekDays: List<LocalDate> get() = (0L until 7L).map(weekStart::plusDays)
    }
}

/** Where the shown week's entries come from. */
sealed interface WeekSource {

    /**
     * The phone keeps the week: today's week and the week before it. The entries that start
     * before the cache and run into the week join them once the server sends them.
     */
    data object Cached : WeekSource

    /** The week is not on the phone, and its entries load from the server. */
    data object Loading : WeekSource

    /** The server sent the week's entries. */
    data object Loaded : WeekSource

    /** No answer: the phone is offline, or the server or the proxy in front of it is down. */
    data object Unreachable : WeekSource

    /** The server refused the stored API token, or the phone could not read it. */
    data object AuthFailed : WeekSource

    /** The app sends nothing to the server until the user answers its trust prompt. */
    data object TrustRefused : WeekSource

    /** Any other answer. [status] is the HTTP status, or null when the body was not a list of entries. */
    data class Failed(val status: Int?) : WeekSource

    /** The week's entries are known, from the phone or the server. */
    val shows: Boolean get() = this == Cached || this == Loaded
}

/** The edge of a block that a resize drags. */
enum class ResizeEdge { START, END }
