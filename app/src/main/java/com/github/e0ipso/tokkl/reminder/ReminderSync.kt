package com.github.e0ipso.tokkl.reminder

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Identity is id plus start to the second, as the web app's LongTimerReminder.tsx. */
data class ReminderTimer(val id: Long, val start: Long, val due: Long, val handled: Boolean = false)
data class ReminderInput(val running: CachedTimeEntry?, val preferences: ReminderPreferences)

/**
 * Follows the shared running state. One delayed request, no periodic work and no network constraint.
 * Re-reads the cache inside the lock: queued flow emissions and old workers cannot revive an old timer.
 * The persisted handled flag survives process death, disable/enable and edits other than the start.
 */
class ReminderSync(
    private val latest: suspend () -> ReminderInput,
    private val currentId: suspend (Long) -> Long,
    private val load: () -> ReminderTimer?,
    private val save: (ReminderTimer?) -> Unit,
    private val schedule: (ReminderTimer) -> Unit,
    private val cancel: () -> Unit,
    private val post: (ReminderTimer) -> Unit,
    private val remove: () -> Unit,
    private val stop: suspend (Long) -> Boolean,
) {
    private val lock = Mutex()

    fun follow(changes: Flow<*>, scope: CoroutineScope, io: CoroutineContext) = scope.launch(io) {
        changes.collect {
            try {
                reconcile()
            } catch (e: IOException) {
                // Keep the old state on a failed disk write; the next running/settings event retries.
            }
        }
    }

    suspend fun reconcile() = lock.withLock { update(latest()) }

    private suspend fun update(input: ReminderInput): ReminderTimer? {
        val entry = input.running?.takeIf { it.stop == null }
        if (entry == null) {
            cancel(); remove(); save(null)
            return null
        }
        val previous = load()
        val same = previous != null && currentId(previous.id) == entry.id && previous.start == entry.start.epochSecond
        val due = entry.start.epochSecond * 1000 + Duration.ofHours(input.preferences.clamped().hours.toLong()).toMillis()
        val timer = if (same) previous.copy(due = due) else ReminderTimer(entry.id, entry.start.epochSecond, due)
        if (!same || previous.due != due) {
            cancel(); remove()
        }
        save(timer)
        if (!input.preferences.enabled || timer.handled) cancel() else schedule(timer)
        if (!input.preferences.enabled) remove()
        return timer.takeIf { input.preferences.enabled }
    }

    /** A worker may arrive late, after cancellation, a start correction, sign-out or a switch. */
    suspend fun fire(id: Long, start: Long, due: Long): Unit = lock.withLock {
        val timer = update(latest()) ?: return@withLock
        if (timer.start != start || currentId(id) != currentId(timer.id) || timer.handled || timer.due != due) return@withLock
        // Persist first, so another process or a second delivery never posts it twice.
        save(timer.copy(handled = true))
        post(timer)
    }

    /** Both actions act on exactly the timer shown, resolving an offline start's server id. */
    suspend fun act(id: Long, start: Long, stopTimer: Boolean): Unit = lock.withLock {
        val input = latest()
        val entry = input.running ?: return@withLock
        if (entry.stop != null || entry.start.epochSecond != start || currentId(id) != entry.id) return@withLock
        val timer = load() ?: return@withLock
        if (timer.start != start || currentId(timer.id) != entry.id) return@withLock
        if (stopTimer && !stop(entry.id)) return@withLock
        save(timer.copy(handled = true))
        cancel(); remove()
    }

    suspend fun forget(): Unit = lock.withLock {
        cancel(); remove(); save(null)
    }
}
