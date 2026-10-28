package com.github.e0ipso.tokkl.timerstate

import androidx.annotation.StringRes
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.Refusable
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CREATED_WITH
import com.github.e0ipso.tokkl.cache.EntryFields
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Tokkl server's timer actions under `/api/app`: Pause, Resume and Discard of the pause chain,
 * and Start, Next phase and End of a Pomodoro session, with the timer read they share. Each one
 * goes to the server at once and is never queued, so without a connection it fails and the phone
 * keeps what it showed. The state the server answers with, also in a 409 refusal, goes to the
 * account's [TimerStateSource], which every surface reads. Start, Stop and edits of a running
 * entry stay with the change queue, and no phase rule runs here: the server applies them all.
 *
 * Each action goes out once. A request whose answer was lost may have reached the server, so it
 * is not sent again; the next read or timer event shows what it did.
 *
 * Resume, Discard, Next phase and End take the revision of the state the surface showed, not the
 * holder's latest, so a tap on an older notification or widget gets a refusal instead of acting
 * on a phase the user never saw.
 *
 * @param client the signed-in account's API client, or null when signed out.
 * @param source the account's timer state source. Each call takes it once and gives the answer to
 *   that source, so an answer for an account that signed out meanwhile changes nothing.
 * @param io where requests run, off the main thread.
 */
class TimerActions(
    private val client: () -> TogglApiClient?,
    private val source: () -> TimerStateSource,
    private val io: CoroutineContext,
) {

    /** Reads the state with `GET /api/app/timer`. */
    suspend fun read(): TimerActionResult = call { api ->
        when (val answer = api.get(api.server.app("timer"), TimerState.serializer())) {
            is ApiResult.Success -> ApiResult.Success(Refusable(answer.value, refused = false))
            is ApiResult.Failure -> answer
        }
    }

    /**
     * Pauses the running entry. The server refuses it during a session and while nothing runs.
     * Pause reads no field, so it sends an empty object.
     */
    suspend fun pause(): TimerActionResult = post(JsonObject(emptyMap()), "timer", "pause")

    /** Resumes the paused chain shown at [revision], with a clone of its fields. */
    suspend fun resume(revision: Long): TimerActionResult = post(revisioned(revision), "timer", "resume")

    /** Ends the paused chain shown at [revision], which is Stop while paused. */
    suspend fun discard(revision: Long): TimerActionResult = post(revisioned(revision), "timer", "discard")

    /**
     * Starts a session in [workspaceId] whose first pomodoro has [fields], with [lengths] in place
     * of the account's defaults. The server refuses it while a session runs, and otherwise stops
     * the running entry and ends any pause chain.
     */
    suspend fun startSession(workspaceId: Long, fields: EntryFields, lengths: SessionLengths): TimerActionResult =
        post(sessionStart(workspaceId, fields, lengths), "pomodoro")

    /** Confirms the phase shown at [revision] and starts the next one. */
    suspend fun nextPhase(revision: Long): TimerActionResult = post(revisioned(revision), "pomodoro", "next")

    /** Ends the session shown at [revision]. */
    suspend fun endSession(revision: Long): TimerActionResult = post(revisioned(revision), "pomodoro", "end")

    private suspend fun post(body: JsonObject, vararg path: String): TimerActionResult = call { api ->
        api.sendRefusable("POST", api.server.app(*path), body, TimerState.serializer())
    }

    private suspend fun call(request: (TogglApiClient) -> ApiResult<Refusable<TimerState>>): TimerActionResult =
        withContext(io) {
            val api = client() ?: return@withContext TimerActionResult.Failed(ApiError.AuthFailed)
            val holder = source()
            // Before the request, so its answer cannot undo a change the phone makes meanwhile.
            val read = holder.begin()
            when (val answer = request(api)) {
                is ApiResult.Success -> {
                    holder.accept(read, answer.value.value)
                    if (answer.value.refused) TimerActionResult.Refused else TimerActionResult.Done
                }
                is ApiResult.Failure ->
                    if (answer.error is ApiError.Network) TimerActionResult.Offline else TimerActionResult.Failed(answer.error)
            }
        }
}

/** What a timer action, or the timer read, came to. */
sealed interface TimerActionResult {

    /**
     * What the surface says, or null when it says nothing new or uses its existing error path, as
     * for [Failed].
     */
    @get:StringRes
    val message: Int?

    /** The server answered with its state, which the holder took unless it held a newer one. */
    data object Done : TimerActionResult {
        override val message: Int? get() = null
    }

    /**
     * 409: the timer was no longer as the surface showed it, and the server refused the action.
     * The holder took the server's state from the refusal, unless it held a newer one.
     */
    data object Refused : TimerActionResult {
        override val message: Int get() = R.string.timer_action_refused
    }

    /**
     * No answer came. The phone changed nothing and queued nothing. The action may still have
     * reached the server, and then the next read or timer event shows it.
     */
    data object Offline : TimerActionResult {
        override val message: Int get() = R.string.timer_action_offline
    }

    /**
     * Any other failure, for the surface's existing error path: a refused token, a trust check the
     * user has not answered, a status with the server's reason, or a body that is not a state.
     */
    data class Failed(val error: ApiError) : TimerActionResult {
        override val message: Int? get() = null
    }
}

/**
 * The lengths a session starts with, which the start form sends in place of the account's
 * defaults. Tokkl answers a length outside its range with 400, so these refuse one first.
 *
 * @property workMinutes the work phase, in minutes, as are the two rests.
 * @property longRestEvery a long rest follows every this many work phases.
 */
data class SessionLengths(
    val workMinutes: Int,
    val shortRestMinutes: Int,
    val longRestMinutes: Int,
    val longRestEvery: Int,
) {
    init {
        require(workMinutes in WORK_MINUTES) { "workMinutes must be in $WORK_MINUTES" }
        require(shortRestMinutes in REST_MINUTES) { "shortRestMinutes must be in $REST_MINUTES" }
        require(longRestMinutes in REST_MINUTES) { "longRestMinutes must be in $REST_MINUTES" }
        require(longRestEvery in LONG_REST_EVERY) { "longRestEvery must be in $LONG_REST_EVERY" }
    }

    companion object {
        /** The ranges Tokkl 1.10.0 checks in `readSessionConfig`. */
        val WORK_MINUTES = 1..180
        val REST_MINUTES = 1..60
        val LONG_REST_EVERY = 1..12
    }
}

/** The body of an action that depends on the state the surface showed. */
private fun revisioned(revision: Long): JsonObject = buildJsonObject { put("revision", revision) }

/**
 * The body of `POST /api/app/pomodoro`: the first pomodoro's fields as a v9 start sends them, tags
 * by name and the app's `created_with`, and the four lengths.
 */
private fun sessionStart(workspaceId: Long, fields: EntryFields, lengths: SessionLengths): JsonObject = buildJsonObject {
    put("workspace_id", workspaceId)
    put("created_with", CREATED_WITH)
    put("description", fields.description)
    put("project_id", fields.projectId)
    put("task_id", fields.taskId)
    put("tags", JsonArray(fields.tags.map(::JsonPrimitive)))
    put("billable", fields.billable)
    put("work_minutes", lengths.workMinutes)
    put("short_rest_minutes", lengths.shortRestMinutes)
    put("long_rest_minutes", lengths.longRestMinutes)
    put("long_rest_every", lengths.longRestEvery)
}
