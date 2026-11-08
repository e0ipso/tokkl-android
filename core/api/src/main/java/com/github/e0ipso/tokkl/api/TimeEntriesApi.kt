package com.github.e0ipso.tokkl.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * The fields of a time entry that a create or an update sends, in one of two shapes: with times,
 * or as a no-time entry. Times go as RFC 3339 instants. Tags go by name, and the server creates
 * any it does not know.
 */
sealed interface TimeEntryBody

/**
 * An entry with a [start] and a [stop], or a running one with a null [stop] and a [duration] of
 * -1. [createdWith] goes only when set, as on a create. [allDay] goes only when set: false turns a
 * no-time entry back into a timed one on a server that stores no-time entries.
 */
@Serializable
data class TimedEntryBody(
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("created_with") val createdWith: String? = null,
    val description: String,
    @SerialName("project_id") val projectId: Long?,
    @SerialName("task_id") val taskId: Long?,
    val tags: List<String>,
    val billable: Boolean,
    val start: String,
    val stop: String?,
    val duration: Long,
    @SerialName("all_day") val allDay: Boolean? = null,
) : TimeEntryBody

/**
 * A no-time entry: its day as [startDate], or without one its local midnight as [start], and its
 * [duration], with neither a start time nor a stop. [allDay] is true. [createdWith] goes only when
 * set, as on a create.
 */
@Serializable
data class NoTimeEntryBody(
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("created_with") val createdWith: String? = null,
    val description: String,
    @SerialName("project_id") val projectId: Long?,
    @SerialName("task_id") val taskId: Long?,
    val tags: List<String>,
    val billable: Boolean,
    @SerialName("all_day") val allDay: Boolean,
    @SerialName("start_date") val startDate: String? = null,
    val start: String? = null,
    val duration: Long,
) : TimeEntryBody

@Serializable
private class StopBody(val stop: String)

/**
 * The user's time entries. The writes go out once: OkHttp does not send one again on a new
 * connection by itself, so a lost answer reaches the caller, which decides whether to retry.
 */
class TimeEntriesApi internal constructor(private val client: TogglApiClient) {

    /**
     * `GET /api/v9/me/time_entries?start_date=…&end_date=…`: the entries that start from
     * [startDate] to [endDate]. Both go as instants, which Tokkl and Toggl read the same way.
     */
    suspend fun list(startDate: Instant, endDate: Instant): ApiResult<List<ServerTimeEntry>> = client.get(
        client.server.api("me", "time_entries").newBuilder()
            .addQueryParameter("start_date", startDate.toString())
            .addQueryParameter("end_date", endDate.toString())
            .build(),
        ListSerializer(ServerTimeEntry.serializer()),
    )

    /** `GET /api/v9/me/time_entries/current`: the running entry, or null when none runs. */
    suspend fun current(): ApiResult<ServerTimeEntry?> =
        client.get(client.server.api("me", "time_entries", "current"), ServerTimeEntry.serializer().nullable)

    /** `GET /api/v9/me/time_entries/{id}`. */
    suspend fun get(id: Long): ApiResult<ServerTimeEntry> =
        client.get(client.server.api("me", "time_entries", id.toString()), ServerTimeEntry.serializer())

    /** `POST /api/v9/workspaces/{workspaceId}/time_entries`, sent once. */
    suspend fun create(workspaceId: Long, body: TimeEntryBody): ApiResult<ServerTimeEntry> =
        client.send(HttpMethod.POST, entries(workspaceId), body.json(), ServerTimeEntry.serializer(), resend = false)

    /** `PUT /api/v9/workspaces/{workspaceId}/time_entries/{id}` with every field, sent once. */
    suspend fun update(workspaceId: Long, id: Long, body: TimeEntryBody): ApiResult<ServerTimeEntry> =
        client.send(HttpMethod.PUT, entries(workspaceId, id), body.json(), ServerTimeEntry.serializer(), resend = false)

    /** `PATCH /api/v9/workspaces/{workspaceId}/time_entries/{id}/stop` at [stop], sent once. */
    suspend fun stop(workspaceId: Long, id: Long, stop: Instant): ApiResult<ServerTimeEntry> = client.send(
        HttpMethod.PATCH,
        entries(workspaceId, id.toString(), "stop"),
        json(StopBody.serializer(), StopBody(stop.toString())),
        ServerTimeEntry.serializer(),
        resend = false,
    )

    /** `DELETE /api/v9/workspaces/{workspaceId}/time_entries/{id}`, sent once. Tokkl answers with no body. */
    suspend fun delete(workspaceId: Long, id: Long): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.DELETE, entries(workspaceId, id), body = null, resend = false)

    /**
     * `POST /api/app/time_entries/{id}/restore`, Tokkl's Undo of a delete, which answers with the
     * entry. With [resend] false it goes out once, as [TogglApiClient.send] describes.
     */
    suspend fun restore(id: Long, resend: Boolean): ApiResult<ServerTimeEntry> = client.send(
        HttpMethod.POST,
        client.server.app("time_entries", id.toString(), "restore"),
        EMPTY_BODY,
        ServerTimeEntry.serializer(),
        resend = resend,
    )

    /** `GET /api/app/bounds`, Tokkl's first and last day with an entry in the account's timezone. */
    suspend fun bounds(): ApiResult<JsonObject> = client.get(client.server.app("bounds"), JsonObject.serializer())

    private fun entries(workspaceId: Long, vararg path: String) =
        client.server.api("workspaces", workspaceId.toString(), "time_entries", *path)

    private fun entries(workspaceId: Long, id: Long) = entries(workspaceId, id.toString())

    private fun TimeEntryBody.json(): JsonElement = when (this) {
        is TimedEntryBody -> json(TimedEntryBody.serializer(), this)
        is NoTimeEntryBody -> json(NoTimeEntryBody.serializer(), this)
    }
}
