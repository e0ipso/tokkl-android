package com.github.e0ipso.tokkl.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.reconcileEntryRows
import com.github.e0ipso.tokkl.cache.resolveEntryOverrides
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AppEvent
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The history on the Timer page (docs/PRD.md H1): the cached two weeks from the phone, which
 * show offline, and older days that [loadOlder] pages from the configured server, [PAGE_DAYS] at
 * a time, when the list's end shows. Nothing loads on a timer. The older days live in memory
 * while the view model does, and nothing of them is written to the phone.
 *
 * On Tokkl, `GET /api/app/bounds` tells where the account's history starts, so the list knows
 * when no older day has entries. A server without it, such as Toggl Track, gets pages until one
 * comes back empty, and then more only on a tap.
 *
 * A tap on any entry opens the entry form, also on the older days, whose entries the form edits
 * through the queue as the history holds them. Such an edit sits in the cache until the send and
 * the refresh after it, and the history then keeps showing the edited entry.
 *
 * The cache's window moves on with the date, and a refresh then drops the days that fell out of
 * it. The older days end where the window began when they first loaded, so the days the cache
 * drops after that stay in the list as the cache last held them, with no request, and the list
 * keeps every day from today to the oldest day loaded.
 *
 * Call [loadOlder] on the main thread.
 *
 * @param entries the cached entries with their send state, as AccountCache.entries gives them.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 * @param overrides the entry actions' deletes and restores, which [reconcile] applies.
 * @param appEvents each online report of the network calls [reconnected].
 */
