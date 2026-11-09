package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/** What the history reads of the session's entries ([SessionEntries]), and asks of them. */
interface HistoryEntriesSource {

    /** The history's entries: the cache's, and the older days' that the cache does not hold. */
    val history: StateFlow<HistoryEntries>

    /**
     * The entry [entryId] as the history last showed it: the cache's copy, or the older days' for
     * an entry the phone does not hold. Null for an entry the history does not show.
     */
    fun entry(entryId: EntryId): TimeEntry?

    /**
     * Loads the [OlderPages.PAGE_DAYS] days before the oldest day loaded, or before the cached two
     * weeks at first. Does nothing while a page loads, once the server has no older entries, or
     * without an account timezone. After a failure it tries the same days again. Call it on the
     * main thread.
     */
    fun loadOlder()
}

/** What the calendar reads of the session's entries ([SessionEntries]), and asks of them. */
interface CalendarWeeks {

    /**
     * How many times the app came back to the foreground or the network came back; the calendar
     * on screen loads its week again on each when the week is due.
     */
    val foregrounds: StateFlow<Int>

    /** The calendar's entries for the week of the day [selected] picks, or of today while it is null. */
    fun week(selected: Flow<LocalDate?>): Flow<WeekEntries>

    /**
     * Loads the server's part of the week from [weekStart], unless it is loading or has loaded
     * since the app last came to the foreground: the whole week outside the cache, or the entries
     * from before the cache for a cached week. A failure while an event that would load the week
     * came in tries once more while [selected] still shows it. Call it on the main thread.
     */
    fun loadWeek(weekStart: LocalDate, selected: StateFlow<LocalDate?>)

    /**
     * The entry [id] as the server sent it for a week the phone does not keep, or null. The entry
     * form opens on this copy of an entry the cache lacks.
     */
    fun weekEntry(id: EntryId): TimeEntry?

    /**
     * Queues the calendar's entry [id] from [start] to [stop], with every other field as the phone
     * or the server has it: the cache's row, or the whole entry from a week the server sent.
     */
    suspend fun moveWeekEntry(id: EntryId, start: Instant, stop: Instant)
}

/** Where the entry form saves the edit of a stopped entry ([SessionEntries.saveEdit]). */
fun interface PastEntryEdits {

    /**
     * Queues every field of the stopped entry [entryId] in [workspaceId], which started at
     * [opened] when the form opened on it, from the cache's copy when [onPhone]. An entry from the
     * older days or a server week goes whole, also one whose copy the refresh after its send
     * dropped. Returns false when the cache's entry has gone since.
     */
    suspend fun saveEdit(
        entryId: EntryId,
        workspaceId: WorkspaceId,
        opened: Instant,
        onPhone: Boolean,
        fields: EntryFields,
        timing: EntryTiming.Ended,
    ): Boolean
}

/** The entry actions' deletes and restores, which the history and the calendar follow. */
interface EntryRemovals {

    /** [entry] was deleted on the phone. The history and the calendar leave it out from now on. */
    fun deleted(entry: TimeEntry)

    /**
     * The delete of [entryId] was taken back. The history and the calendar show the cache's row
     * again, or the copy that was deleted, under the id the cache holds it by now.
     */
    suspend fun restored(entryId: EntryId)
}

/** What [HistoryEntriesSource.history] gives the history. */
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

/** What [CalendarWeeks.week] gives the calendar. */
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
