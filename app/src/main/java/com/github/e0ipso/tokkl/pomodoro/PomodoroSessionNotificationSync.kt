package com.github.e0ipso.tokkl.pomodoro

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

/** All publication and actions belong to one store/account for this observer's lifetime. */
internal class PomodoroSessionNotificationSync(
    private val sessions: PomodoroSessionStore,
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

    /** Re-read the durable phase while holding its lock, never publish a captured flow row. */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        if (retired) return@withLock
        sessions.withNotificationState { session, ringing ->
            if (retired || sessions.isForgotten) return@withNotificationState
            if (!signedIn()) {
                display = null
                loaded = true
                remove()
                try { save(null) } catch (_: IOException) { /* Try again on an ordinary emission. */ }
                return@withNotificationState
            }
            val state = readDisplay()
            if (session == null) {
                remove()
                remember(state.copy(shown = null))
            } else if (ringing || serviceRunning()) {
                // ID 3 is now owned by the ring, including a service still stopping. Never
                // cancel it here. The service-state emission republishes after onDestroy.
                remember(state.copy(shown = null))
            } else {
                val content = session.notificationContent()
                if (force || state.shown != content.key) {
                    // A denied permission leaves this eligible for the next permission grant.
                    if (post(content, state.account)) {
                        remember(state.copy(shown = content.key))
                    }
                } else {
                    remember(state)
                }
            }
        }
    }

    /** The phase guard lives in the store's transaction; the account guard shares this lock. */
    suspend fun act(account: String, phase: PomodoroPhaseKey, end: Boolean): PomodoroStep = lock.withLock {
        if (retired || !signedIn() || account != readDisplay().account) return@withLock PomodoroStep.Idle
        if (end) sessions.endIfCurrent(phase) else sessions.nextPhaseIfCurrent(phase)
    }

    suspend fun acceptsTap(account: String, phase: PomodoroPhaseKey): Boolean = lock.withLock {
        if (retired || !signedIn() || account != readDisplay().account) return@withLock false
        var current = false
        sessions.withNotificationState { session, _ -> current = session?.phaseKey() == phase }
        current
    }

    /** Fence first, await collectors/actions, and erase display identity before cache clearing. */
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
