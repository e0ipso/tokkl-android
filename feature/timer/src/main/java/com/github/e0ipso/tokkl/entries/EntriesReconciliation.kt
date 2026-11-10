package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.reconcileEntryRows
import com.github.e0ipso.tokkl.entryfields.noTimeEntry
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Clock
import java.time.LocalDate

/**
 * What the session keeps beside the cache to merge it with what the server sent: the cache's
 * [rows] and the account's [settings] as last read, null until read, and the copies of entries
 * that the cache held for a while. The history and the calendar show what [history] and [week]
 * make of it, and every change is one of the functions below, so it never changes in place.
 *
 * @property lastRows the cache's entries as the read before [rows] gave them.
 * @property dropped the entries of the days the cache dropped between the older days' end and its
 *   first day, as its window moved on with the date.
 * @property pageEdits the newest copy the cache showed of each entry from the older days that it
 *   held before its window, as after an edit on the phone, and of each restored entry.
 * @property weekEdits the same for the entries of the server's weeks, as after a drag on an older
 *   week, with whether the cache still held each one at the last read.
 * @property overrides the history's and the calendar's deletes (null) and restores, by the id the
 *   action had; [deleted] holds the entries deleted.
 */
internal data class Reconciliation(
    val rows: List<EntryWithSendState>? = null,
    val settings: AccountSettings? = null,
    val lastRows: List<TimeEntry> = emptyList(),
    val dropped: List<TimeEntry> = emptyList(),
    val pageEdits: Map<EntryId, TimeEntry> = emptyMap(),
    val weekEdits: Map<EntryId, KeptEdit> = emptyMap(),
    val overrides: Map<EntryId, TimeEntry?> = emptyMap(),
    val deleted: Map<EntryId, TimeEntry> = emptyMap(),
)

/** The copy of an entry changed on the phone, and whether the cache [held] it when last read. */
internal data class KeptEdit(val entry: TimeEntry, val held: Boolean)

/**
 * This state once the cache read [rows] with [settings]: the copies kept of the entries the cache
 * held, from the read before and this one, as if the history and the calendar had shown both.
 * The older days' entries come from [older], the server's weeks from [weeks], and [restoredIds]
 * are the restored entries under the ids the cache holds them by now.
 */
internal fun Reconciliation.cacheRead(
    rows: List<EntryWithSendState>,
    settings: AccountSettings?,
    clock: Clock,
    older: OlderDaysState,
    weeks: WeeksState,
    restoredIds: Set<EntryId>,
): Reconciliation {
    val time = settings?.time(clock) ?: return copy(rows = rows, settings = settings)
    val now = rows.map { it.entry }
    val firstCached = firstCachedDay(time)
    val pageIds = older.entries.idSet()
    val weekIds = weeks.entryIds() + restoredIds
    fun pagesSeen(edits: Map<EntryId, TimeEntry>, seen: List<TimeEntry>) =
        withEdits(edits, seen, pageIds, firstCached, time) + seen.filter { it.id in restoredIds }.associateBy { it.id }
    return copy(
        rows = rows,
        settings = settings,
        lastRows = now,
        dropped = droppedDays(dropped + lastRows + now, older.until, firstCached, time),
        pageEdits = pagesSeen(pagesSeen(pageEdits, lastRows), now),
        weekEdits = keepEdits(keepEdits(weekEdits, lastRows, weekIds, time), now, weekIds, time),
    )
}

/**
 * The history's entries: the cache's, and the older days' that the cache does not hold, with the
 * deletes and restores [resolved] to the ids the cache holds them by.
 */
