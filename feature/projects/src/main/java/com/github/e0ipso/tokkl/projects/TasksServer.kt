package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TaskBody
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.catalog.createOnce
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A task of a project as `GET /api/v9/workspaces/{wid}/projects/{pid}/tasks` sends it. A done task is not [active]. */
@Serializable
data class ProjectTask(
    val id: Long,
    @SerialName("project_id") val projectId: Long,
    val name: String = "",
    val active: Boolean = true,
)

/**
 * The API v9 task calls of [project] in [workspace] on the configured server. Each call suspends
 * while its request runs.
 */
class TasksServer(private val api: TokklApi, private val workspace: Long, private val project: Long) {

    /** Every task of the project, done ones included, sorted by name. */
    suspend fun list(): ApiResult<List<ProjectTask>> =
        api.projects.tasks(workspace, project, ListSerializer(ProjectTask.serializer()))

    /**
     * Creates the task once, as [createOnce] does. When the answer was lost, the project's task
     * named [name] whose id is not in [known] counts as the one it made.
     *
     * @param known the ids of the tasks the list shows, which the create cannot have made.
     */
    suspend fun create(name: String, known: Set<Long>): ApiResult<ProjectTask> = createOnce(
        create = { api.projects.createTask(workspace, project, name, ProjectTask.serializer()) },
        list = ::list,
        made = { it.id !in known && it.projectId == project && it.name == name },
    )

    /** Renames the task and marks it done or not done, as the web app's task row does. */
    suspend fun update(id: Long, name: String, done: Boolean): ApiResult<ProjectTask> =
        api.projects.updateTask(workspace, project, id, TaskBody(name, active = !done), ProjectTask.serializer())

    /** Deletes the task. The server takes it off the time entries that had it, which stay. */
    suspend fun delete(id: Long): ApiResult<Unit> = api.projects.deleteTask(workspace, project, id)
}
