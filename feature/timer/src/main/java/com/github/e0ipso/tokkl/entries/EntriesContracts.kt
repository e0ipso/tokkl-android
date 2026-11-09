package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.LocalDate

/** What [SessionEntries.history] gives the history. */
sealed interface HistoryEntries {
    /** The account settings or the cache are not read yet. */
    data object Loading : HistoryEntries

    /** The phone cannot resolve the account's timezone, [timeZone]. */
    data class NoAccountTime(val timeZone: String) : HistoryEntries

    /**
     * The cache's [current] rows and the [older] days' entries the cache does not hold, in the
     * account's [time]. The cache starts on [firstCached], and [status] is what comes after the
     * older days.
     */
    data class Shown(
        val current: List<EntryWithSendState>,
        val older: List<TimeEntry>,
        val time: AccountTime,
        val settings: AccountSettings,
        val firstCached: LocalDate,
        val status: OlderDays,
    ) : HistoryEntries
}

/** What [SessionEntries.week] gives the calendar. */
sealed interface WeekEntries {
    /** The account settings or the cache are not read yet. */
    data object Loading : WeekEntries

    /** The phone cannot resolve the account's timezone, [timeZone]. */
    data class NoAccountTime(val timeZone: String) : WeekEntries

    /**
     * The week from [weekStart] that holds the [shown] day, as of [today] in the account's [time]:
     * the cache's [current] rows and the [server]'s entries the cache does not hold, from [source].
     */
    data class Shown(
        val shown: LocalDate,
        val weekStart: LocalDate,
        val today: LocalDate,
        val time: AccountTime,
        val settings: AccountSettings,
        val current: List<EntryWithSendState>,
        val server: List<TimeEntry>,
        val source: WeekSource,
    ) : WeekEntries
}
