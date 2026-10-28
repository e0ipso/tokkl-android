package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.cache.toCached
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the cache held when a timer state request went out or a stream began waiting for its next
 * event, from [TimerStateSource.begin]. It belongs to the source that made it, so it also carries
 * the account: a read kept from a retired source counts for nothing.
 */
class TimerStateRead internal constructor(
    internal val source: TimerStateSource,
    internal val before: RunningCheckStart,
    internal val written: Long,
)

/**
 * The signed-in account's timer state from the Tokkl server, and the one place on the phone that
 * holds it. Answers to `GET /api/app/timer`, action answers, 409 bodies and timer events all go
 * through [accept], which keeps the newest revision with its arrival time, stores it, and writes
 * its running entry to the cache through the same current-entry reconciliation as the running
 * entry check, so queued Starts, Stops and edits keep showing until the server has them.
 *
 * One source serves one account. Sign-out [retire]s it before clearing the cache and the store,
 * and sign-in makes a new one, so an answer that kept a retired source changes nothing.
 *
 * @param initial what the store held at process start, or null.
 * @param save writes the state to the store, throwing [IOException] when it cannot.
 * @param cache the cache's current-entry reconciliation.
 * @param clock the phone's clock. Arrival times come from it.
 * @param onRunningEnded called after an accepted state removed a running entry from the cache.
 *   The state does not say when that entry stopped, and only a refresh brings it back with its
 *   stop, so the day's totals keep counting it.
 */
