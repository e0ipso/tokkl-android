package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.QueueSender
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Pomodoro session's commands to the server and the edits of its open pomodoro, apart from what
 * the page shows. Start, Next phase and End go to the server through [actions]. Field edits of
 * the open pomodoro go to the running entry through [changes], the queue every running-entry
 * edit uses, and the server carries them to the later pomodoros.
 *
 * Every edit of the running entry moves the server's revision. So when edits made here may not
 * have reached the server, Next phase and End first wait for the edits still being queued, send
 * the queue through [sender], read the state and wait for the revision the edits produced, and
 * send it only while the phase is the one the page showed. Otherwise the server would refuse the
 * revision the page showed, and the edits would land after the phase they belong to.
 *
 * The Pomodoro page's view model holds one and maps each result to what the page says.
 *
 * @param held the account's held timer state.
 */
class PomodoroSessionEdits(
    private val held: Flow<HeldTimerState?>,
    private val actions: SessionActions,
    private val changes: RunningEntryChanges,
    private val sender: QueueSender,
) {

    /** How many edits are on their way into the queue. */
    private val writing = MutableStateFlow(0)

    /** Whether an edit reached the queue that the server may not have yet. */
    private val unflushed = AtomicBoolean(false)

    /**
     * Queues [fields] for the open pomodoro, [entry] as the cache holds it, with the identity the
     * cache holds it under. Returns false when the queue no longer runs that entry.
     */
    suspend fun editOpenPomodoro(entry: TimeEntry, fields: EntryFields): Boolean {
        writing.update { it + 1 }
        try {
            val identity = RunningEntryIdentity(entry.id, entry.workspaceId, entry.start)
            return changes.updateRunning(identity, fields, entry.start).also { queued ->
                if (queued) unflushed.set(true)
            }
        } finally {
            writing.update { it - 1 }
        }
    }

    /** Starts a session in [workspaceId] whose first pomodoro has [fields], with [lengths]. */
    suspend fun start(workspaceId: WorkspaceId, fields: EntryFields, lengths: SessionLengths): TimerActionResult =
        actions.startSession(workspaceId, fields, lengths)

    /** Confirms the phase [shown] and starts the next one, after this page's own edits. */
    suspend fun next(shown: PomodoroShown): TimerActionResult = finish(shown, actions::nextPhase)

    /** Ends the session [shown], after this page's own edits. */
    suspend fun end(shown: PomodoroShown): TimerActionResult = finish(shown, actions::endSession)

    private suspend fun finish(shown: PomodoroShown, send: suspend (Long) -> TimerActionResult): TimerActionResult {
        val revision = revisionToSend(shown) ?: return TimerActionResult.Refused
        return send(revision)
    }

    /**
     * The revision Next phase or End sends: the one the page showed, or after the edits made here
     * the one the server gave them, which they moved. Null when the session left the phase the
     * page showed meanwhile. Sending the edits first keeps them on the pomodoro this action ends,
     * and lets Next phase carry them to the next.
     */
    private suspend fun revisionToSend(shown: PomodoroShown): Long? {
        writing.first { it == 0 }
        if (!unflushed.getAndSet(false)) return shown.revision
        sender.sendQueued()
        actions.read()
        withTimeoutOrNull(REFRESH_MS) {
            held.first { state -> state?.shown().let { now -> now == null || now.revision > shown.revision } }
        }
        val latest = held.first()?.shown() ?: return null
        return if (latest.phaseStart == shown.phaseStart) latest.revision else null
    }

    private companion object {
        /** How long Next phase waits for the revision the page's own edits produced. */
        const val REFRESH_MS = 3_000L
    }
}
