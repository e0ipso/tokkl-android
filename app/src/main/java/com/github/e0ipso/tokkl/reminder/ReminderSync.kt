package com.github.e0ipso.tokkl.reminder

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
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
 * Pause (docs/PRD.md T1) goes to the server, which stops the entry, and that cancels its reminder.
 * Resume's new entry gets one from its own start.
 *
 * @param post shows the reminder for a timer. It decides then whether the reminder offers Pause.
 * @param pause the server's Pause of the running entry with this id, ready to send, or null when
 *   Pause is not offered for it now: without the server's timer API, or during a Pomodoro session.
 */
class ReminderSync(
    private val latest: suspend () -> ReminderInput,
    private val currentId: suspend (Long) -> Long,
    private val load: () -> ReminderTimer?,
    private val save: (ReminderTimer?) -> Unit,
    private val schedule: (ReminderTimer) -> Unit,
    private val cancel: () -> Unit,
    private val post: suspend (ReminderTimer) -> Unit,
    private val remove: () -> Unit,
    private val stop: suspend (Long) -> Boolean,
    private val pause: suspend (Long) -> SurfaceTap.Request? = { null },
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

    /**
     * Every action acts on exactly the timer shown, resolving an offline start's server id. Keep
     * running and Stop are done on the phone, and return null. Pause goes to the server after
     * [beforeRequest], outside the lock, and returns what it came to: once the server has it, the
     * reminder goes; otherwise, offline included, the reminder stays for another try. A Pause not
     * offered for the timer now sends nothing, keeps the reminder and returns null.
     */
    suspend fun act(
        id: Long,
        start: Long,
        stopTimer: Boolean,
        pauseTimer: Boolean = false,
        beforeRequest: () -> Unit = {},
    ): TimerActionResult? {
        val request = lock.withLock {
            val input = latest()
            val entry = input.running ?: return null
            if (entry.stop != null || entry.start.epochSecond != start || currentId(id) != entry.id) return null
            val timer = load() ?: return null
            if (timer.start != start || currentId(timer.id) != entry.id) return null
            if (pauseTimer) {
                pause(entry.id) ?: return null
            } else {
                if (stopTimer && !stop(entry.id)) return null
                handled(timer)
                return null
            }
        }
        beforeRequest()
        val result = request.send()
        if (result == TimerActionResult.Done) {
            lock.withLock {
                // The server's paused state may have ended the reminder already.
                load()?.takeIf { it.start == start && currentId(it.id) == currentId(id) }?.let(::handled)
            }
        }
        return result
    }

    private fun handled(timer: ReminderTimer) {
        save(timer.copy(handled = true))
        cancel(); remove()
    }

    suspend fun forget(): Unit = lock.withLock {
        cancel(); remove(); save(null)
    }
}
