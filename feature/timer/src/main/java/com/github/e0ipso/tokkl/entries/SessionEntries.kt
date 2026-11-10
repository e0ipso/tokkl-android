package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.resolveEntryOverrides
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
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
 * the cache shows them.
 *
 * What it keeps beside the cache, a [Reconciliation], changes only through
 * [MutableStateFlow.update] in its commands: a read of the cache, a delete, a restore, a week that
 * loaded, and the forgetting below. One reader of the cache in [scope] runs while anything reads
 * the entries, and [history] and [week] are pure functions of what it keeps, the older days and
 * the weeks, so a second reader writes nothing.
 *
 * A sign-in again over a refused token, which can be another account, drops what the server sent
 * and the deletes and restores made before it. Sign-out forgets everything ([forgetAccount]).
 *
 * @param olderPages the history's days before the cache, from the server. Runs on [io].
 * @param serverWeeks the calendar's weeks outside the cache, from the server. Runs on [io].
 * @param accounts the number of the account context signed in now. Each sign-in changes it,
 *   which drops what the server sent and the deletes and restores.
 * @param scope the session's scope, on the main thread, where the loads start.
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
) : AccountDataOwner {

    private val paging = OlderDaysPaging(olderPages, accountSettings, clock, scope, io)
    private val weeks = WeekLoading(serverWeeks, accountSettings, clock, scope, io)
    private val state = MutableStateFlow(Reconciliation())

    /** [state], with one reader of the cache keeping it up to date while anything reads it. */
    private val followed: SharedFlow<Reconciliation> = channelFlow {
        launch { combine(cached, accountSettings, ::Pair).collect { (rows, settings) -> cacheRead(rows, settings) } }
        state.collect { send(it) }
    }.flowOn(computation).shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    val history: StateFlow<HistoryEntries> = combine(followed, paging.state) { kept, older ->
        kept.history(older, resolveEntryOverrides(kept.overrides, changes), clock)
    }.flowOn(computation).stateIn(scope, SharingStarted.WhileSubscribed(), HistoryEntries.Loading)

    val foregrounds: StateFlow<Int> = weeks.foregrounds

    override val signOutPhase: SignOutPhase get() = SignOutPhase.CLEAR_DATA

    /**
     * The entry [entryId] as the history last showed it: the cache's copy, or the older days' for
     * an entry the phone does not hold. Null for an entry the history does not show.
     */
    fun entry(entryId: EntryId): TimeEntry? = when (val shown = history.value) {
        is HistoryEntries.Shown -> shown.current.firstOrNull { it.entry.id == entryId }?.entry
            ?: shown.older.firstOrNull { it.id == entryId }

        else -> null
    }

    /**
     * Loads the [OlderPages.PAGE_DAYS] days before the oldest day loaded, or before the cached two
     * weeks at first. Does nothing while a page loads, once the server has no older entries, or
     * without an account timezone. After a failure it tries the same days again. Call it on the
     * main thread.
     */
    fun loadOlder() = paging.loadOlder()

    /** The calendar's entries for the week of the day [selected] picks, or of today while it is null. */
    fun week(selected: Flow<LocalDate?>): Flow<WeekEntries> =
        combine(followed, weeks.state, selected) { kept, loaded, day ->
            kept.week(loaded, resolveEntryOverrides(kept.overrides, changes), day, clock)
        }

    /**
     * Loads the server's part of the week from [weekStart], unless it is loading or has loaded
     * since the app last came to the foreground: the whole week outside the cache, or the entries
     * from before the cache for a cached week. A failure while an event that would load the week
     * came in tries once more while [selected] still shows it. Call it on the main thread.
     */
    fun loadWeek(weekStart: LocalDate, selected: StateFlow<LocalDate?>) {
        // The server has every edit that the cache no longer holds, so this load's copies replace them.
        val sent = state.value.weekEdits.filterValues { !it.held }
        weeks.load(weekStart, selected) { week -> state.update { it.weekLoaded(week, sent) } }
    }

    /**
     * The entry [id] as the server sent it for a week the phone does not keep, or null. The entry
     * form opens on this copy of an entry the cache lacks.
     */
    fun weekEntry(id: EntryId): TimeEntry? = state.value.weekEntry(id, weeks.state.value, clock)

    /**
     * Queues the calendar's entry [id] from [start] to [stop], with every other field as the phone
     * or the server has it: the cache's row, or the whole entry from a week the server sent.
     */
    suspend fun moveWeekEntry(id: EntryId, start: Instant, stop: Instant) {
        val held = cached.first().firstOrNull { it.entry.id == id }?.entry
        when {
            held != null -> changes.update(held.id, held.fields(), EntryTiming.Stopped(start, stop))

            else -> weekEntry(id)?.let {
                changes.updateOlder(it.id, it.workspaceId, it.fields(), EntryTiming.Stopped(start, stop))
            }
        }
    }

    /** [entry] was deleted on the phone. The history and the calendar leave it out from now on. */
    fun deleted(entry: TimeEntry) = state.update { it.deleted(entry) }

    /**
     * The delete of [entryId] was taken back. The history and the calendar show the cache's row
     * again, or the copy that was deleted, under the id the cache holds it by now.
     */
    suspend fun restored(entryId: EntryId) {
        val id = changes.currentId(entryId)
        val restored = cached.first().firstOrNull { it.entry.id == id }?.entry
            ?: state.value.deleted[entryId]?.copy(id = id)
        state.update { it.overridden(entryId, restored) }
    }

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
    ): Boolean {
        if (onPhone && changes.update(entryId, fields, timing)) return true
        val time = accountSettings.value?.time(clock)
        if (onPhone && (time == null || opened >= HistoryWindow.endingToday(time).start)) return false
        changes.updateOlder(entryId, workspaceId, fields, timing)
        return true
    }

    override suspend fun forgetAccount() = forgetServer()

    /** The cache read [rows] with [settings]: the copies kept of its entries follow. */
    private suspend fun cacheRead(rows: List<EntryWithSendState>, settings: AccountSettings?) {
        val restoredIds = resolveEntryOverrides(state.value.overrides, changes).values
            .mapNotNullTo(HashSet()) { it?.id }
        state.update { it.cacheRead(rows, settings, clock, paging.state.value, weeks.state.value, restoredIds) }
    }

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
