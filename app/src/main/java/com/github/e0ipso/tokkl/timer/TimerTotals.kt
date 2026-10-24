package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Duration
import java.time.Instant

/**
 * Today and this week on the Timer page, in whole seconds, as TimerPage.tsx sums them from the
 * entries it already has. [todaySeconds] is the account-local day of the clock. [weekSeconds]
 * runs from that day's week start through the same day.
 */
internal data class TimerPeriodTotals(val todaySeconds: Long, val weekSeconds: Long) {

    /** Both totals in the account's duration format. A zero total still has a text. */
    fun formatted(settings: AccountSettings) = FormattedTimerTotals(
        today = settings.formatDuration(todaySeconds),
        week = settings.formatDuration(weekSeconds),
    )
}

/** [TimerPeriodTotals] written for the screen. */
internal data class FormattedTimerTotals(val today: String, val week: String)

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
internal fun timerPeriodTotals(
    entries: List<CachedTimeEntry>,
    time: AccountTime,
    now: Instant,
    serverIdOf: Map<Long, Long> = emptyMap(),
): TimerPeriodTotals {
    val today = time.local(now).toLocalDate()
    val weekStart = time.startOfWeek(today)
    var todaySeconds = 0L
    var weekSeconds = 0L
    for (entry in entries.countedOnce(serverIdOf)) {
        val day = time.local(entry.start).toLocalDate()
        if (day > today) continue
        val seconds = entrySeconds(entry.start, entry.stop, now)
        if (day == today) todaySeconds += seconds
        if (day in weekStart..today) weekSeconds += seconds
    }
    return TimerPeriodTotals(todaySeconds, weekSeconds)
}

/**
 * [timerPeriodTotals] for [settings], or null when the phone cannot resolve the account's
 * timezone. The device's zone is not a stand-in, matching [AccountSettings.time].
 */
internal fun timerPeriodTotals(
    entries: List<CachedTimeEntry>,
    settings: AccountSettings,
    now: Instant,
    serverIdOf: Map<Long, Long> = emptyMap(),
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
internal fun nextTotalsDelay(now: Instant, time: AccountTime, hasRunning: Boolean): Long {
    if (hasRunning) return SECOND_MS - Math.floorMod(now.toEpochMilli(), SECOND_MS)
    val midnight = time.local(now).toLocalDate().plusDays(1).atStartOfDay(time.zone).toInstant()
    return Duration.between(now, midnight).toMillis().coerceAtLeast(1L)
}

/** Whole seconds from [start] to [stop], or to [now] while the entry runs, never below zero. */
private fun entrySeconds(start: Instant, stop: Instant?, now: Instant): Long {
    val end = stop ?: now
    return Duration.between(start, end).seconds.coerceAtLeast(0L)
}

/**
 * One row per entry. A local id whose server id is also present yields to the local row, and
 * two rows that share a canonical id count as the first of them.
 */
private fun List<CachedTimeEntry>.countedOnce(serverIdOf: Map<Long, Long>): List<CachedTimeEntry> {
    val present = HashSet<Long>(size)
    for (entry in this) present.add(entry.id)
    val hiddenServers = HashSet<Long>()
    for ((localId, serverId) in serverIdOf) {
        if (localId in present && serverId in present) hiddenServers.add(serverId)
    }
    val seen = HashSet<Long>()
    val kept = ArrayList<CachedTimeEntry>(size)
    for (entry in this) {
        if (entry.id in hiddenServers) continue
        if (seen.add(serverIdOf[entry.id] ?: entry.id)) kept.add(entry)
    }
    return kept
}

private const val SECOND_MS = 1_000L
