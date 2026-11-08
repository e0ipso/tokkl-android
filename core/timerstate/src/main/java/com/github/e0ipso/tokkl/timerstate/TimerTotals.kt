package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.LocalEntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Today and this week on the Timer page, in whole seconds, as TimerPage.tsx sums them from the
 * entries it already has. [todaySeconds] is the account-local day of the clock. [weekSeconds]
 * runs from that day's week start through the same day.
 */
data class TimerPeriodTotals(val todaySeconds: Long, val weekSeconds: Long) {

    /** Both totals in the account's duration format. A zero total still has a text. */
    fun formatted(settings: AccountSettings) = FormattedTimerTotals(
        today = settings.formatDuration(todaySeconds),
        week = settings.formatDuration(weekSeconds),
    )
}

/** [TimerPeriodTotals] written for the screen. */
data class FormattedTimerTotals(val today: String, val week: String)

/**
 * Today and this week for [entries], the cached rows with queued changes already applied.
 *
 * Each entry contributes `max(0, floor((stop - start) / 1000))` once, or the same up to [now]
 * while it runs. The whole contribution sits on the account-local day of its start, so an
 * overnight entry does not split at midnight, and a daylight-saving transition uses the elapsed
 * instant rather than the wall-clock gap. This week is [time]'s week start through today. A
 * start on a later day is left out. [now] chooses the day, not [time]'s own clock, so the total
 * ticks and the day rolls over when the screen's clock does.
 *
 * [serverIdOf] maps a local id to the server id that replaced it. When both rows are in
 * [entries], the local row is the one counted: it carries the queued edit. The same entry is
 * never added again from a pause chain's cumulative clock. Stopped segments are ordinary rows.
 *
 * There is no Reports request and no older-page argument. The calendar's selected week is not
 * an input, so browsing another week leaves these dates where they are.
 */
fun timerPeriodTotals(
    entries: List<TimeEntry>,
    time: AccountTime,
    now: Instant,
    serverIdOf: Map<LocalEntryId, EntryId> = emptyMap(),
): TimerPeriodTotals {
    val today = time.local(now).toLocalDate()
    val weekStart = time.startOfWeek(today)
    var todaySeconds = 0L
    var weekSeconds = 0L
    for ((_, day, seconds) in countedTimerEntries(entries, time, now, serverIdOf)) {
        if (day > today) continue
        if (day == today) todaySeconds += seconds
        if (day in weekStart..today) weekSeconds += seconds
    }
    return TimerPeriodTotals(todaySeconds, weekSeconds)
}

/**
 * [timerPeriodTotals] for [settings], or null when the phone cannot resolve the account's
 * timezone. The device's zone is not a stand-in, matching [AccountSettings.time].
 */
fun timerPeriodTotals(
    entries: List<TimeEntry>,
    settings: AccountSettings,
    now: Instant,
    serverIdOf: Map<LocalEntryId, EntryId> = emptyMap(),
): TimerPeriodTotals? {
    val time = settings.time() ?: return null
    return timerPeriodTotals(entries, time, now, serverIdOf)
}

/**
 * How long the totals wait before reading the clock again, while the Timer page is showing.
 * A running entry ticks to the next whole second. Otherwise the wait runs to the next
 * account-local midnight, which is also the week boundary, and the elapsed gap is used across
 * a daylight-saving transition. Nothing here asks the server.
 */
fun nextTotalsDelay(now: Instant, time: AccountTime, hasRunning: Boolean): Long {
    if (hasRunning) return SECOND_MS - Math.floorMod(now.toEpochMilli(), SECOND_MS)
    val midnight = time.local(now).toLocalDate().plusDays(1).atStartOfDay(time.zone).toInstant()
    return Duration.between(now, midnight).toMillis().coerceAtLeast(1L)
}

/** Whole seconds from [start] to [stop], or to [now] while the entry runs, never below zero. */
fun timerEntrySeconds(start: Instant, stop: Instant?, now: Instant): Long {
    val end = stop ?: now
    return Duration.between(start, end).seconds.coerceAtLeast(0L)
}

/** One effective cached segment, its account-local start day, and its nonnegative elapsed seconds. */
data class CountedTimerEntry(val entry: TimeEntry, val day: LocalDate, val seconds: Long)

/** Shared by the Timer page and widget so remaps, start days and elapsed time cannot drift. */
fun countedTimerEntries(
    entries: List<TimeEntry>,
    time: AccountTime,
    now: Instant,
    serverIdOf: Map<LocalEntryId, EntryId> = emptyMap(),
): List<CountedTimerEntry> = entries.countedOnce(serverIdOf).map { entry ->
    CountedTimerEntry(entry, time.local(entry.start).toLocalDate(), timerEntrySeconds(entry.start, entry.stop, now))
}

/**
 * One row per entry. A server row with a mapped local row present yields to the local row, and
 * two rows that share a canonical id count as the first of them.
 */
private fun List<TimeEntry>.countedOnce(serverIdOf: Map<LocalEntryId, EntryId>): List<TimeEntry> {
    val present = HashSet<EntryId>(size)
    for (entry in this) present.add(entry.id)
    val hiddenServers = HashSet<EntryId>()
    for ((localId, serverId) in serverIdOf) {
        if (localId.entryId in present && serverId in present) hiddenServers.add(serverId)
    }
    val seen = HashSet<EntryId>()
    val kept = ArrayList<TimeEntry>(size)
    for (entry in this) {
        if (entry.id in hiddenServers) continue
        if (seen.add(entry.id.local?.let(serverIdOf::get) ?: entry.id)) kept.add(entry)
    }
    return kept
}

private const val SECOND_MS = 1_000L
