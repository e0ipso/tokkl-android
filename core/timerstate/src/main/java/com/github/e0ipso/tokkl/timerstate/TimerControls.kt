package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.PauseChain
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import java.time.Duration
import java.time.Instant

/**
 * What a timer control acts on (docs/PRD.md T1). Each surface passes the target it drew, and the
 * action goes ahead only while the phone still shows that state, so a second tap, a tap on an old
 * notification or a widget drawn before sign-out does nothing.
 *
 * Every surface draws its targets from the server's timer state ([serverTimerControls]): a running
 * entry or a paused chain. Each names the [account] it was drawn for, so a control kept in a
 * notification, a widget or a PendingIntent past a sign-out acts on nothing in the next account.
 * Where a PendingIntent, a work request or a widget action keeps one, it keeps it flat
 * ([flatRevision], [flatEntryId]) and reads it back with [timerTarget].
 */
sealed interface TimerTarget {

    /** The account the control was drawn for, as the timer state's store names it. */
    val account: String

    /** The running entry [entryId] a control showed, which Pause and Stop act on. */
    data class Running(val entryId: EntryId, override val account: String) : TimerTarget

    /** A paused chain a control showed at the server's [revision], which Resume and Discard send. */
    data class Paused(val revision: Long, override val account: String) : TimerTarget
}

/** The revision a flat copy of this target keeps: a paused chain's, or 0 for a running entry. */
val TimerTarget.flatRevision: Long get() = (this as? TimerTarget.Paused)?.revision ?: 0

/** The entry a flat copy of this target keeps: a running entry's id, or 0 for a paused chain. */
val TimerTarget.flatEntryId: Long get() = (this as? TimerTarget.Running)?.entryId?.value ?: 0

/**
 * The target a PendingIntent, a work request or a widget action kept flat, as [account],
 * [revision] and [entryId], 0 for none: the running entry [entryId], or else the paused chain at
 * [revision]. Null when it names no account, as one an older version kept, which acts on nothing.
 */
fun timerTarget(account: String?, revision: Long, entryId: Long): TimerTarget? {
    if (account.isNullOrEmpty()) return null
    return if (entryId != 0L) TimerTarget.Running(EntryId(entryId), account) else TimerTarget.Paused(revision, account)
}

/**
 * What every timer surface shows and offers: the timer panel, the notification, the widget, the
 * Quick Settings tile and the long-timer reminder read the same state, so they agree.
 */
sealed interface TimerControls {

    /** Nothing runs and no chain is paused. Start, Continue and favorites start a timer. */
    data object Idle : TimerControls

    /**
     * [entry] runs, marked [unsent] while the queue holds a change to it. Pause and Stop act on it.
     *
     * @property accumulatedSeconds what the chain's stopped entries store before this one, or 0
     *   outside a chain. The clock shows it plus the time since [TimeEntry.start].
     * @property clockOffset what to add to the phone's clock when counting from the entry's
     *   start: the server's clock offset while the entry is the server's resumed clone, whose
     *   start the server set, and zero otherwise.
     * @property pausable whether Pause is offered. The server's chain needs the timer API, and the
     *   server refuses Pause during a Pomodoro session.
     */
    data class Running(
        val entry: TimeEntry,
        val unsent: Boolean,
        val accumulatedSeconds: Long,
        val target: TimerTarget.Running,
        val clockOffset: Duration = Duration.ZERO,
        val pausable: Boolean = true,
    ) : TimerControls

    /**
     * A chain is paused at [seconds], the time its entries store. Resume starts [fields] in
     * [workspaceId] as a new entry, and Stop ends the chain.
     */
    data class Paused(
        val seconds: Long,
        val workspaceId: WorkspaceId,
        val fields: EntryFields,
        val target: TimerTarget.Paused,
    ) : TimerControls
}

val TimerControls.target: TimerTarget?
    get() = when (this) {
        is TimerControls.Running -> target
        is TimerControls.Paused -> target
        TimerControls.Idle -> null
    }

/**
 * The controls for the cache's [running] entry and the server's timer state [held]. Without
 * [timerApi] the held state counts for nothing, so no chain shows and Pause is not offered.
 *
 * The cache's running entry always shows, as it carries the queued Starts and Stops the server
 * may not have yet. It adds the chain's stored seconds only while it is the chain's clone, and
 * then counts on the server's clock, which set the clone's start. A chain shows as paused only
 * while nothing runs, with the seconds and fields the server holds. A chain whose clone no longer
 * runs in the cache, as after a queued Stop, shows nothing. Pause is offered outside a Pomodoro
 * session, which the server refuses it in.
 */
fun serverTimerControls(
    running: EntryWithSendState?,
    held: HeldTimerState?,
    timerApi: Boolean,
    account: String,
): TimerControls {
    val state = held?.takeIf { timerApi }
    val pause = state?.pause
    if (running != null) {
        val entry = running.entry
        val clone = (pause as? PauseChain.Resumed)?.takeIf { it.cloneEntryId == entry.id }
        return TimerControls.Running(
            entry = entry,
            unsent = running.unsent,
            accumulatedSeconds = clone?.seconds ?: 0,
            target = TimerTarget.Running(entry.id, account),
            clockOffset = clone?.let { held?.clockOffset } ?: Duration.ZERO,
            pausable = timerApi && state?.pomodoro == null,
        )
    }
    if (state != null && pause is PauseChain.Paused) {
        return TimerControls.Paused(
            seconds = pause.seconds,
            workspaceId = pause.workspaceId,
            fields = pause.fields,
            target = TimerTarget.Paused(state.revision, account),
        )
    }
    return TimerControls.Idle
}

/**
 * The whole seconds the clock shows at the phone's [now]: the chain's stored time and this
 * entry's, counted on the server's clock for its clone.
 */
fun TimerControls.Running.seconds(now: Instant): Long =
    accumulatedSeconds + Duration.between(entry.start, now.plus(clockOffset)).seconds.coerceAtLeast(0)

/**
 * The instant on the phone's clock that a system chronometer counts this entry from, after
 * [TimerControls.Running.accumulatedSeconds]: the entry's start, moved by the server's clock
 * offset for a clone. The offset is rounded to whole seconds, so the few milliseconds it moves
 * with each new state do not redraw a notification or a widget whose clock the system ticks.
 */
val TimerControls.Running.countsFrom: Instant
    get() = entry.start.minusSeconds(Math.floorDiv(clockOffset.toMillis() + HALF_SECOND_MS, SECOND_MS))

private const val SECOND_MS = 1_000L
private const val HALF_SECOND_MS = 500L
