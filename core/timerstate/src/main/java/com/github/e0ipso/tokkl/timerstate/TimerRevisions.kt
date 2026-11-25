package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.model.EntryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a revisioned timer action comes to before it goes out: [Send] it at a revision, or [Skip]
 * it, send nothing, and report a result instead.
 */
sealed interface RevisionChoice {

    /** Send the action at [revision]. */
    data class Send(val revision: Long) : RevisionChoice

    /**
     * Send nothing, and report [result]: a failed read's [TimerActionResult.Offline] or
     * [TimerActionResult.Failed], or [TimerActionResult.Refused] when the state no longer shows
     * what the surface showed.
     */
    data class Skip(val result: TimerActionResult) : RevisionChoice

    /** Sends [action] at the chosen revision and returns what it came to, or the [Skip]'s result. */
    suspend fun send(action: suspend (revision: Long) -> TimerActionResult): TimerActionResult = when (this) {
        is Send -> action(revision)
        is Skip -> result
    }
}

/**
 * The revision Pause and Pomodoro Start send, chosen in one place for every surface:
 * `forPause(entryId, unsent, currentId)` for the Timer page, the full-screen timer and the native
 * timer controls, and `forStart(queued)` for the Pomodoro page and widget. Send the action with
 * [RevisionChoice.send], as in `forPause(...).send(actions::pause)`.
 *
 * Each sends the revision the phone holds at the call while that state still shows what the
 * control showed, which the surface checked just before. When the phone holds no state yet, or
 * Pause finds the held state runs another entry, it reads the timer first. When the phone's own
 * queued changes would move the revision, as an unsent Start or an edit does, it sends the queue,
 * reads, and waits up to 3 s for a revision newer than the one held before, so those changes do
 * not get the action refused. After a read it sends the latest held revision only while that
 * state still shows what the surface showed, and otherwise reports [TimerActionResult.Refused]. A
 * read that fails ends the choice with its result.
 *
 * @param held the account's held state, which [read] gives its answer to.
 * @param read reads the timer with `GET /api/app/timer`, as [PauseActions.read] and
 *   [SessionActions.read] do.
 * @param sender sends the queue and waits for it.
 */
class TimerRevisions(
    private val held: Flow<HeldTimerState?>,
    private val read: suspend () -> TimerActionResult,
    private val sender: suspend () -> Unit,
) {

    /**
     * The revision Pause of [entryId], the running entry the surface showed, sends. [unsent] says
     * the queue holds a change the server may not have yet, such as the entry's own Start. It
     * sends the held revision only while the held state runs that entry outside a session. When
     * it does not, as when the stream brought another entry after the surface's check, it reads
     * first, and then sends only while the read state runs that entry. [currentId] gives the
     * entry's id once its Start is sent, as `RunningEntryChanges.currentId` does.
     */
    suspend fun forPause(entryId: EntryId, unsent: Boolean, currentId: suspend (EntryId) -> EntryId): RevisionChoice {
        val runsEntry: suspend (HeldTimerState) -> Boolean = { state ->
            state.pomodoro == null && state.runningEntry?.id == currentId(entryId)
        }
        val tapped = held.first()
        if (tapped != null && !unsent && runsEntry(tapped)) return RevisionChoice.Send(tapped.revision)
        return readAgain(tapped, flush = unsent, runsEntry)
    }

    /**
     * The revision Pomodoro Start sends, while no session shows. [queued] says whether the queue
     * holds a change the server may not have yet, as `RunningEntryEdits.awaitQueued` does.
     */
    suspend fun forStart(queued: suspend () -> Boolean): RevisionChoice {
        val flush = queued()
        val tapped = held.first()
        if (tapped != null && !flush) return sendWhile(tapped, ::noSession)
        return readAgain(tapped, flush, ::noSession)
    }

    /**
     * Sends the queue when [flush], reads the timer, and after a flush waits for a revision newer
     * than [before]'s. Then sends the latest held revision while that state [shows] what the
     * surface showed.
     */
    private suspend fun readAgain(
        before: HeldTimerState?,
        flush: Boolean,
        shows: suspend (HeldTimerState) -> Boolean,
    ): RevisionChoice {
        if (flush) sender()
        val result = read()
        if (result != TimerActionResult.Done) return RevisionChoice.Skip(result)
        val latest = if (flush && before != null) held.awaitNewerThan(before.revision) else held.first()
        return sendWhile(latest, shows)
    }

    private suspend fun sendWhile(state: HeldTimerState?, shows: suspend (HeldTimerState) -> Boolean): RevisionChoice =
        if (state != null && shows(state)) {
            RevisionChoice.Send(state.revision)
        } else {
            RevisionChoice.Skip(TimerActionResult.Refused)
        }

    private fun noSession(state: HeldTimerState): Boolean = state.pomodoro == null
}

/**
 * Waits up to 3 s for a held state newer than [revision], such as the one the phone's own changes
 * produce once they reach the server, or for the account's state to go. Returns the latest held
 * state then, or null without one. The surface checks it still shows what it showed before it
 * sends that state's revision.
 */
suspend fun Flow<HeldTimerState?>.awaitNewerThan(revision: Long): HeldTimerState? {
    withTimeoutOrNull(REFRESH_MS) { first { it == null || it.revision > revision } }
    return first()
}

/** How long an action waits for the revision the phone's own changes produced. */
private const val REFRESH_MS = 3_000L