class TimerStateSource(
    initial: StoredTimerState?,
    private val save: (StoredTimerState) -> Unit,
    private val cache: CurrentEntryCache,
    private val clock: Clock = Clock.systemUTC(),
    private val onRunningEnded: () -> Unit = {},
) {

    /** Orders acceptance, storage, cache writes, notice taking and retirement. */
    private val lock = Any()

    private var retired = false
    private var acknowledged: Long? = initial?.acknowledgedDiscard

    /** How many accepted states wrote to the cache, and the latest of those writes. */
    private var written = 0L
    private val recentWrites = ArrayDeque<Pair<Long, RunningCheckWrites>>()

    private val mutableState = MutableStateFlow(initial?.held)
    private val mutableNotice = MutableStateFlow(initial?.held?.unshownDiscard(acknowledged))

    /** The newest state with its arrival time: the stored one after a restart, null before any. */
    val state: StateFlow<HeldTimerState?> = mutableState.asStateFlow()

    /**
     * The too-short pomodoro whose notice no surface has taken yet, or null. Show it only after
     * [takeDiscardNotice] returns it.
     */
    val discardNotice: StateFlow<DiscardedPomodoro?> = mutableNotice.asStateFlow()

    /**
     * Reads what the cache holds, for the [accept] of a state the server builds after this call.
     * Call it before sending a timer request. A stream calls it before it waits for each event,
     * so the next event's answer cannot undo a change the phone made in between. Blocks on disk
     * I/O.
     */
    fun begin(): TimerStateRead {
        val writtenBefore = synchronized(lock) { written }
        return TimerStateRead(this, cache.runningCheckStart(), writtenBefore)
    }

    /**
     * Takes [state], read through [read], unless this source is retired, [read] belongs to
     * another source, or a state with a higher revision is already held. A state with the held
     * revision replaces it, which refreshes the arrival time and the clock offset. An accepted
     * state is stored, published and its running entry written to the cache, in that order and
     * under one lock, so a slower answer with a lower revision cannot overwrite a newer one.
     * Returns whether it took the state. Blocks on disk I/O.
     */
    fun accept(read: TimerStateRead, state: TimerState): Boolean {
        // Taken before waiting for the lock, so a wait does not skew the clock offset.
        val receivedAt = clock.instant()
        val ended = synchronized(lock) {
            if (retired || read.source !== this) return false
            val held = mutableState.value
            if (held != null && state.revision < held.state.revision) return false
            val next = HeldTimerState(state, receivedAt)
            store(next)
            mutableState.value = next
            mutableNotice.value = next.unshownDiscard(acknowledged)
            writeRunning(read, state)
        }
        if (ended) onRunningEnded()
        return true
    }

    /**
     * Calls [read] with the held state while no [accept] is between publishing a state and
     * writing its running entry to the cache, so a cache read inside [read] matches the state it
     * gets. The shared timer controls read both this way, so a resumed clone never shows as
     * nothing running for the moment between the two writes. Keep [read] short: an accept waits
     * for it. Blocks on disk I/O when [read] reads the cache.
     */
    fun <T> withState(read: (HeldTimerState?) -> T): T = synchronized(lock) { read(mutableState.value) }

    /**
     * The too-short pomodoro notice, for the one surface that shows it, or null when there is none
     * or another surface took it. Records its entry id with the state, so a replay of the same
     * revision or a restart does not show it again.
     */
    fun takeDiscardNotice(): DiscardedPomodoro? = synchronized(lock) {
        if (retired) return null
        val notice = mutableNotice.value ?: return null
        acknowledged = notice.entryId
        mutableNotice.value = null
        mutableState.value?.let(::store)
        notice
    }

    /**
     * Ends this source's account: it forgets the state and takes no more. Waits for an [accept]
     * that is writing, so once it returns, clearing the store and the cache is final. Sign-out
     * calls this, then clears both.
     */
    fun retire() {
        synchronized(lock) {
            retired = true
            mutableState.value = null
            mutableNotice.value = null
        }
    }

    /** A failed write keeps the state for this process, and the next state writes again. */
    private fun store(held: HeldTimerState) {
        try {
            save(StoredTimerState(held, acknowledged))
        } catch (e: IOException) {
            // The next accepted state or taken notice writes again.
        }
    }

    /**
     * Writes [state]'s running entry to the cache, against what the cache held at [read] with the
     * writes of states accepted since applied to it. Those states are older than this one, so
     * their rows count as server values, not as changes made during the read. Returns whether a
     * running entry left the cache. Call it holding [lock].
     */
    private fun writeRunning(read: TimerStateRead, state: TimerState): Boolean {
        val server: CachedTimeEntry? = state.runningEntry?.let { entry ->
            // A running entry the phone cannot read leaves the cache as it is.
            entry.toCached() ?: return false
        }
        val since = recentWrites.filter { (number, _) -> number > read.written }.map { it.second }
        val writes = cache.checkedRunning(read.before.withWrites(since), server) ?: return false
        if (writes.delete.isEmpty() && writes.upsert == null) return false
        written++
        recentWrites.addLast(written to writes)
        while (recentWrites.size > RECENT_WRITES) recentWrites.removeFirst()
        return writes.delete.isNotEmpty()
    }

    private companion object {
        /**
         * Accepted writes kept for reads still out. A read older than these sees the rows of the
         * forgotten writes as changes and leaves them alone, which errs towards keeping the cache.
         */
        const val RECENT_WRITES = 32
    }
}

/** The discarded pomodoro of this state, unless its notice was taken as [acknowledged]. */
private fun HeldTimerState.unshownDiscard(acknowledged: Long?): DiscardedPomodoro? =
    state.discardedPomodoro?.takeIf { it.entryId != acknowledged }

/** These running entries as they would read had [writes] happened before them. */
private fun RunningCheckStart.withWrites(writes: List<RunningCheckWrites>): RunningCheckStart {
    if (writes.isEmpty()) return this
    val rows = LinkedHashMap<Long, CachedTimeEntry>()
    running.forEach { rows[it.id] = it }
    for (write in writes) {
        write.delete.forEach(rows::remove)
        write.upsert?.let { rows[it.id] = it }
    }
    return RunningCheckStart(rows.values.toList(), chain, pendingIds)
}
