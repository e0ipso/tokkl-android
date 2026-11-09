package com.github.e0ipso.tokkl.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.reconcileEntryRows
import com.github.e0ipso.tokkl.cache.resolveEntryOverrides
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.model.noTimeDay
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * The signed-in session's one owner of the entries that the Timer page's history and calendar and
 * the entry form show (docs/PRD.md H1, H2, K1). It merges the cache's entries with what the server
 * sent, the history's older days and the calendar's weeks, which live in memory while the session
 * does and are never written to the phone. It keeps the copies of those entries edited on the
 * phone, which the cache holds only until the send and the refresh after it, and the history's
 * deletes and restores until the cache shows them. The history and the calendar select and format
 * what [history] and [week] give, and the entry form saves an edit through [saveEdit].
 *
 * It lives in the signed-in session's view model store, so a sign-out drops it. A sign-in again
 * over a refused token, which can be another account, drops what the server sent, as the Timer
 * page that held it did.
 *
 * The history pages older days, [OlderPages.PAGE_DAYS] at a time, when [loadOlder] is called. On
 * Tokkl, `GET /api/app/bounds` tells where the account's history starts, so the list knows when no
 * older day has entries; a server without it, such as Toggl Track, gets pages until one comes back
 * empty, and then more only on a tap. The cache's window moves on with the date, and a refresh then
 * drops the days that fell out of it. The older days end where the window began when they first
 * loaded, so the days the cache drops after that stay in the list as the cache last held them.
 *
 * The calendar loads a week outside the cache, and for a cached week the entries that start before
 * the cache and run into it, when [loadWeek] is called. Coming back to the foreground makes every
 * loaded week load again the next time it shows, and [foregrounds] tells the calendar on screen to
 * load its week at once. The old entries show until the new ones come, and stay offline.
 *
 * Call [loadOlder], [loadWeek], [deleted] and [restored] on the main thread.
 *
 * @param cached the cached entries with their send state, as AccountCache.entries gives them.
 * @param changes the change queue: where the cache holds an entry now, and where edits go.
 * @param olderPages the history's days before the cache, from the server. Runs on [io].
 * @param serverWeeks the calendar's weeks outside the cache, from the server. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 * @param app the app's trips to the background and back, and the network's returns.
 * @param accounts the account lifetime's sign-ins, each of which drops what the server sent.
 */
