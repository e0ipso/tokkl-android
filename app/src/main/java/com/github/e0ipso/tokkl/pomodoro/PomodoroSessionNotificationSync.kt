package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The session notification of the server's Pomodoro session, from the held timer state, and its
 * buttons. It posts the held phase with its countdown, number and upcoming phase, replaces it on
 * each new revision, and removes it when the session ends, the server loses the timer API or the
 * account signs out. Once the phase's planned end has come, the ringing notification owns the
 * shared id.
 *
 * Next phase and End session go to the server with the revision the notification showed, and
 * only for the account and the phase it was posted for. All publication and actions belong to one
 * account for this observer's lifetime; sign-out [forget]s it.
 *
 * @param alarm the phase alarm, which holds the held state and knows whether its phase rings.
 * @param actions the server's session actions.
 * @param changes emits on every change of the held state, the capability or the ringing service.
 */
internal class PomodoroSessionNotificationSync(
    private val alarm: PomodoroPhaseAlarm,
    private val actions: SessionActions,
    private val changes: Flow<Unit>,
    private val signedIn: () -> Boolean,
    private val serviceRunning: () -> Boolean,
    private val load: () -> PomodoroNotificationDisplay?,
    private val save: (PomodoroNotificationDisplay?) -> Unit,
    private val post: (PomodoroSessionNotificationContent, String) -> Boolean,
    private val remove: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {
    private val lock = Mutex()
    private var display: PomodoroNotificationDisplay? = null
    private var loaded = false
    private var job: Job? = null
    @Volatile private var retired = false

    @Synchronized fun start() {
        if (retired || job?.isActive == true) return
        job = scope.launch(io) { changes.collect { refresh() } }
    }

    private fun readDisplay(): PomodoroNotificationDisplay {
        if (!loaded) {
            display = load()
            loaded = true
        }
        return display ?: PomodoroNotificationDisplay(UUID.randomUUID().toString()).also { display = it }
    }

    private fun remember(next: PomodoroNotificationDisplay) {
        display = next
        try {
            save(next)
        } catch (_: IOException) {
            // Keep observing; the next ordinary emission can persist the shown state again.
            // Forget uses the throwing save directly so sign-out reports a failed cleanup.
        }
    }

    /**
     * Posts, replaces or removes the notification for the held state now. It reads that state
     * under the alarm's lock, so it never posts over a ring that starts meanwhile. [force] posts
     * again what was shown, as after a notification permission grant.
     */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        if (retired) return@withLock
        alarm.withState { held, ringing ->
            if (retired) return@withState
            if (!signedIn()) {
                display = null
                loaded = true
                remove()
                try { save(null) } catch (_: IOException) { /* Try again on an ordinary emission. */ }
                return@withState
            }
            val state = readDisplay()
            val content = held?.notificationContent()
            if (content == null) {
                remove()
                remember(state.copy(shown = null))
            } else if (ringing || serviceRunning()) {
                // ID 3 is now owned by the ring, including a service still stopping. Never
                // cancel it here. The service-state emission republishes after onDestroy.
                remember(state.copy(shown = null))
            } else if (force || state.shown != content.key) {
                // A denied permission leaves this eligible for the next permission grant.
                if (post(content, state.account)) remember(state.copy(shown = content.key))
            } else {
                remember(state)
            }
        }
    }

    /**
     * Sends a button's Next phase, or End session when [end], at the [revision] it showed. A button
     * of another account, or of a phase that is no longer the held one, sends nothing and returns
     * null. Otherwise returns what the server action came to.
     */
    suspend fun act(account: String, phase: PomodoroPhaseKey, revision: Long, end: Boolean): TimerActionResult? {
        val accepted = lock.withLock { !retired && signedIn() && account == readDisplay().account }
        if (!accepted) return null
        return alarm.sendPhaseAction(actions, phase, revision, end)
    }

    /** Whether a tap on the notification of [account] and [phase] may open the Pomodoro page. */
    suspend fun acceptsTap(account: String, phase: PomodoroPhaseKey): Boolean = lock.withLock {
        if (retired || !signedIn() || account != readDisplay().account) return@withLock false
        alarm.current()?.phase == phase
    }

    /** Fence first, await collectors, and erase display identity before cache clearing. */
    suspend fun forget() {
        val observer = synchronized(this) { retired = true; job }
        observer?.cancelAndJoin()
        lock.withLock {
            display = null
            loaded = true
            try { remove() } finally { save(null) }
        }
    }
}
