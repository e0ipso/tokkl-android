package com.github.e0ipso.tokkl.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.InputStream

/**
 * The body of `POST /api/app/pomodoro`: the first pomodoro's fields as a v9 start sends them, tags
 * by name, the four lengths in place of the account's defaults, and the [revision] of the state
 * the start acts on. A start stops a running timer, so the server refuses one at a stale revision.
 */
@Serializable
data class SessionStartBody(
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("created_with") val createdWith: String,
    val description: String,
    @SerialName("project_id") val projectId: Long?,
    @SerialName("task_id") val taskId: Long?,
    val tags: List<String>,
    val billable: Boolean,
    @SerialName("work_minutes") val workMinutes: Int,
    @SerialName("short_rest_minutes") val shortRestMinutes: Int,
    @SerialName("long_rest_minutes") val longRestMinutes: Int,
    @SerialName("long_rest_every") val longRestEvery: Int,
    val revision: Long,
)

/** The body of an action that depends on the state the surface showed. */
@Serializable
private class RevisionBody(val revision: Long)

/**
 * Tokkl's server-owned timer under `/api/app`, from Tokkl 1.10.0; older servers answer 404. Each
 * action goes out once and answers with the timer state, also when the server refuses it with a
 * 409, as [ApiTransport.sendRefusable] describes. Every action takes the revision of the state it
 * acts on, and the server refuses one at a stale revision. The server checks it on Pause and on
 * the session start from Tokkl 2.2.0.
 */
class TimerApi internal constructor(private val client: ApiTransport) {

    /** `GET /api/app/timer`. */
    suspend fun state(): ApiResult<TimerState> = client.get(client.server.appUrl("timer"), TimerState.serializer())

    /** `GET /api/app/timer/events`, the Server-Sent Events stream, as [ApiTransport.stream] describes. */
    suspend fun <T> events(cancellation: ApiCallCancellation, read: (InputStream) -> T): ApiResult<T> =
        client.stream(client.server.appUrl("timer", "events"), cancellation, read)

    /** `POST /api/app/timer/pause` at [revision]. */
    suspend fun pause(revision: Long): ApiResult<Refusable<TimerState>> = action(revisioned(revision), "timer", "pause")

    /** `POST /api/app/timer/resume` at [revision]. */
    suspend fun resume(revision: Long): ApiResult<Refusable<TimerState>> =
        action(revisioned(revision), "timer", "resume")

    /** `POST /api/app/timer/discard` at [revision], which is Stop while paused. */
    suspend fun discard(revision: Long): ApiResult<Refusable<TimerState>> =
        action(revisioned(revision), "timer", "discard")

    /** `POST /api/app/pomodoro`, which starts a session with [body], at its revision. */
    suspend fun startSession(body: SessionStartBody): ApiResult<Refusable<TimerState>> =
        action(json(SessionStartBody.serializer(), body), "pomodoro")

    /** `POST /api/app/pomodoro/next` at [revision]: confirms the phase and starts the next one. */
    suspend fun nextPhase(revision: Long): ApiResult<Refusable<TimerState>> =
        action(revisioned(revision), "pomodoro", "next")

    /** `POST /api/app/pomodoro/end` at [revision]. */
    suspend fun endSession(revision: Long): ApiResult<Refusable<TimerState>> =
        action(revisioned(revision), "pomodoro", "end")

    private suspend fun action(body: JsonElement, vararg path: String): ApiResult<Refusable<TimerState>> =
        client.sendRefusable(HttpMethod.POST, client.server.appUrl(*path), body, TimerState.serializer())

    private fun revisioned(revision: Long): JsonElement = json(RevisionBody.serializer(), RevisionBody(revision))
}