class SessionEntries(
    private val cached: Flow<List<EntryWithSendState>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    private val olderPages: OlderPages,
    private val serverWeeks: ServerWeeks,
    private val clock: Clock,
    app: AppForeground,
    accounts: Flow<Int>,
    scope: CoroutineScope,
    private val io: CoroutineContext,
) : ViewModel(scope) {

    /** Counts what dropped the server's entries, so an answer for an earlier account changes nothing. */
    private var generation = 0

    /** The history's and the calendar's deletes (null) and restores, by the id the action had. */
    private val overrides = MutableStateFlow<Map<EntryId, TimeEntry?>>(emptyMap())
    private val deletedEntries = mutableMapOf<EntryId, TimeEntry>()

    /** [entry] was deleted on the phone. The history and the calendar leave it out from now on. */
    fun deleted(entry: TimeEntry) {
        deletedEntries[entry.id] = entry
        override(overrides.value + (entry.id to null))
    }

    /**
     * The delete of [entryId] was taken back. The history and the calendar show the cache's row
     * again, or the copy that was deleted, under the id the cache holds it by now.
     */
    suspend fun restored(entryId: EntryId) {
        val id = changes.currentId(entryId)
        val restored = cached.first().firstOrNull { it.entry.id == id }?.entry
            ?: deletedEntries[entryId]?.copy(id = id)
        override(overrides.value + (entryId to restored))
    }

    private fun override(next: Map<EntryId, TimeEntry?>) {
        val before = overrides.value
        val restored = next.filter { (id, entry) -> entry != null && before[id] != entry }.values.filterNotNull()
        // A week's load started before Undo must not replace its restored copy. Later loads
        // replace these entries through the same held and sent guard as the calendar's edits.
        weekEdits.update { edits -> edits + restored.associate { it.id to Edit(it, held = false) } }
        overrides.value = next
    }

    // The history's older days.

    private val older = MutableStateFlow(Older())

    /** Where the account's history starts, once the server has said or shown that it cannot. */
    private var bounds: HistoryBounds? = null

    /** Online updates since the session began, including one arriving during an older-page read. */
    private var reconnects = 0L

    /**
     * The entries of the days the cache dropped between [Older.until] and its first day, and the
     * cached entries as [history] last saw them. Only [history]'s upstream reads and writes these,
     * one emission at a time.
     */
    private var dropped: List<TimeEntry> = emptyList()
    private var lastCached: List<TimeEntry> = emptyList()

    /**
     * The newest copy the cache has shown of each entry from the older pages that it held before
     * its window, as after an edit on the phone. Only [history]'s upstream reads and writes it.
     */
    private var pageEdits: Map<EntryId, TimeEntry> = emptyMap()

    /** Each entry [history] last gave, by id, as the history picks the copy it shows. */
    @Volatile
    private var shownEntries: Map<EntryId, TimeEntry> = emptyMap()

    /** The history's entries: the cache's, and the older days' that the cache does not hold. */
    fun history(): Flow<HistoryEntries> = combine(
        cached,
        accountSettings,
        older,
        overrides,
    ) { rows, settings, loaded, ov ->
        val time = settings?.time(clock)
        when {
            settings == null -> HistoryEntries.Loading

            time == null -> HistoryEntries.NoAccountTime(settings.timeZone)

            else -> {
                val firstCached = firstCachedDay(time)
                val olderEntries = daysTheCacheDropped(rows, loaded.until, time) +
                    withEdits(rows, loaded.entries, firstCached, time)
                val resolved = resolveEntryOverrides(ov, changes)
                val restoredIds = resolved.values.filterNotNull().mapTo(HashSet()) { it.id }
                pageEdits = pageEdits + rows.filter { it.entry.id in restoredIds }.associate { it.entry.id to it.entry }
                val restored = resolved.mapValues { (id, entry) -> entry?.let { pageEdits[id] ?: it } }
                val (current, reconciled) = reconcileEntryRows(rows, olderEntries, restored)
                shownEntries = reconciled.associateBy { it.id } + current.associate { it.entry.id to it.entry }
                HistoryEntries.Shown(current, reconciled, time, settings, firstCached, loaded.status)
            }
        }
    }

    /**
     * The entry [entryId] as the history last showed it: the cache's copy, or the older days' for
     * an entry the phone does not hold. Null for an entry the history does not show.
     */
    fun entry(entryId: EntryId): TimeEntry? = shownEntries[entryId]

    /**
     * Loads the [OlderPages.PAGE_DAYS] days before the oldest day loaded, or before the cached two
     * weeks at first. Does nothing while a page loads, once the server has no older entries, or
     * without an account timezone. After a failure it tries the same days again.
     */
    fun loadOlder() {
        val current = older.value
        if (current.status == OlderDays.Loading || current.status == OlderDays.End) return
        val time = accountSettings.value?.time(clock) ?: return
        val before = current.from ?: firstCachedDay(time)
        older.value = current.copy(status = OlderDays.Loading)
        val known = bounds
        val reconnectsBefore = reconnects
        val asked = generation
        viewModelScope.launch {
            val (page, read) = withContext(io) { olderPages.page(time, before, known) }
            if (asked != generation) return@launch
            bounds = read
            val loaded = older.value
            older.value = when (page) {
                is OlderPage.Loaded -> Older(
                    loaded.entries + page.entries,
                    page.from,
                    loaded.until ?: before,
                    page.status,
                )

                is OlderPage.NotLoaded -> loaded.copy(
                    // The server's word that no entry comes before the cache fixes where the older days end too.
                    until = loaded.until ?: before.takeIf { page.status == OlderDays.End },
                    status = page.status,
                )
            }
            // The request may still have used the old route when an online update arrived.
            // Retry that failure once for the newer event; a successful page never pages on.
            if (page is OlderPage.NotLoaded && page.status == OlderDays.Unreachable && reconnects != reconnectsBefore) {
                loadOlder()
            }
        }
    }

    /**
     * The entries of the days from [until] to the day before the cache's first day: days the
     * cache held when the older days first loaded and has since dropped, or will drop, as its
     * window moved on with the date. They come from the cache as [history] last saw it, the newest
     * version of each entry winning, and none when no older days have loaded.
     */
    private fun daysTheCacheDropped(
        rows: List<EntryWithSendState>,
        until: LocalDate?,
        time: AccountTime,
    ): List<TimeEntry> {
        val now = rows.map { it.entry }
        val before = lastCached
        lastCached = now
        val firstCached = firstCachedDay(time)
        dropped = if (until == null || until >= firstCached) {
            emptyList()
        } else {
            (dropped + before + now)
                .filter { time.local(it.start).toLocalDate().let { day -> day >= until && day < firstCached } }
                .associateBy { it.id }
                .values
                .toList()
        }
        return dropped
    }

    /**
     * The [pages]' entries, each as the cache last showed it while holding it before [firstCached],
     * the first day of its window: an entry from the older days edited on the phone. The refresh
     * after the send drops it from the cache, and the history keeps showing the edit rather than
     * the page's older copy. An entry the cache holds inside its window shows from the cache, so
     * the page's copy stays for it.
     */
    private fun withEdits(
        rows: List<EntryWithSendState>,
        pages: List<TimeEntry>,
        firstCached: LocalDate,
        time: AccountTime,
    ): List<TimeEntry> {
        if (pages.isEmpty()) return pages
        val ids = pages.mapTo(HashSet()) { it.id }
        var edits = pageEdits
        for (row in rows) {
            val entry = row.entry
            if (entry.id !in ids) continue
            val beforeWindow = time.local(entry.start).toLocalDate() < firstCached
            edits = if (beforeWindow) edits + (entry.id to entry) else edits - entry.id
        }
        pageEdits = edits
        return if (edits.isEmpty()) pages else pages.map { edits[it.id] ?: it }
    }

    // The calendar's weeks.

    /** What the server sent, by the span it covers, as far as it has loaded. */
    private val loaded = MutableStateFlow<Map<WeekSpan, ServerWeek>>(emptyMap())

    /**
     * The newest copy the cache has shown of each entry from [loaded] that it held before its
     * window, as after a drag on an older week, which the cache keeps only until the send and the
     * refresh after it. [Edit.held] says whether the cache still held it when [week] last read the
     * cache. A load that starts once the cache no longer holds an entry replaces its copy.
     */
    private val weekEdits = MutableStateFlow<Map<EntryId, Edit>>(emptyMap())

    /** The app went to the background, and has not come back yet. */
    private var backgrounded = false

    private val foregroundCount = MutableStateFlow(0)

    /**
     * How many times the app came back to the foreground or the network came back; the calendar
     * on screen loads its week again on each when the week is due.
     */
    val foregrounds: StateFlow<Int> = foregroundCount

    /** The calendar's entries for the week of the day [selected] picks, or of today while it is null. */
    fun week(selected: Flow<LocalDate?>): Flow<WeekEntries> =
        combine(cached, accountSettings, selected, loaded, overrides) { rows, settings, day, weeks, ov ->
            val time = settings?.time(clock)
            when {
                settings == null -> WeekEntries.Loading

                time == null -> WeekEntries.NoAccountTime(settings.timeZone)

                else -> {
                    val today = time.today()
                    val shown = day ?: today
                    val weekStart = time.startOfWeek(shown)
                    val server = weeks[spanOf(time, weekStart, today)]
                    val resolved = resolveEntryOverrides(ov, changes)
                    val edits = keepEdits(rows, weeks, time, resolved.values.filterNotNull().mapTo(HashSet()) { it.id })
                    val restored = resolved.mapValues { (id, entry) ->
                        entry?.let { edits[id]?.entry ?: server?.entries?.firstOrNull { it.id == id } ?: it }
                    }
                    val (current, serverRows) = reconcileEntryRows(
                        rows,
                        server?.entries.orEmpty().map { edits[it.id]?.entry ?: it },
                        restored,
                    )
                    val cachedWeek = isCached(weekStart, today)
                    val source = if (cachedWeek) WeekSource.Cached else server?.status ?: WeekSource.Loading
                    WeekEntries.Shown(shown, weekStart, today, time, settings, current, serverRows, source)
                }
            }
        }

    /**
     * Loads the server's part of the week from [weekStart], unless it is loading or has loaded
     * since the app last came to the foreground: the whole week outside the cache, or the entries
     * from before the cache for a cached week. A week that failed loads again the next time it
     * shows. A week that loaded before keeps its entries on screen while they load again, and
     * keeps them, to load again the next time, when the server is out of reach. A failure while
     * an event that would load the week came in tries once more while [selected] still shows it.
     */
    fun loadWeek(weekStart: LocalDate, selected: StateFlow<LocalDate?>) {
        val time = accountSettings.value?.time(clock) ?: return
        val span = spanOf(time, weekStart, time.today())
        val known = loaded.value[span]
        if (known?.isCurrent() == true) return
        val before = known?.takeIf { it.status == WeekSource.Loaded }
        loaded.update { it + (span to (before?.copy(stale = false) ?: ServerWeek(emptyList(), WeekSource.Loading))) }
        // The server has every edit that the cache no longer holds, so this load's copies replace them.
        val sent = weekEdits.value.filterValues { !it.held }
        val foregroundAtStart = foregroundCount.value
        val asked = generation
        viewModelScope.launch {
            val week = withContext(io) { serverWeeks.week(span) }
            if (asked != generation) return@launch
            val kept = before?.takeIf { week.status == WeekSource.Unreachable }?.copy(stale = true)
            if (kept == null && week.status == WeekSource.Loaded) {
                val ids = week.entries.mapTo(HashSet()) { it.id }
                weekEdits.update { edits -> edits.filterNot { (id, edit) -> id in ids && sent[id] == edit } }
            }
            loaded.update { it + (span to (kept ?: week)) }
            // A reconnect or foreground event can arrive while this request is still pending.
            // Its loadWeek call then does nothing. Retry the failed attempt once for that event,
            // while this span is still shown, without starting a polling loop.
            if (week.status == WeekSource.Unreachable && foregroundCount.value != foregroundAtStart && !backgrounded) {
                loadAgainIfShown(span, selected)
            }
        }
    }

    /** Loads the week of [span] again while [selected] still shows it. */
    private fun loadAgainIfShown(span: WeekSpan, selected: StateFlow<LocalDate?>) {
        val current = accountSettings.value?.time(clock) ?: return
        val currentWeek = current.startOfWeek(selected.value ?: current.today())
        if (spanOf(current, currentWeek, current.today()) == span) loadWeek(currentWeek, selected)
    }

    /**
     * The entry [id] as the server sent it for a week the phone does not keep, or null. The entry
     * form opens on this copy of an entry the cache lacks.
     */
    fun weekEntry(id: EntryId): TimeEntry? =
        loaded.value.values.firstNotNullOfOrNull { week -> week.entries.firstOrNull { it.id == id } }
            ?.let { weekEdits.value[id]?.entry ?: it }

    /**
     * Queues the calendar's entry [id] from [start] to [stop], with every other field as the phone
     * or the server has it: the cache's row, or the whole entry from a week the server sent.
     */
    suspend fun moveWeekEntry(id: EntryId, start: Instant, stop: Instant) {
        val held = cached.first().firstOrNull { it.entry.id == id }?.entry
        when {
            held != null -> changes.update(held.id, held.fields(), start, stop)
            else -> weekEntry(id)?.let { changes.updateOlder(it.id, it.workspaceId, it.fields(), start, stop) }
        }
    }

    /**
     * Updates [weekEdits] from [rows] and returns it. An entry of [weeks] that the cache holds from
     * before its window was changed on the phone, and its copy goes in, with the times the server
     * stores for it. One that the cache holds inside its window shows from the cache, and one that
     * the cache dropped keeps its copy, no longer held.
     */
    private fun keepEdits(
        rows: List<EntryWithSendState>,
        weeks: Map<WeekSpan, ServerWeek>,
        time: AccountTime,
        restored: Set<EntryId>,
    ): Map<EntryId, Edit> {
        val ids = weeks.values.flatMapTo(HashSet()) { week -> week.entries.map { it.id } } + restored
        val windowStart = HistoryWindow.endingToday(time).start
        val held = rows.filter { it.entry.id in ids }.associateBy { it.entry.id }
        return weekEdits.updateAndGet { edits ->
            val dropped = edits.filterKeys { it !in held }.mapValues { (_, edit) -> edit.copy(held = false) }
            val kept = held.values.filter { it.entry.start < windowStart }
                .associate { it.entry.id to Edit(it.entry.stored(time), held = true) }
            dropped + kept
        }
    }

    /** The app went to the background. A configuration change is no trip to the background. */
    private fun wentToBackground() {
        backgrounded = true
    }

    /**
     * The network came back while the app is in the foreground. Older days that failed for want
     * of an answer load again, as a tap on Try again would, and [foregrounds] counts up, so the
     * calendar on screen loads its week again when it failed or kept old entries offline.
     */
    private fun reconnected() {
        reconnects++
        if (older.value.status == OlderDays.Unreachable) loadOlder()
        foregroundCount.update { it + 1 }
    }

    /**
     * The app came back to the foreground. After [wentToBackground], every loaded week loads
     * again the next time it shows, and [foregrounds] counts up so the calendar on screen loads
     * its week at once.
     */
    private fun cameToForeground() {
        if (!backgrounded) return
        backgrounded = false
        loaded.update { weeks ->
            weeks.mapValues { (_, week) -> if (week.status == WeekSource.Loaded) week.copy(stale = true) else week }
        }
        foregroundCount.update { it + 1 }
    }

    // The entry form.

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
        start: Instant,
        stop: Instant?,
        noTimeDay: LocalDate?,
    ): Boolean {
        if (onPhone && changes.update(entryId, fields, start, stop, noTimeDay)) return true
        val time = accountSettings.value?.time(clock)
        if (onPhone && (time == null || opened >= HistoryWindow.endingToday(time).start)) return false
        changes.updateOlder(entryId, workspaceId, fields, start, requireNotNull(stop), noTimeDay)
        return true
    }

    /** Drops what the server sent, and the copies kept of its entries, for the account signing in. */
    private fun forgetServer() {
        generation++
        older.value = Older()
        bounds = null
        dropped = emptyList()
        lastCached = emptyList()
        pageEdits = emptyMap()
        shownEntries = emptyMap()
        loaded.value = emptyMap()
        weekEdits.value = emptyMap()
    }

    /**
     * The older days loaded so far: their [entries], from the day [from] on, and what comes next.
     * They end the day before [until], the cache's first day when the first page loaded or the
     * server said no entry comes before it, and null until then.
     */
    private data class Older(
        val entries: List<TimeEntry> = emptyList(),
        val from: LocalDate? = null,
        val until: LocalDate? = null,
        val status: OlderDays = OlderDays.More(auto = true),
    )

    /** The copy of an entry changed on the phone, and whether the cache [held] it when last read. */
    private data class Edit(val entry: TimeEntry, val held: Boolean)

    init {
        viewModelScope.launch { app.reconnects.collect { reconnected() } }
        viewModelScope.launch {
            app.foreground.drop(1).collect { foreground -> if (foreground) cameToForeground() else wentToBackground() }
        }
        viewModelScope.launch { accounts.drop(1).collect { forgetServer() } }
    }

    private companion object {
        /** The first day of the cached two weeks, today and the 13 days before it. */
        fun firstCachedDay(time: AccountTime): LocalDate = time.today().minusDays(HistoryWindow.DAYS - 1)
    }
}

/** What [SessionEntries.history] gives the history. */
sealed interface HistoryEntries {
    /** The account settings are not read yet. */
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
    /** The account settings are not read yet. */
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

/** True while the week loads, or once it loaded and nothing has made it stale. */
private fun ServerWeek.isCurrent(): Boolean = status == WeekSource.Loading || (status == WeekSource.Loaded && !stale)

/** The entry with the times the server stores once it gets it: a stopped entry's span snapped. */
private fun TimeEntry.stored(time: AccountTime): TimeEntry {
    if (allDay) {
        val span = stop?.let {
            time.noTimeSpan(
                noTimeDay(time.zone),
                Duration.between(start, it).seconds,
            )
        } ?: return this
        return copy(start = span.start, stop = span.stop)
    }
    val stop = stop?.takeIf { !it.isBefore(start) } ?: return this
    val span = time.snapUnsent(start, stop) ?: return this
    return copy(start = span.start, stop = span.stop)
}
