package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.platform.SingleFlight
import com.github.e0ipso.tokkl.session.SignedInCheck
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/** Whether the Pomodoro ringing service runs now. */
fun interface RingingServiceCheck {
    fun running(): Boolean
}

/** Keeps which account the session notification was last shown to, and what it showed. */
interface PomodoroNotificationMemory {
    fun load(): PomodoroNotificationDisplay?

    @Throws(IOException::class)
    fun save(display: PomodoroNotificationDisplay?)
}

/** Posts the session notification for an account, or removes it. */
interface PomodoroSessionPoster {

    /** Posts [content] for [account], and returns false when notifications are off. */
    fun post(content: PomodoroSessionNotificationContent, account: String): Boolean

    fun remove()
}

/**
 * The session notification of the server's Pomodoro session, from the held timer state, and its
 * buttons. It posts the held phase with its countdown, number and upcoming phase, replaces it on
 * each new revision, and removes it when the session ends, the server loses the timer API or the
 * account signs out. Once the phase's planned end has come, the ringing notification owns the
 * shared id.
 *
 * Next phase and End session go to the server with the revision the notification showed, and
 * only for the account and the phase it was posted for. Publication and actions belong to the
 * account signed in: sign-out [forget]s it, after which it shows and sends nothing until the next
 * account [signedIn], which it follows under a new identity, so no button of the account before
 * acts on it.
 *
 * @param alarm the phase alarm, which holds the held state and knows whether its phase rings.
 * @param actions the server's session actions.
 * @param changes emits on every change of the held state, the capability or the ringing service.
 * @param serviceRunning whether the ringing service runs, which then owns the shared id.
 * @param memory what the last process showed, for the account it showed it to.
 * @param notifications posts and removes the session notification.
 */
class PomodoroSessionNotificationSync internal constructor(
    private val alarm: PomodoroPhaseAlarm,
    private val actions: SessionActions,
    private val changes: Flow<Unit>,
    private val session: SignedInCheck,
    private val serviceRunning: RingingServiceCheck,
    private val memory: PomodoroNotificationMemory,
    private val notifications: PomodoroSessionPoster,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : SignedInListener {
    private val lock = Mutex()
    private var display: PomodoroNotificationDisplay? = null
    private var loaded = false
    private val observing = SingleFlight(scope, io)

    @Volatile private var retired = false

    // A JVM lock, not a Mutex: process start and sign-in call these without suspending; lock orders
    // the suspending work.
    @Synchronized fun start() {
        if (retired) return
        observing.launch { changes.collect { refresh() } }
    }

    /**
     * Starts following the account that has just signed in. After a sign-out it shows nothing
     * yet and makes a new identity on first use, as the first process after a sign-out does.
     */
    @Synchronized override fun signedIn() {
        if (retired) {
            display = null
            loaded = true
            retired = false
        }
        start()
    }

    private fun readDisplay(): PomodoroNotificationDisplay {
        if (!loaded) {
            display = memory.load()
            loaded = true
        }
        return display ?: PomodoroNotificationDisplay(UUID.randomUUID().toString()).also { display = it }
    }

    private fun remember(next: PomodoroNotificationDisplay) {
        display = next
        try {
            memory.save(next)
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
            if (!session.isSignedIn()) {
                display = null
                loaded = true
                notifications.remove()
                try {
                    memory.save(null)
                } catch (_: IOException) { /* Try again on an ordinary emission. */ }
                return@withState
            }
            val state = readDisplay()
            val content = held?.notificationContent()
            if (content == null) {
                notifications.remove()
                remember(state.copy(shown = null))
            } else if (ringing || serviceRunning.running()) {
                // ID 3 is now owned by the ring, including a service still stopping. Never
                // cancel it here. The service-state emission republishes after onDestroy.
                remember(state.copy(shown = null))
            } else if (force || state.shown != content.key) {
                // A denied permission leaves this eligible for the next permission grant.
                if (notifications.post(content, state.account)) remember(state.copy(shown = content.key))
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
        val accepted = lock.withLock { !retired && session.isSignedIn() && account == readDisplay().account }
        if (!accepted) return null
        return alarm.sendPhaseAction(actions, phase, revision, end)
    }

    /** Whether a tap on the notification of [account] and [phase] may open the Pomodoro page. */
    suspend fun acceptsTap(account: String, phase: PomodoroPhaseKey): Boolean = lock.withLock {
        if (retired || !session.isSignedIn() || account != readDisplay().account) return@withLock false
        alarm.current()?.phase == phase
    }

    /** Fence first, await collectors, and erase display identity before cache clearing. */
    suspend fun forget() {
        val observer = synchronized(this) {
            retired = true
            observing.running()
        }
        observer?.cancelAndJoin()
        lock.withLock {
            display = null
            loaded = true
            try {
                notifications.remove()
            } finally {
                memory.save(null)
            }
        }
    }
}
