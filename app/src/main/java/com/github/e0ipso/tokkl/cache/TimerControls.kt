package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import java.time.Duration
import java.time.Instant

/**
 * What a timer control acts on (docs/PRD.md T1). Each surface passes the target it drew, and the
 * action goes ahead only while the phone still shows that state, so a second tap, a tap on an old
 * notification or a widget drawn before sign-out does nothing.
 *
 * Every surface draws its targets from the server's timer state, made with [server]: a paused one
 * carries the server [revision] it was shown at, which Resume and Discard send, and a running one
 * the entry it showed. Each also names the [account] it was drawn for, so a control kept in a
 * notification, a widget or a PendingIntent past a sign-out acts on nothing in the next account.
 *
 * @property revision the server revision a paused target was shown at, or 0.
 * @property entryId the running entry it showed, or 0 while paused.
 * @property account the account the control was drawn for, as the timer state's store names it,
 *   or blank where no account names it.
 */
data class TimerTarget(
    val revision: Long = 0,
    val entryId: Long = 0,
    val account: String = "",
) {
    companion object {
        /**
         * A control drawn from the server's timer state for [account]: the running entry
         * [entryId], or a paused chain shown at the server's [revision].
         */
        fun server(revision: Long = 0, entryId: Long = 0, account: String = "") =
            TimerTarget(revision = revision, entryId = entryId, account = account)
    }
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
     *   outside a chain. The clock shows it plus the time since [CachedTimeEntry.start].
     * @property clockOffset what to add to the phone's clock when counting from the entry's
     *   start: the server's clock offset while the entry is the server's resumed clone, whose
     *   start the server set, and zero otherwise.
     * @property pausable whether Pause is offered. The server's chain needs the timer API, and the
     *   server refuses Pause during a Pomodoro session.
     */
    data class Running(
        val entry: CachedTimeEntry,
        val unsent: Boolean,
        val accumulatedSeconds: Long,
        val target: TimerTarget,
        val clockOffset: Duration = Duration.ZERO,
        val pausable: Boolean = true,
    ) : TimerControls

    /**
     * A chain is paused at [seconds], the time its entries store. Resume starts [fields] in
     * [workspaceId] as a new entry, and Stop ends the chain.
     */
    data class Paused(
        val seconds: Long,
        val workspaceId: Long,
        val fields: EntryFields,
        val target: TimerTarget,
    ) : TimerControls
}

/** The target these controls were drawn with, or null while idle. */
val TimerControls.target: TimerTarget?
    get() = when (this) {
        is TimerControls.Running -> target
        is TimerControls.Paused -> target
        TimerControls.Idle -> null
    }

/**
 * The controls for the cache's [running] entry and the server's timer state [held], as every
 * timer surface shows them: the Timer page, the full-screen timer, the running notification, the
 * widget, the Quick Settings tile and the reminder. [timerApi] is whether the server offers the
 * timer API: without it the held state counts for nothing, so no chain shows and Pause is not
 * offered. Every target names [account].
 *
 * The cache's running entry always shows, as it carries the queued Starts and Stops the server
 * may not have yet. It adds the chain's stored seconds only while it is the chain's clone, and
 * then counts on the server's clock, which set the clone's start. A chain shows as paused only
 * while nothing runs, with the seconds and fields the server holds. A chain whose clone no longer
 * runs in the cache, as after a queued Stop, shows nothing. Pause is offered outside a Pomodoro
 * session, which the server refuses it in.
 */
fun serverTimerControls(running: EntryWithSendState?, held: HeldTimerState?, timerApi: Boolean, account: String = ""): TimerControls {
    val state = held?.takeIf { timerApi }?.state
    val pause = state?.pause
    if (running != null) {
        val entry = running.entry
        val clone = pause?.takeIf { !it.paused && it.cloneEntryId == entry.id }
        return TimerControls.Running(
            entry = entry,
            unsent = running.unsent,
            accumulatedSeconds = clone?.seconds ?: 0,
            target = TimerTarget.server(entryId = entry.id, account = account),
            clockOffset = clone?.let { held?.clockOffset } ?: Duration.ZERO,
            pausable = timerApi && state?.pomodoro == null,
        )
    }
    if (pause != null && pause.paused) {
        return TimerControls.Paused(
            seconds = pause.seconds,
            workspaceId = pause.workspaceId,
            fields = EntryFields(pause.description, pause.projectId, pause.taskId, pause.tags, pause.billable),
            target = TimerTarget.server(revision = state?.revision ?: 0, account = account),
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
