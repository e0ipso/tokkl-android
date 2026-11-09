package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.resolveEntryOverrides
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * The signed-in session's one owner of the entries that the Timer page's history and calendar and
 * the entry form show (docs/PRD.md H1, H2, K1), a repository that lives as long as the account
 * does. It merges the cache's entries with what the server sent, the history's older days
 * ([OlderDaysPaging]) and the calendar's weeks ([WeekLoading]), which live in memory and are never
 * written to the phone. It keeps the copies of those entries edited on the phone, which the cache
 * holds only until the send and the refresh after it, and the history's deletes and restores until
 * the cache shows them. The history and the calendar select and format what [history] and [week]
 * give, and the entry form saves an edit through [saveEdit]. Each reads it through its own
 * contract: [HistoryEntriesSource], [CalendarWeeks], [PastEntryEdits] and [EntryRemovals].
 *
 * What it keeps beside the cache, a [Reconciliation], changes only through
 * [MutableStateFlow.update] in its commands: a read of the cache, a delete, a restore, a week that
 * loaded, and the forgetting below. One reader of the cache in [scope] runs while anything reads
 * the entries, and [history] and [week] are pure functions of what it keeps, the older days and
 * the weeks, so a second reader writes nothing.
 *
 * A sign-in again over a refused token, which can be another account, drops what the server sent.
 * Sign-out forgets everything ([forgetAccount]).
 *
 * @param cached the cached entries with their send state, as AccountCache.entries gives them.
 * @param changes the change queue: where the cache holds an entry now, and where edits go.
 * @param olderPages the history's days before the cache, from the server. Runs on [io].
 * @param serverWeeks the calendar's weeks outside the cache, from the server. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 * @param app the app's trips to the background and back, and the network's returns.
 * @param accounts the number of the account context signed in now. Each sign-in changes it,
 *   which drops what the server sent.
 * @param scope the session's scope, on the main thread, where the loads start.
 * @param computation where the entries are merged.
 */
class SessionEntries(
    private val cached: Flow<List<EntryWithSendState>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    olderPages: OlderPages,
    serverWeeks: ServerWeeks,
    private val clock: Clock,
    app: AppForeground,
    accounts: Flow<Int>,
    scope: CoroutineScope,
    io: CoroutineContext,
    computation: CoroutineContext,
) : HistoryEntriesSource,
    CalendarWeeks,
    PastEntryEdits,
    EntryRemovals,
    AccountDataOwner {

    private val paging = OlderDaysPaging(olderPages, accountSettings, clock, scope, io)
    private val weeks = WeekLoading(serverWeeks, accountSettings, clock, scope, io)
    private val state = MutableStateFlow(Reconciliation())

    /** [state], with one reader of the cache keeping it up to date while anything reads it. */
    private val followed: SharedFlow<Reconciliation> = channelFlow {
        launch { combine(cached, accountSettings, ::Pair).collect { (rows, settings) -> cacheRead(rows, settings) } }
        state.collect { send(it) }
    }.flowOn(computation).shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    override val history: StateFlow<HistoryEntries> = combine(followed, paging.state) { kept, older ->
        kept.history(older, resolveEntryOverrides(kept.overrides, changes), clock)
    }.flowOn(computation).stateIn(scope, SharingStarted.WhileSubscribed(), HistoryEntries.Loading)

    override val foregrounds: StateFlow<Int> = weeks.foregrounds

    override val signOutPhase: SignOutPhase get() = SignOutPhase.CLEAR_DATA

    override fun entry(entryId: EntryId): TimeEntry? = when (val shown = history.value) {
        is HistoryEntries.Shown -> shown.current.firstOrNull { it.entry.id == entryId }?.entry
            ?: shown.older.firstOrNull { it.id == entryId }

        else -> null
    }

    override fun loadOlder() = paging.loadOlder()

    override fun week(selected: Flow<LocalDate?>): Flow<WeekEntries> =
        combine(followed, weeks.state, selected) { kept, loaded, day ->
            kept.week(loaded, resolveEntryOverrides(kept.overrides, changes), day, clock)
        }

    override fun loadWeek(weekStart: LocalDate, selected: StateFlow<LocalDate?>) {
        // The server has every edit that the cache no longer holds, so this load's copies replace them.
        val sent = state.value.weekEdits.filterValues { !it.held }
        weeks.load(weekStart, selected) { week -> state.update { it.weekLoaded(week, sent) } }
    }

    override fun weekEntry(id: EntryId): TimeEntry? = state.value.weekEntry(id, weeks.state.value, clock)

    override suspend fun moveWeekEntry(id: EntryId, start: Instant, stop: Instant) {
        val held = cached.first().firstOrNull { it.entry.id == id }?.entry
        when {
            held != null -> changes.update(held.id, held.fields(), start, stop)
            else -> weekEntry(id)?.let { changes.updateOlder(it.id, it.workspaceId, it.fields(), start, stop) }
        }
    }

    override fun deleted(entry: TimeEntry) = state.update { it.deleted(entry) }

    override suspend fun restored(entryId: EntryId) {
        val id = changes.currentId(entryId)
        val restored = cached.first().firstOrNull { it.entry.id == id }?.entry
            ?: state.value.deleted[entryId]?.copy(id = id)
        state.update { it.overridden(entryId, restored) }
    }

    override suspend fun saveEdit(
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

    /** Forgets the account's entries: what the server sent, the copies kept, the deletes and restores. */
    override suspend fun forgetAccount() {
        paging.forget()
        weeks.forget()
        state.update { Reconciliation(rows = it.rows, settings = it.settings) }
    }

    /** The cache read [rows] with [settings]: the copies kept of its entries follow. */
    private suspend fun cacheRead(rows: List<EntryWithSendState>, settings: AccountSettings?) {
        val restoredIds = resolveEntryOverrides(state.value.overrides, changes).values
            .mapNotNullTo(HashSet()) { it?.id }
        state.update { it.cacheRead(rows, settings, clock, paging.state.value, weeks.state.value, restoredIds) }
    }

    /** Drops what the server sent, and the copies kept of its entries, for the account signing in. */
    private fun forgetServer() {
        paging.forget()
        weeks.forget()
        state.update { it.forgotServer() }
    }

    init {
        scope.launch {
            app.reconnects.collect {
                paging.reconnected()
                weeks.reconnected()
            }
        }
        scope.launch {
            app.foreground.drop(1).collect { foreground ->
                if (foreground) weeks.cameToForeground() else weeks.wentToBackground()
            }
        }
        scope.launch { accounts.drop(1).collect { forgetServer() } }
    }
}
