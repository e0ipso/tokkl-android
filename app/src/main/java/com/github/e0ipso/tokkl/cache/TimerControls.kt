package com.github.e0ipso.tokkl.cache

import java.time.Duration
import java.time.Instant

/**
 * What a timer control acts on (docs/PRD.md T1): the session it was shown in, the pause chain and
 * its revision, and the running entry it showed. Each surface passes the target it drew, and the
 * queue acts only while the phone still holds that state, so a second tap, a tap on an old
 * notification or a widget drawn before sign-out does nothing.
 *
 * @property session the [TimerSession] the control was shown in.
 * @property chainId the chain it was shown for, or 0 outside a chain.
 * @property revision the chain's revision then, or 0 outside a chain.
 * @property entryId the running entry it showed, or 0 while paused.
 */
data class TimerTarget(
    val session: Long,
    val chainId: Long = 0,
    val revision: Long = 0,
    val entryId: Long = 0,
)

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
     * @property accumulatedSeconds what the chain's stopped segments store before this one, or 0
     *   outside a chain. The clock shows it plus the time since [CachedTimeEntry.start].
     */
    data class Running(
        val entry: CachedTimeEntry,
        val unsent: Boolean,
        val accumulatedSeconds: Long,
        val target: TimerTarget,
    ) : TimerControls

    /**
     * A chain is paused at [seconds], the time its segments store. Resume starts [fields] in
     * [workspaceId] as a new entry, and Stop ends the chain without a request.
     */
    data class Paused(
        val seconds: Long,
        val workspaceId: Long,
        val fields: EntryFields,
        val target: TimerTarget,
    ) : TimerControls
}

/**
 * The controls for the cache's [running] entry, the pause [chain] and the [session]. A running
 * entry always shows, as the server's running timer wins over a chain. The chain adds its time
 * only while that entry is its clone, and shows as paused only while nothing runs.
 */
fun timerControls(running: EntryWithSendState?, chain: PauseChain?, session: Long): TimerControls {
    if (running != null) {
        val entry = running.entry
        val segment = chain?.takeIf { it.runningId == entry.id }
        return TimerControls.Running(
            entry = entry,
            unsent = running.unsent,
            accumulatedSeconds = segment?.stoppedSeconds ?: 0,
            target = TimerTarget(session, segment?.chainId ?: 0, segment?.revision ?: 0, entry.id),
        )
    }
    if (chain != null && chain.runningId == null) {
        return TimerControls.Paused(
            seconds = chain.stoppedSeconds,
            workspaceId = chain.workspaceId,
            fields = chain.fields,
            target = TimerTarget(session, chain.chainId, chain.revision),
        )
    }
    return TimerControls.Idle
}

/** The whole seconds the clock shows at [now]: the chain's stored time and this segment's. */
fun TimerControls.Running.seconds(now: Instant): Long =
    accumulatedSeconds + Duration.between(entry.start, now).seconds.coerceAtLeast(0)
