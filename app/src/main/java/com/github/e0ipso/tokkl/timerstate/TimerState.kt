package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// The Tokkl server's timer state, as `TimerState` in ../tokkl/packages/types/index.ts at v1.10.0
// declares it. `GET /api/app/timer`, every timer action, every 409 refusal and each `timer` event
// of `GET /api/app/timer/events` send it. The server applies every Pomodoro and pause rule; the
// phone reads these values and counts down on a clock corrected by `server_time`. Every field is
// required, as the server always sends it, so a body that lacks one is not a timer state.

/**
 * A user's timer. It holds at most one of a pause chain and a Pomodoro session.
 *
 * @property revision goes up by one whenever the server's timer changes, from any client,
 *   including a v9 edit of the running entry and a rename of its project, task or tags. 0 for a
 *   user who has never changed the timer.
 * @property serverTime the server's clock when it built this state.
 * @property runningEntry the running entry in the v9 shape, or null when none runs.
 * @property pause the pause chain, from Pause until Discard, a start that is not a resume, or a
 *   stop of the resumed clone. Null without one.
 * @property pomodoro the Pomodoro session, or null without one.
 * @property discardedPomodoro the pomodoro that the change which produced this revision discarded
 *   as too short. It repeats in every state at that revision, so its notice must be shown once.
 */
@Serializable
data class TimerState(
    val revision: Long,
    @SerialName("server_time") @Serializable(with = IsoInstantSerializer::class) val serverTime: Instant,
    @SerialName("running_entry") val runningEntry: ServerTimeEntry?,
    val pause: TimerPause?,
    val pomodoro: TimerPomodoro?,
    @SerialName("discarded_pomodoro") val discardedPomodoro: DiscardedPomodoro?,
)

/**
 * A pause chain. Resume starts a clone with these fields, which are read-only while paused.
 *
 * @property paused true while no clone runs.
 * @property seconds what the chain's stopped entries stored, after the server snapped them. A
 *   pause that rounded to zero added none.
 * @property tagIds the tags a resumed clone gets, in name order.
 * @property tags the names of [tagIds], in the same order, for display.
 * @property cloneEntryId the running clone, or null while paused.
 */
@Serializable
data class TimerPause(
    val paused: Boolean,
    val seconds: Long,
    @SerialName("workspace_id") val workspaceId: Long,
    val description: String,
    @SerialName("project_id") val projectId: Long?,
    @SerialName("task_id") val taskId: Long?,
    @SerialName("tag_ids") val tagIds: List<Long>,
    val tags: List<String>,
    val billable: Boolean,
    @SerialName("clone_entry_id") val cloneEntryId: Long?,
)

/**
 * A Pomodoro session. Its open pomodoro is the running entry.
 *
 * @property workMinutes the lengths the session started with, in minutes, as are the two rests.
 * @property longRestEvery a long rest follows every this many work phases. The page shows
 *   "Pomodoro [number] of [longRestEvery]".
 * @property upcomingPhase the phase Next phase starts.
 * @property transition the change Next phase makes, which picks the phase end's sound.
 * @property number the pomodoro's place in the cycle. A rest counts the work phase it follows.
 * @property entryId the open pomodoro: the running entry's id.
 * @property pomodoroStart the open pomodoro's start, the running entry's start.
 */
@Serializable
data class TimerPomodoro(
    @SerialName("work_minutes") val workMinutes: Int,
    @SerialName("short_rest_minutes") val shortRestMinutes: Int,
    @SerialName("long_rest_minutes") val longRestMinutes: Int,
    @SerialName("long_rest_every") val longRestEvery: Int,
    val phase: TimerPomodoroPhase,
    @SerialName("upcoming_phase") val upcomingPhase: PhaseKind,
    val transition: PhaseTransition,
    val number: Int,
    @SerialName("entry_id") val entryId: Long,
    @SerialName("pomodoro_start") @Serializable(with = IsoInstantSerializer::class) val pomodoroStart: Instant,
)

/**
 * The session's current phase.
 *
 * @property start when the phase started: the session start or the instant Next phase was
 *   confirmed. The phase's alarm is keyed by it.
 * @property completed work phases ended since the session started or the last long rest ended.
 * @property plannedEnd when the phase reaches its planned length. Every device rings from here on
 *   until the phase changes.
 */
@Serializable
data class TimerPomodoroPhase(
    val kind: PhaseKind,
    @Serializable(with = IsoInstantSerializer::class) val start: Instant,
    val completed: Int,
    @SerialName("planned_end") @Serializable(with = IsoInstantSerializer::class) val plannedEnd: Instant,
)

/** A pomodoro too short to keep. The server sent it to the trash with zero length. */
@Serializable
data class DiscardedPomodoro(
    @SerialName("entry_id") val entryId: Long,
    @Serializable(with = IsoInstantSerializer::class) val start: Instant,
    @Serializable(with = IsoInstantSerializer::class) val stop: Instant,
)

/** A phase of a Pomodoro session, as the server names it. */
@Serializable
enum class PhaseKind {
    @SerialName("work") WORK,
    @SerialName("shortRest") SHORT_REST,
    @SerialName("longRest") LONG_REST,
}

/** The change that ends a phase, as the server names it. */
@Serializable
enum class PhaseTransition {
    @SerialName("workToShortRest") WORK_TO_SHORT_REST,
    @SerialName("shortRestToWork") SHORT_REST_TO_WORK,
    @SerialName("workToLongRest") WORK_TO_LONG_REST,
    @SerialName("longRestToWork") LONG_REST_TO_WORK,
}

/**
 * An ISO 8601 instant. The timer state's own times end in `Z` with milliseconds; an offset such
 * as `+00:00` reads too. It writes `Z`. A value that is not a timestamp fails the whole decode.
 */
internal object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("tokkl.IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            OffsetDateTime.parse(text).toInstant()
        } catch (e: DateTimeParseException) {
            throw SerializationException("Not an ISO 8601 instant")
        }
    }
}