internal fun Reconciliation.history(
    older: OlderDaysState,
    resolved: Map<EntryId, TimeEntry?>,
    clock: Clock,
): HistoryEntries {
    val settings = settings ?: return HistoryEntries.Loading
    val rows = rows ?: return HistoryEntries.Loading
    val time = settings.time(clock) ?: return HistoryEntries.NoAccountTime(settings.timeZone)
    val firstCached = firstCachedDay(time)
    val now = rows.map { it.entry }
    val restoredIds = resolved.restoredIds()
    val edits = withEdits(pageEdits, now, older.entries.idSet(), firstCached, time)
    val pages = if (edits.isEmpty()) older.entries else older.entries.map { edits[it.id] ?: it }
    val olderEntries = droppedDays(dropped + now, older.until, firstCached, time) + pages
    val kept = edits + now.filter { it.id in restoredIds }.associateBy { it.id }
    val restored = resolved.mapValues { (id, entry) -> entry?.let { kept[id] ?: it } }
    val (current, reconciled) = reconcileEntryRows(rows, olderEntries, restored)
    return HistoryEntries.Shown(current, reconciled, time, settings, firstCached, older.status)
}

/**
 * The calendar's entries for the week of [day], or of today while it is null: the cache's, and
 * the server's for that week that the cache does not hold, with the deletes and restores
 * [resolved] to the ids the cache holds them by.
 */
internal fun Reconciliation.week(
    weeks: WeeksState,
    resolved: Map<EntryId, TimeEntry?>,
    day: LocalDate?,
    clock: Clock,
): WeekEntries {
    val settings = settings ?: return WeekEntries.Loading
    val rows = rows ?: return WeekEntries.Loading
    val time = settings.time(clock) ?: return WeekEntries.NoAccountTime(settings.timeZone)
    val today = time.today()
    val shown = day ?: today
    val weekStart = time.startOfWeek(shown)
    val server = weeks.weeks[spanOf(time, weekStart, today)]
    val edits = keepEdits(weekEdits, rows.map { it.entry }, weeks.entryIds() + resolved.restoredIds(), time)
    val restored = resolved.mapValues { (id, entry) ->
        entry?.let { edits[id]?.entry ?: server?.entries?.firstOrNull { it.id == id } ?: it }
    }
    val (current, serverRows) = reconcileEntryRows(
        rows,
        server?.entries.orEmpty().map { edits[it.id]?.entry ?: it },
        restored,
    )
    val source = if (isCached(weekStart, today)) WeekSource.Cached else server?.status ?: WeekSource.Loading
    return WeekEntries.Shown(shown, weekStart, today, time, settings, current, serverRows, source)
}

/**
 * The entry [id] as the server sent it for a week the phone does not keep, as the calendar shows
 * it: the copy kept of it after a drag, or the server's. Null for an entry of no loaded week.
 */
internal fun Reconciliation.weekEntry(id: EntryId, weeks: WeeksState, clock: Clock): TimeEntry? {
    val server = weeks.entry(id) ?: return null
    val time = settings?.time(clock)
    val edits = if (time == null) weekEdits else keepEdits(weekEdits, rows.orEmpty().map { it.entry }, setOf(id), time)
    return edits[id]?.entry ?: server
}

internal fun Reconciliation.deleted(entry: TimeEntry): Reconciliation =
    overridden(entry.id, null).copy(deleted = deleted + (entry.id to entry))

/**
 * The action on [id] now shows [entry], or nothing for a delete. A restored copy goes in
 * [Reconciliation.weekEdits] too: a week's load started before Undo must not replace it. Later
 * loads replace it through the same held and sent guard as the calendar's edits.
 */
internal fun Reconciliation.overridden(id: EntryId, entry: TimeEntry?): Reconciliation {
    val restored = entry?.takeIf { overrides[id] != it }
    return copy(
        overrides = overrides + (id to entry),
        weekEdits = if (restored == null) weekEdits else weekEdits + (restored.id to KeptEdit(restored, held = false)),
    )
}

/**
 * The [week] that loaded anew replaces the copies kept of its entries that the server had when
 * the load started, [sent]: the edits the cache no longer held then.
 */
internal fun Reconciliation.weekLoaded(week: ServerWeek, sent: Map<EntryId, KeptEdit>): Reconciliation {
    val ids = week.entries.idSet()
    return copy(weekEdits = weekEdits.filterNot { (id, edit) -> id in ids && sent[id] == edit })
}

