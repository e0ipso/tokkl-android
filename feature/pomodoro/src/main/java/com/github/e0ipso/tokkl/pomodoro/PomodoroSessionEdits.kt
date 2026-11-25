package com.github.e0ipso.tokkl.pomodoro

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
import com.github.e0ipso.tokkl.timerstate.TimerRevisions
import com.github.e0ipso.tokkl.timerstate.awaitNewerThan
import kotlinx.coroutines.flow.Flow

/**
 * Field edits of the open pomodoro go through the running entry's queue, as [RunningEntryEdits]
 * writes them for the timer too, and the server carries them to the later pomodoros.
 *
 * Every edit of the running entry moves the server's revision. So when edits made here may not
 * have reached the server, Next phase and End first wait for the edits still being queued, send
 * the queue through [sender], read the state and wait for the revision the edits produced, and
 * send it only while the phase is the one the page showed. Otherwise the server would refuse the
 * revision the page showed, and the edits would land after the phase they belong to.
 *
 * Start sends a revision too, which [TimerRevisions.forStart] chooses: the one held at the tap, or
 * after a read when the phone holds none. When edits made here may not have reached the server, it
 * sends the queue and reads first, as Next phase does. It sends only while no session shows, and
 * otherwise reports [TimerActionResult.Refused].
 */
class PomodoroSessionEdits(
    private val held: Flow<HeldTimerState?>,
    private val actions: SessionActions,
    changes: RunningEntryChanges,
    private val sender: suspend () -> Unit,
) {

    private val edits = RunningEntryEdits(changes)

    private val revisions = TimerRevisions(held, actions::read, sender)

    /**
     * Queues [fields] for the open pomodoro, [entry] as the page shows it, with the identity the
     * cache holds it under, and waits until [running], the cache's running entry, shows them, as
     * [RunningEntryEdits.write] does. Returns whether the phone's storage refused the write.
     */
    suspend fun editOpenPomodoro(entry: TimeEntry, fields: EntryFields, running: Flow<TimeEntry?>): Boolean =
        edits.write(RunningEntryIdentity(entry.id, entry.workspaceId, entry.start), fields, entry.fields(), running)

    suspend fun start(workspaceId: WorkspaceId, fields: EntryFields, lengths: SessionLengths): TimerActionResult =
        revisions.forStart(edits::awaitQueued).send { actions.startSession(workspaceId, fields, lengths, it) }

    suspend fun next(shown: PomodoroShown): TimerActionResult = finish(shown, actions::nextPhase)

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
        sender()
        actions.read()
        val latest = held.awaitNewerThan(shown.revision)?.shown() ?: return null
        return if (latest.phaseStart == shown.phaseStart) latest.revision else null
    }
}
