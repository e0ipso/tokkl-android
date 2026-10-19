package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A task of a project as `GET /api/v9/workspaces/{wid}/projects/{pid}/tasks` sends it. A done task is not [active]. */
@Serializable
data class ProjectTask(
    val id: Long,
    @SerialName("project_id") val projectId: Long,
    val name: String = "",
    val active: Boolean = true,
)

/**
 * The API v9 task calls of [project] in [workspace] on the configured server. Every call blocks,
 * so make them off the main thread.
 */
class TasksServer(private val api: TogglApiClient, private val workspace: Long, private val project: Long) {

    /** Every task of the project, done ones included, sorted by name. */
    fun list(): ApiResult<List<ProjectTask>> = api.get(url(), ListSerializer(ProjectTask.serializer()))

    fun create(name: String): ApiResult<ProjectTask> =
        api.send("POST", url(), buildJsonObject { put("name", name) }, ProjectTask.serializer())

    /** Renames the task and marks it done or not done, as the web app's task row does. */
    fun update(id: Long, name: String, done: Boolean): ApiResult<ProjectTask> =
        api.send(
            "PUT",
            url(id.toString()),
            buildJsonObject {
                put("name", name)
                put("active", !done)
            },
            ProjectTask.serializer(),
        )

    /** Deletes the task. The server takes it off the time entries that had it, which stay. */
    fun delete(id: Long): ApiResult<Unit> = api.sendNoContent("DELETE", url(id.toString()))

    private fun url(vararg path: String) =
        api.server.api("workspaces", workspace.toString(), "projects", project.toString(), "tasks", *path)
}