internal fun Reconciliation.forgotServer(): Reconciliation =
    copy(lastRows = emptyList(), dropped = emptyList(), pageEdits = emptyMap(), weekEdits = emptyMap())

/**
 * The entries of [entries] on the days from [until] to the day before [firstCached]: days the
 * cache held when the older days first loaded and has since dropped, or will drop, as its window
 * moved on with the date. The newest version of each entry wins, and none come back when no older
 * days have loaded.
 */
private fun droppedDays(
    entries: List<TimeEntry>,
    until: LocalDate?,
    firstCached: LocalDate,
    time: AccountTime,
): List<TimeEntry> {
    if (until == null || until >= firstCached) return emptyList()
    return entries
        .filter { time.local(it.start).toLocalDate().let { day -> day >= until && day < firstCached } }
        .associateBy { it.id }
        .values
        .toList()
}

/**
 * [edits] after the cache showed [seen]: an entry of the older days, [pageIds], that the cache
 * holds from before [firstCached], the first day of its window, was edited on the phone, and its
 * copy goes in. The refresh after the send drops it from the cache, and the history keeps showing
 * the edit rather than the page's older copy. An entry the cache holds inside its window shows
 * from the cache, so the page's copy stays for it.
 */
private fun withEdits(
    edits: Map<EntryId, TimeEntry>,
    seen: List<TimeEntry>,
    pageIds: Set<EntryId>,
    firstCached: LocalDate,
    time: AccountTime,
): Map<EntryId, TimeEntry> {
    if (pageIds.isEmpty()) return edits
    var next = edits
    for (entry in seen) {
        if (entry.id !in pageIds) continue
        val beforeWindow = time.local(entry.start).toLocalDate() < firstCached
        next = if (beforeWindow) next + (entry.id to entry) else next - entry.id
    }
    return next
}

/**
 * [edits] after the cache showed [seen]. An entry of [ids] that the cache holds from before its
 * window was changed on the phone, and its copy goes in, with the times the server stores for it.
 * One that the cache holds inside its window shows from the cache, and one that the cache dropped
 * keeps its copy, no longer held.
 */
private fun keepEdits(
    edits: Map<EntryId, KeptEdit>,
    seen: List<TimeEntry>,
    ids: Set<EntryId>,
    time: AccountTime,
): Map<EntryId, KeptEdit> {
    val windowStart = HistoryWindow.endingToday(time).start
    val held = seen.filter { it.id in ids }.associateBy { it.id }
    val dropped = edits.filterKeys { it !in held }.mapValues { (_, edit) -> edit.copy(held = false) }
    val kept = held.values.filter { it.start < windowStart }
        .associate { it.id to KeptEdit(it.stored(time), held = true) }
    return dropped + kept
}

private fun List<TimeEntry>.idSet(): Set<EntryId> = mapTo(HashSet()) { it.id }

private fun WeeksState.entryIds(): Set<EntryId> =
    weeks.values.flatMapTo(HashSet()) { week -> week.entries.map { it.id } }

private fun Map<EntryId, TimeEntry?>.restoredIds(): Set<EntryId> = values.filterNotNull().idSet()

/** The entry with the times the server stores once it gets it: an ended entry's span snapped. */
private fun TimeEntry.stored(time: AccountTime): TimeEntry = when (val timing = timing) {
    is EntryTiming.Running -> this

    is EntryTiming.AllDay -> copy(
        timing = time.noTimeEntry(timing.dayIn(time.zone), timing.duration.seconds, stored = timing.day),
    )

    is EntryTiming.Stopped -> {
        val span = timing.stop.takeIf { !it.isBefore(timing.start) }?.let { time.snapUnsent(timing.start, it) }
        if (span == null) this else copy(timing = EntryTiming.Stopped(span.start, span.stop))
    }
}