class HistoryViewModel(
    entries: Flow<List<EntryWithSendState>>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val client: ApiClientProvider,
    scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
    private val currentId: suspend (Long) -> Long = { it },
    overrides: Flow<Map<Long, CachedTimeEntry?>> = emptyFlow(),
    appEvents: Flow<AppEvent> = emptyFlow(),
) : ViewModel(scope) {

    private val older = MutableStateFlow(Older())
    private val reconciliation = MutableStateFlow<Map<Long, CachedTimeEntry?>>(emptyMap())

    fun reconcile(overrides: Map<Long, CachedTimeEntry?>) {
        reconciliation.value = overrides
    }

    private val pages = combine(older, reconciliation) { loaded, overrides -> loaded to overrides }

    /** Where the account's history starts, once the server has said or shown that it cannot. */
    private var bounds: HistoryBounds? = null

    /** Online updates since the view model opened, including one arriving during an older-page read. */
    private var reconnects = 0L

    /**
     * The entries of the days the cache dropped between [Older.until] and its first day, and the
     * cached entries as [state] last saw them. Only [state]'s upstream reads and writes these, one
     * emission at a time.
     */
    private var dropped: List<CachedTimeEntry> = emptyList()
    private var lastCached: List<CachedTimeEntry> = emptyList()

    /**
     * The newest copy the cache has shown of each entry from the older pages that it held before
     * its window, as after an edit on the phone, which the cache keeps until the send and the
     * refresh after it. Only [state]'s upstream reads and writes it.
     */
    private var edited: Map<Long, CachedTimeEntry> = emptyMap()

    /** Each entry [state] last showed, by id, as [historyDays] picks the copy it shows. */
    @Volatile
    private var shownEntries: Map<Long, CachedTimeEntry> = emptyMap()

    val state: StateFlow<HistoryState> = combine(entries, projects, tasks, accountSettings, pages) { cached, projectRows, taskRows, settings, (loaded, overrides) ->
        val time = settings?.time(clock)
        when {
            settings == null -> HistoryState.Loading
            time == null -> HistoryState.NoAccountTime(settings.timeZone)
            else -> {
                val firstCached = firstCachedDay(time)
                val olderEntries = daysTheCacheDropped(cached, loaded.until, time) + withEdits(cached, loaded.entries, firstCached, time)
                val resolved = resolveEntryOverrides(overrides, currentId)
                val restoredIds = resolved.values.filterNotNull().mapTo(HashSet()) { it.id }
                edited = edited + cached.filter { it.entry.id in restoredIds }.associate { it.entry.id to it.entry }
                val restored = resolved.mapValues { (id, entry) -> entry?.let { edited[id] ?: it } }
                val (current, reconciled) = reconcileEntryRows(cached, olderEntries, restored)
                shownEntries = reconciled.associateBy { it.id } + current.associate { it.entry.id to it.entry }
                HistoryState.Shown(
                    days = historyDays(
                        current,
                        reconciled,
                        projectRows,
                        taskRows,
                        time,
                        collapse = settings.collapseSimilar,
                        windowStart = firstCached,
                    ),
                    today = time.today(),
                    zone = time.zone,
                    settings = settings,
                    older = loaded.status,
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryState.Loading)

    /**
     * The entry [entryId] as the history last showed it: the cache's copy, or the older days' for
     * an entry the phone does not hold. Null for an entry the history does not show.
     */
    fun entry(entryId: Long): CachedTimeEntry? = shownEntries[entryId]

    /**
     * Loads the [PAGE_DAYS] days before the oldest day loaded, or before the cached two weeks at
     * first. Does nothing while a page loads, once the server has no older entries, or without
     * an account timezone. After a failure it tries the same days again.
     */
    fun loadOlder() {
        val current = older.value
        if (current.status == OlderDays.Loading || current.status == OlderDays.End) return
        val time = accountSettings.value?.time(clock) ?: return
        val before = current.from ?: firstCachedDay(time)
        older.value = current.copy(status = OlderDays.Loading)
        val known = bounds
        val reconnectsBefore = reconnects
        viewModelScope.launch {
            val (page, read) = withContext(io) { fetchOlderPage(client, time, before, known) }
            bounds = read
            val loaded = older.value
            older.value = when (page) {
                is OlderPage.Loaded -> Older(loaded.entries + page.entries, page.from, loaded.until ?: before, page.status)
                is OlderPage.NotLoaded -> loaded.copy(
                    // The server's word that no entry comes before the cache fixes where the older days end too.
                    until = loaded.until ?: before.takeIf { page.status == OlderDays.End },
                    status = page.status,
                )
            }
            // The request may still have used the old route when an online update arrived.
            // Retry that failure once for the newer event; a successful page never pages on.
            if (page is OlderPage.NotLoaded && page.status == OlderDays.Unreachable && reconnects != reconnectsBefore) loadOlder()
        }
    }

    /**
     * The network came back while the app is on screen. Older days that failed for want of an
     * answer load again, as a tap on Try again would; older days that loaded, or any other state,
     * ask nothing. Call it on the main thread.
     */
    fun reconnected() {
        reconnects++
        if (older.value.status == OlderDays.Unreachable) loadOlder()
    }

    /**
     * The entries of the days from [until] to the day before the cache's first day: days the
     * cache held when the older days first loaded and has since dropped, or will drop, as its
     * window moved on with the date. They come from the cache as [state] last saw it, the newest
     * version of each entry winning, and none when no older days have loaded.
     */
    private fun daysTheCacheDropped(cached: List<EntryWithSendState>, until: LocalDate?, time: AccountTime): List<CachedTimeEntry> {
        val now = cached.map { it.entry }
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
    private fun withEdits(cached: List<EntryWithSendState>, pages: List<CachedTimeEntry>, firstCached: LocalDate, time: AccountTime): List<CachedTimeEntry> {
        if (pages.isEmpty()) return pages
        val ids = pages.mapTo(HashSet()) { it.id }
        var edits = edited
        for (row in cached) {
            val entry = row.entry
            if (entry.id !in ids) continue
            edits = if (time.local(entry.start).toLocalDate() < firstCached) edits + (entry.id to entry) else edits - entry.id
        }
        edited = edits
        return if (edits.isEmpty()) pages else pages.map { edits[it.id] ?: it }
    }

    /**
     * The older days loaded so far: their [entries], from the day [from] on, and what comes next.
     * They end the day before [until], the cache's first day when the first page loaded or the
     * server said no entry comes before it, and null until then.
     */
    private data class Older(
        val entries: List<CachedTimeEntry> = emptyList(),
        val from: LocalDate? = null,
        val until: LocalDate? = null,
        val status: OlderDays = OlderDays.More(auto = true),
    )

    init {
        viewModelScope.launch { overrides.collect(::reconcile) }
        viewModelScope.launch { appEvents.collect { if (it == AppEvent.ONLINE) reconnected() } }
    }

    companion object {
        /** How many days one request for older entries covers, as the web app's "Show 14 more days". */
        const val PAGE_DAYS = 14L

        /** How long the state keeps following the cache after the last screen stops reading it. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The first day of the cached two weeks, today and the 13 days before it. */
        private fun firstCachedDay(time: AccountTime): LocalDate = time.today().minusDays(HistoryWindow.DAYS - 1)
    }
}
