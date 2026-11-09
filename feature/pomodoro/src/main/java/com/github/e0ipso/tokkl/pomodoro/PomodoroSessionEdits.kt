package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.QueueSender
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.cache.RunningEntryEdits
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A Pomodoro session's commands to the server and the edits of its open pomodoro, apart from what
 * the page shows. Start, Next phase and End go to the server through [actions]. Field edits of
 * the open pomodoro go to the running entry through [changes], the queue every running-entry
 * edit uses, as [RunningEntryEdits] writes them for the timer too, and the server carries them to
 * the later pomodoros.
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
    changes: RunningEntryChanges,
    private val sender: QueueSender,
) {

    private val edits = RunningEntryEdits(changes)

    /**
     * Queues [fields] for the open pomodoro, [entry] as the page shows it, with the identity the
     * cache holds it under, and waits until [running], the cache's running entry, shows them, as
     * [RunningEntryEdits.write] does. Returns whether the phone's storage refused the write.
     */
    suspend fun editOpenPomodoro(entry: TimeEntry, fields: EntryFields, running: Flow<TimeEntry?>): Boolean =
        edits.write(RunningEntryIdentity(entry.id, entry.workspaceId, entry.start), fields, entry.fields(), running)

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
        if (!edits.awaitQueued()) return shown.revision
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
