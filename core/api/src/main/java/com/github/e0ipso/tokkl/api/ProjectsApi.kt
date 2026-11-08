package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

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

/** A workspace's projects and their tasks. */
class ProjectsApi internal constructor(private val client: TogglApiClient) {

    /** `GET /api/v9/workspaces/{workspaceId}/projects`, archived projects included. */
    suspend fun list(workspaceId: WorkspaceId): ApiResult<List<WorkspaceProject>> =
        client.get(projects(workspaceId), ListSerializer(WorkspaceProject.serializer()))

    /**
     * `GET /api/v9/workspaces/{workspaceId}/projects?active=both`, as the clients page asks, with
     * each project's client.
     */
    suspend fun clients(workspaceId: WorkspaceId): ApiResult<List<ProjectClient>> = client.get(
        projects(workspaceId).newBuilder().addQueryParameter("active", "both").build(),
        ListSerializer(ProjectClient.serializer()),
    )

    /** `POST /api/v9/workspaces/{workspaceId}/projects`, sent once. */
    suspend fun create(workspaceId: WorkspaceId, body: ProjectBody): ApiResult<WorkspaceProject> = client.send(
        HttpMethod.POST,
        projects(workspaceId),
        json(ProjectBody.serializer(), body),
        WorkspaceProject.serializer(),
        resend = false,
    )

    /** `PUT /api/v9/workspaces/{workspaceId}/projects/{id}` with every field the form shows. */
    suspend fun update(workspaceId: WorkspaceId, id: ProjectId, body: ProjectBody): ApiResult<WorkspaceProject> =
        client.send(
            HttpMethod.PUT,
            projects(workspaceId, id.toString()),
            json(ProjectBody.serializer(), body),
            WorkspaceProject.serializer(),
        )

    /** `PUT /api/v9/workspaces/{workspaceId}/projects/{id}` with `active` alone: false archives it. */
    suspend fun setActive(workspaceId: WorkspaceId, id: ProjectId, active: Boolean): ApiResult<WorkspaceProject> =
        client.send(
            HttpMethod.PUT,
            projects(workspaceId, id.toString()),
            json(ActiveBody.serializer(), ActiveBody(active)),
            WorkspaceProject.serializer(),
        )

    /** `GET /api/v9/workspaces/{workspaceId}/projects/{projectId}/tasks`, done tasks included. */
    suspend fun tasks(workspaceId: WorkspaceId, projectId: ProjectId): ApiResult<List<ProjectTask>> =
        client.get(taskUrl(workspaceId, projectId), ListSerializer(ProjectTask.serializer()))

    /** `POST /api/v9/workspaces/{workspaceId}/projects/{projectId}/tasks` with [name], sent once. */
    suspend fun createTask(workspaceId: WorkspaceId, projectId: ProjectId, name: String): ApiResult<ProjectTask> =
        client.send(
            HttpMethod.POST,
            taskUrl(workspaceId, projectId),
            json(NewTaskBody.serializer(), NewTaskBody(name)),
            ProjectTask.serializer(),
            resend = false,
        )

    /** `PUT .../projects/{projectId}/tasks/{id}` with [body]. */
    suspend fun updateTask(
        workspaceId: WorkspaceId,
        projectId: ProjectId,
        id: TaskId,
        body: TaskBody,
    ): ApiResult<ProjectTask> = client.send(
        HttpMethod.PUT,
        taskUrl(workspaceId, projectId, id.toString()),
        json(TaskBody.serializer(), body),
        ProjectTask.serializer(),
    )

    /** `DELETE .../projects/{projectId}/tasks/{id}`. Tokkl answers with no body. */
    suspend fun deleteTask(workspaceId: WorkspaceId, projectId: ProjectId, id: TaskId): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.DELETE, taskUrl(workspaceId, projectId, id.toString()))

    private fun projects(workspaceId: WorkspaceId, vararg path: String) =
        client.server.api("workspaces", workspaceId.toString(), "projects", *path)

    private fun taskUrl(workspaceId: WorkspaceId, projectId: ProjectId, vararg path: String) =
        projects(workspaceId, projectId.toString(), "tasks", *path)
}
