package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry

/**
 * Whether a state of [revision] replaces [held]: a lower revision never does, so a slower answer
 * cannot overwrite a newer one, and the held revision does again, which refreshes its arrival time
 * and the clock offset.
 */
internal fun acceptsRevision(held: HeldTimerState?, revision: Long): Boolean = held == null || revision >= held.revision

/** The discarded pomodoro of this state, unless its notice was taken as [acknowledged]. */
internal fun HeldTimerState.unshownDiscard(acknowledged: Long?): DiscardedPomodoro? =
    discardedPomodoro?.takeIf { it.entryId.value != acknowledged }

/**
 * These running entries as they would read had [writes] happened before them: what the cache held
 * when an answer's request went out, with the writes of the states accepted since applied, as the
 * rows of states older than the answer.
 */
internal fun RunningCheckStart.withWrites(writes: List<RunningCheckWrites>): RunningCheckStart {
    if (writes.isEmpty()) return this
    val rows = LinkedHashMap<EntryId, TimeEntry>()
    running.forEach { rows[it.id] = it }
    for (write in writes) {
        write.delete.forEach(rows::remove)
        write.upsert?.let { rows[it.id] = it }
    }
    return RunningCheckStart(rows.values.toList(), pendingIds)
}

/**
 * The cache writes of the last [limit] accepted states, numbered from 1, so an answer is reconciled
 * against what its read missed: [since] the [count] its read saw. A read older than these sees the
 * rows of the forgotten writes as changes and leaves them alone, which errs towards keeping the
 * cache.
 */
internal class RecentWrites(private val limit: Int) {
    private val writes = ArrayDeque<Pair<Long, RunningCheckWrites>>()

    var count = 0L
        private set

    fun add(write: RunningCheckWrites) {
        count++
        writes.addLast(count to write)
        while (writes.size > limit) writes.removeFirst()
    }

    /** The writes added after the first [seen], oldest first. */
    fun since(seen: Long): List<RunningCheckWrites> = writes.filter { (number, _) -> number > seen }.map { it.second }
}
