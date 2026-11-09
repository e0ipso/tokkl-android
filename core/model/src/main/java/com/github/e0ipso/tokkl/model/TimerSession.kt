package com.github.e0ipso.tokkl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// The server's pause chain and Pomodoro session as the screens read them (docs/PRD.md T1, W2 to
// W10). core:timerstate maps the server's timer state to these; the server decides every phase,
// planned end and transition. Times are on the server's clock.

/** A Pomodoro phase's kind. The serial names are the server's. */
@Serializable
enum class PhaseKind {
    @SerialName("work")
    WORK,

    @SerialName("shortRest")
    SHORT_REST,

    @SerialName("longRest")
    LONG_REST,
}

/** What the next phase change of a Pomodoro session is. The serial names are the server's. */
@Serializable
enum class PhaseTransition {
    @SerialName("workToShortRest")
    WORK_TO_SHORT_REST,

    @SerialName("shortRestToWork")
    SHORT_REST_TO_WORK,

    @SerialName("workToLongRest")
    WORK_TO_LONG_REST,

    @SerialName("longRestToWork")
    LONG_REST_TO_WORK,
}

/** The session's current phase: its [kind], from [start] to its [plannedEnd], after [completed] pomodoros. */
data class PomodoroPhase(val kind: PhaseKind, val start: Instant, val completed: Int, val plannedEnd: Instant)

/**
 * The server's Pomodoro session: its lengths in minutes, the [phase] it is in, the phase that
 * comes next and how, the pomodoro [number], and [entryId], the time entry its open pomodoro runs
 * as, which started at [pomodoroStart].
 */
data class PomodoroSession(
    val workMinutes: Int,
    val shortRestMinutes: Int,
    val longRestMinutes: Int,
    val longRestEvery: Int,
    val phase: PomodoroPhase,
    val upcomingPhase: PhaseKind,
    val transition: PhaseTransition,
    val number: Int,
    val entryId: EntryId,
    val pomodoroStart: Instant,
)

/**
 * The server's pause chain: [paused], or running as its clone [cloneEntryId], with the [seconds]
 * its stopped entries store and the [fields] Resume starts with in [workspaceId].
 */
data class PauseChain(
    val paused: Boolean,
    val seconds: Long,
    val workspaceId: WorkspaceId,
    val fields: EntryFields,
    val cloneEntryId: EntryId?,
)

/** The pomodoro [entryId], from [start] to [stop], which the server discarded as too short. */
data class DiscardedPomodoro(val entryId: EntryId, val start: Instant, val stop: Instant)
