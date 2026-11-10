package com.github.e0ipso.tokkl.reminder

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.collectDurably
import com.github.e0ipso.tokkl.preferences.ReminderPreferences
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ValueStore
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/** Identity is id plus start to the second, as the web app's LongTimerReminder.tsx. */
data class ReminderTimer(val id: Long, val start: Long, val due: Long, val handled: Boolean = false)
data class ReminderInput(val running: TimeEntry?, val preferences: ReminderPreferences)

/** The one delayed wakeup at a timer's reminder time, such as [ReminderWork]. */
interface ReminderDelays {
    fun schedule(timer: ReminderTimer)

    fun cancel()
}

interface ReminderNotice {

    /** Shows the reminder for [timer], deciding then whether it offers Pause. */
    suspend fun post(timer: ReminderTimer)

    fun remove()
}

/**
 * What the reminder reads and does for the signed-in account. [AccountReminder] does it over the
 * cache, the queue and the timer's surfaces; the unit tests answer in memory.
 */
interface ReminderAccount {

    suspend fun input(): ReminderInput

    suspend fun stop(entryId: EntryId): Boolean

    /**
     * The server's Pause of the running entry with this id, ready to send, or null when Pause is
     * not offered for it now: without the server's timer API, or during a Pomodoro session.
     */
    suspend fun pause(entryId: EntryId): SurfaceTap.Request?
}

/**
 * Follows the shared running state. One delayed request, no periodic work and no network constraint.
 * Re-reads the cache inside the lock: queued flow emissions and old workers cannot revive an old timer.
 * The persisted handled flag survives process death, disable/enable and edits other than the start.
 * Pause (docs/PRD.md T1) goes to the server, which stops the entry, and that cancels its reminder.
 * Resume's new entry gets one from its own start.
 */
class ReminderSync(
    private val account: ReminderAccount,
    private val currentId: suspend (EntryId) -> EntryId,
    private val timers: ValueStore<ReminderTimer?>,
    private val delays: ReminderDelays,
    private val notice: ReminderNotice,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = forget()

    private val lock = Mutex()

    /** A failed read of the changes or a failed reconcile tries again after a wait, as [collectDurably] does. */
    fun follow(changes: Flow<*>, scope: CoroutineScope, io: CoroutineContext) = scope.launch(io) {
        changes.collectDurably("ReminderSync") { reconcile() }
    }

    suspend fun reconcile() = lock.withLock { update(account.input()) }

    private suspend fun update(input: ReminderInput): ReminderTimer? {
        val entry = input.running?.takeIf { it.stop == null }
        if (entry == null) {
            delays.cancel()
            notice.remove()
            timers.save(null)
            return null
        }
        val previous = timers.load()
        val same = previous != null && currentId(
            EntryId(previous.id),
        ) == entry.id && previous.start == entry.start.epochSecond
        val due = entry.start.epochSecond * 1000 + Duration.ofHours(
            input.preferences.clamped().hours.toLong(),
        ).toMillis()
        val timer = if (same) previous.copy(due = due) else ReminderTimer(entry.id.value, entry.start.epochSecond, due)
        if (!same || previous.due != due) {
            delays.cancel()
            notice.remove()
        }
        timers.save(timer)
        if (!input.preferences.enabled || timer.handled) delays.cancel() else delays.schedule(timer)
        if (!input.preferences.enabled) notice.remove()
        return timer.takeIf { input.preferences.enabled }
    }

    /** A worker may arrive late, after cancellation, a start correction, sign-out or a switch. */
    suspend fun fire(id: Long, start: Long, due: Long): Unit = lock.withLock {
        val timer = update(account.input()) ?: return@withLock
        val same = timer.start == start && currentId(
            EntryId(id),
        ) == currentId(EntryId(timer.id)) && !timer.handled
        if (!same || timer.due != due) return@withLock
        // Persist first, so another process or a second delivery never posts it twice.
        timers.save(timer.copy(handled = true))
        notice.post(timer)
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
            val input = account.input()
            val entry = input.running ?: return null
            if (entry.stop != null || entry.start.epochSecond != start || currentId(
                    EntryId(id),
                ) != entry.id
            ) {
                return null
            }
            val timer = timers.load() ?: return null
            if (timer.start != start || currentId(EntryId(timer.id)) != entry.id) return null
            if (pauseTimer) {
                account.pause(entry.id) ?: return null
            } else {
                if (stopTimer && !account.stop(entry.id)) return null
                handled(timer)
                return null
            }
        }
        beforeRequest()
        val result = request.send()
        if (result == TimerActionResult.Done) lock.withLock { paused(id, start) }
        return result
    }

    /** The server has paused timer [id] from [start]: its reminder is handled, unless it ended already. */
    private suspend fun paused(id: Long, start: Long) {
        val timer = timers.load() ?: return
        if (timer.start == start && currentId(EntryId(timer.id)) == currentId(EntryId(id))) handled(timer)
    }

    private suspend fun handled(timer: ReminderTimer) {
        timers.save(timer.copy(handled = true))
        delays.cancel()
        notice.remove()
    }

    suspend fun forget(): Unit = lock.withLock {
        delays.cancel()
        notice.remove()
        timers.save(null)
    }
}

/** Follows the running entry's reminder: after the running and Pomodoro notifications, before the widgets. */
class ReminderHook(
    private val reminder: Lazy<ReminderSync>,
    private val settings: Lazy<ReminderSetting>,
    private val cache: Lazy<AccountCache>,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AppHook {
    override fun processStart() {
        reminder.value.follow(
            combine(cache.value.running, settings.value.loaded) { running, preferences -> running to preferences },
            scope,
            io,
        )
    }
}
