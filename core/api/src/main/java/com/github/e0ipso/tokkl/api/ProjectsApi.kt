package com.github.e0ipso.tokkl.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the project form sends, as the web app's ProjectBody: every field, so a save writes what
 * the form shows. No client goes as 0, as the web app sends it; Tokkl reads 0 and null alike.
 * A null [rate], [currency] or [estimatedHours] goes as null.
 */
@Serializable
data class ProjectBody(
    val name: String,
    val color: String,
    @SerialName("client_id") val clientId: Long,
    val billable: Boolean,
    val rate: Double?,
    val currency: String?,
    @SerialName("estimated_hours") val estimatedHours: Double?,
)

@Serializable
private class ActiveBody(val active: Boolean)

/** A task's name, and [active], false for a task marked done. */
@Serializable
data class TaskBody(val name: String, val active: Boolean)

@Serializable
private class NewTaskBody(val name: String)

/**
 * A workspace's projects and their tasks. The answers read as the caller's [DeserializationStrategy],
 * since the pages that send them own the shapes they read.
 */
class ProjectsApi internal constructor(private val client: TogglApiClient) {

    /**
     * `GET /api/v9/workspaces/{workspaceId}/projects`, archived projects included. With
     * [activeBoth] it adds `active=both`, as the clients page asks.
     */
    suspend fun <T> list(
        workspaceId: Long,
        deserializer: DeserializationStrategy<T>,
        activeBoth: Boolean = false,
    ): ApiResult<T> {
        val url = projects(workspaceId).let {
            if (activeBoth) it.newBuilder().addQueryParameter("active", "both").build() else it
        }
        return client.get(url, deserializer)
    }

    /** `POST /api/v9/workspaces/{workspaceId}/projects`, sent once. */
    suspend fun <T> create(
        workspaceId: Long,
        body: ProjectBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.POST,
        projects(workspaceId),
        json(ProjectBody.serializer(), body),
        deserializer,
        resend = false,
    )

    /** `PUT /api/v9/workspaces/{workspaceId}/projects/{id}` with every field the form shows. */
    suspend fun <T> update(
        workspaceId: Long,
        id: Long,
        body: ProjectBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.PUT,
        projects(workspaceId, id.toString()),
        json(ProjectBody.serializer(), body),
        deserializer,
    )

    /** `PUT /api/v9/workspaces/{workspaceId}/projects/{id}` with `active` alone: false archives it. */
    suspend fun <T> setActive(
        workspaceId: Long,
        id: Long,
        active: Boolean,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.PUT,
        projects(workspaceId, id.toString()),
        json(ActiveBody.serializer(), ActiveBody(active)),
        deserializer,
    )

    /** `GET /api/v9/workspaces/{workspaceId}/projects/{projectId}/tasks`, done tasks included. */
    suspend fun <T> tasks(workspaceId: Long, projectId: Long, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.get(tasks(workspaceId, projectId), deserializer)

    /** `POST /api/v9/workspaces/{workspaceId}/projects/{projectId}/tasks` with [name], sent once. */
    suspend fun <T> createTask(
        workspaceId: Long,
        projectId: Long,
        name: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.POST,
        tasks(workspaceId, projectId),
        json(NewTaskBody.serializer(), NewTaskBody(name)),
        deserializer,
        resend = false,
    )

    /** `PUT .../projects/{projectId}/tasks/{id}` with [body]. */
    suspend fun <T> updateTask(
        workspaceId: Long,
        projectId: Long,
        id: Long,
        body: TaskBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.PUT,
        tasks(workspaceId, projectId, id.toString()),
        json(TaskBody.serializer(), body),
        deserializer,
    )

    /** `DELETE .../projects/{projectId}/tasks/{id}`. Tokkl answers with no body. */
    suspend fun deleteTask(workspaceId: Long, projectId: Long, id: Long): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.DELETE, tasks(workspaceId, projectId, id.toString()))

    private fun projects(workspaceId: Long, vararg path: String) =
        client.server.api("workspaces", workspaceId.toString(), "projects", *path)

    private fun tasks(workspaceId: Long, projectId: Long, vararg path: String) =
        projects(workspaceId, projectId.toString(), "tasks", *path)
}
