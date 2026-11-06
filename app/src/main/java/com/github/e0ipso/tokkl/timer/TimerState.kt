package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Duration
import java.time.Instant

/** What the timer shows. */
sealed interface TimerState {

    /** The phone has not read its cache yet. */
    data object Loading : TimerState

    /**
     * No entry runs. Start starts one with [fields] in [workspaceId], the account's default
     * workspace, which is null until the phone has read it from the server. Start waits for it.
     */
    data class Idle(val fields: EntryFields, val labels: EntryLabels, val workspaceId: Long?, val authoritative: Boolean = false) : TimerState

    /**
     * An entry runs since [start], with [fields]: the cached entry's, or an edit the cache does not
     * have yet.
     *
     * @property entryId the cached entry's id, or null from a Start tap until the cache shows the entry.
     * @property original the project, task and tags the cache has, which the picker keeps on offer
     *   when they are archived.
     * @property unsent the server has not received every change to the entry.
     * @property accumulatedSeconds what the server's pause chain stored before this entry, its
     *   resumed clone, which the clock adds to the time since [start] (docs/PRD.md T1), or 0
     *   outside a chain.
     * @property pausable whether Pause shows: on a server with the timer API, outside a Pomodoro
     *   session.
     * @property clockOffset what the clock adds to the phone's time when it counts from [start]:
     *   the server's clock offset for the clone, whose start the server set, and zero otherwise.
     */
    data class Running(
        val entryId: Long?,
        val workspaceId: Long,
        val start: Instant,
        val fields: EntryFields,
        val labels: EntryLabels,
        val original: Picked,
        val unsent: Boolean,
        val accumulatedSeconds: Long = 0,
        val target: TimerTarget? = null,
        val pausable: Boolean = true,
        val clockOffset: Duration = Duration.ZERO,
    ) : TimerState

    /**
     * The server's pause chain is paused at [seconds], the time its entries store (docs/PRD.md
     * T1). Resume starts [fields] in [workspaceId] as a new entry, and Stop ends the chain. The
     * fields are read-only: the server keeps the ones recorded at Pause.
     *
     * @property original the project, task and tags the chain keeps.
     */
    data class Paused(
        val workspaceId: Long,
        val seconds: Long,
        val fields: EntryFields,
        val labels: EntryLabels,
        val original: Picked,
        val target: TimerTarget? = null,
    ) : TimerState
}

/**
 * What the timer's screens say about it: a Pause, Resume or Stop while paused that did not
 * happen, and a pomodoro the server discarded as too short. The navigation host shows them in one
 * place, whichever of the timer's pages is on screen.
 */
sealed interface TimerNotice {

    /**
     * A pause chain action the server refused, or that got no answer, as [result] says.
     * [number] tells two failures with the same result apart.
     */
    data class ActionFailed(val result: TimerActionResult, val number: Long) : TimerNotice

    /** The server discarded the pomodoro [entryId] as too short to save. */
    data class PomodoroDiscarded(val entryId: Long) : TimerNotice
}

/** No durable target during an optimistic transition; stale controls cannot act on its successor. */
internal val TimerState.target: TimerTarget?
    get() = when (this) {
        is TimerState.Running -> target
        is TimerState.Paused -> target
        else -> null
    }

/**
 * The instant the running clock counts from on the phone's clock: the start, moved back by the
 * server's clock offset for its clone.
 */
internal val TimerState.Running.countsFrom: Instant get() = start.minus(clockOffset)
