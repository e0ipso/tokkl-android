package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.toTimeEntry
import com.github.e0ipso.tokkl.capabilities.ProbedTimerState
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.model.TimeEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.time.Clock

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
 * The side of an account's timer state that the server feeds: [begin] before a timer request goes
 * out or a stream waits for its next event, and [accept] with what came back. The timer actions,
 * the event stream and the account refresh's probe take it, and nothing else of the state.
 */
interface TimerStateIntake : ProbedTimerState<TimerStateRead>

/**
 * The signed-in account's timer state from the Tokkl server, and the one place on the phone that
 * holds it. Answers to `GET /api/app/timer`, action answers, 409 bodies and timer events all go
 * through [accept], which coordinates the steps in order under one lock: the revision rule
 * ([acceptsRevision]), the write to disk ([HeldStateDisk]), the published state and its notice
 * ([DiscardNoticeSlot]), and the running entry's write to the cache through the same
 * current-entry reconciliation as the running entry check, against what the cache held at the
 * request with the writes of states accepted since ([RecentWrites], [withWrites]), so queued
 * Starts, Stops and edits keep showing until the server has them.
 *
 * One source serves one account. Sign-out [retire]s it before clearing the cache and the store,
 * and sign-in makes a new one, so an answer that kept a retired source changes nothing. Only
 * [AccountTimerStates] holds a source: readers take [CurrentTimerState], and what feeds it takes
 * the source as a [TimerStateIntake].
 *
 * @param initial what the store held at process start, or null.
 * @param store writes each accepted state to disk, and may throw an [IOException].
 * @param cache the cache's current-entry reconciliation.
 * @param clock the phone's clock. Arrival times come from it.
 * @param runningEnded asked for a refresh after an accepted state removed a running entry from
 *   the cache. The state does not say when that entry stopped, and only a refresh brings it back
 *   with its stop, so the day's totals keep counting it.
 */
class TimerStateSource(
    initial: StoredTimerState?,
    store: (StoredTimerState) -> Unit,
    private val cache: CurrentEntryCache,
    private val clock: Clock,
    private val runningEnded: () -> Unit,
) : TimerStateIntake {

    // A JVM lock, not a Mutex: answers and events arrive on OkHttp and IO threads, and flows read
    // the state, none of them suspending.
    /** Orders acceptance, storage, cache writes, notice taking and retirement. */
    private val lock = Any()

    private var retired = false
    private val disk = HeldStateDisk(store, initial?.wire)
    private val notices = DiscardNoticeSlot(initial)
    private val recentWrites = RecentWrites(RECENT_WRITES)
    private val mutableState = MutableStateFlow(initial?.held)

    /** The newest state with its arrival time: the stored one after a restart, null before any. */
    val state: StateFlow<HeldTimerState?> = mutableState.asStateFlow()

    /**
     * The too-short pomodoro whose notice no surface has taken yet, or null. Show it only after
     * [takeDiscardNotice] returns it.
     */
    val discardNotice: StateFlow<DiscardedPomodoro?> = notices.notice

    /**
     * Reads what the cache holds, for the [accept] of a state the server builds after this call.
     * Call it before sending a timer request. A stream calls it before it waits for each event,
     * so the next event's answer cannot undo a change the phone made in between. Blocks on disk
     * I/O.
     */
    override fun begin(): TimerStateRead {
        val writtenBefore = synchronized(lock) { recentWrites.count }
        return TimerStateRead(this, cache.runningCheckStart(), writtenBefore)
    }

    /**
     * Takes [state], read through [read], unless this source is retired, [read] belongs to
     * another source, or [acceptsRevision] refuses it. An accepted state is stored, published and
     * its running entry written to the cache, in that order and under one lock. Returns whether
     * it took the state. Blocks on disk I/O.
     */
    override fun accept(read: TimerStateRead, state: TimerState): Boolean {
        // Taken before waiting for the lock, so a wait does not skew the clock offset.
        val receivedAt = clock.instant()
        val ended = synchronized(lock) {
            if (retired || read.source !== this || !acceptsRevision(mutableState.value, state.revision)) return false
            val next = StoredHeld(state, receivedAt)
            disk.write(next, notices.acknowledged)
            val shown = next.toHeld()
            mutableState.value = shown
            notices.follow(shown)
            writeRunning(read, state)
        }
        if (ended) runningEnded()
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
        notices.take()?.also { disk.write(null, notices.acknowledged) }
    }

    /**
     * Ends this source's account: it forgets the state and takes no more. Waits for an [accept]
     * that is writing, so once it returns, clearing the store and the cache is final. Sign-out
     * calls this, then clears both.
     */
    fun retire() {
        synchronized(lock) {
            retired = true
            disk.forget()
            mutableState.value = null
            notices.follow(null)
        }
    }

    /**
     * Writes [state]'s running entry to the cache, against what the cache held at [read] with the
     * writes of states accepted since applied to it. Returns whether a running entry left the
     * cache. Call it holding [lock].
     */
    private fun writeRunning(read: TimerStateRead, state: TimerState): Boolean {
        val server: TimeEntry? = state.runningEntry?.let { entry ->
            // A running entry the phone cannot read leaves the cache as it is.
            entry.toTimeEntry() ?: return false
        }
        val before = read.before.withWrites(recentWrites.since(read.written))
        val writes = cache.checkedRunning(before, server) ?: return false
        if (writes.delete.isEmpty() && writes.upsert == null) return false
        recentWrites.add(writes)
        return writes.delete.isNotEmpty()
    }

    private companion object {
        /** Accepted writes kept for reads still out. */
        const val RECENT_WRITES = 32
    }
}

/**
 * Writes the newest state as the server sent it, with the entry id of the last notice taken,
 * through [store]. A failed write keeps the state for this process, and the next one writes again.
 */
private class HeldStateDisk(private val store: (StoredTimerState) -> Unit, private var stored: StoredHeld?) {

    /** Writes [held], or again the state written last when null, unless there is none or it was forgotten. */
    fun write(held: StoredHeld?, acknowledged: Long?) {
        val next = held ?: stored ?: return
        stored = next
        try {
            store(StoredTimerState(next, acknowledged))
        } catch (_: IOException) {
            // The next accepted state or taken notice writes again.
        }
    }

    fun forget() {
        stored = null
    }
}

/** The held state's too-short pomodoro until a surface takes its notice, and the last one taken. */
private class DiscardNoticeSlot(initial: StoredTimerState?) {

    /** The entry id of the last notice taken, which a replay or a restart does not show again. */
    var acknowledged: Long? = initial?.acknowledgedDiscard
        private set

    private val mutable = MutableStateFlow(initial?.held?.unshownDiscard(acknowledged))
    val notice: StateFlow<DiscardedPomodoro?> = mutable.asStateFlow()

    /** Shows the notice of [held], unless it was taken. */
    fun follow(held: HeldTimerState?) {
        mutable.value = held?.unshownDiscard(acknowledged)
    }

    fun take(): DiscardedPomodoro? {
        val notice = mutable.value ?: return null
        acknowledged = notice.entryId.value
        mutable.value = null
        return notice
    }
}
