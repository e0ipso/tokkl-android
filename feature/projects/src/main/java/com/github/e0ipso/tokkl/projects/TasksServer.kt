package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ProjectTask
import com.github.e0ipso.tokkl.api.TaskBody
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId

/**
 * The API v9 task calls of [project] in [workspace] on the configured server. Each call suspends
 * while its request runs.
 */
class TasksServer(private val api: TokklApi, private val workspace: WorkspaceId, private val project: ProjectId) {

    /** Every task of the project, done ones included, sorted by name. */
    suspend fun list(): ApiResult<List<Task>> =
        api.projects.tasks(workspace, project).map { tasks -> tasks.map { it.toTask(workspace) } }

    /**
     * Creates the task once, as [createOnce] does. When the answer was lost, the project's task
     * named [name] whose id is not in [known] counts as the one it made.
     *
     * @param known the ids of the tasks the list shows, which the create cannot have made.
     */
    suspend fun create(name: String, known: Set<TaskId>): ApiResult<Task> = createOnce(
        create = {
            api.projects.createTask(workspace, project, name).map { it.toTask(workspace) }
        },
        list = ::list,
        made = { it.id !in known && it.projectId == project && it.name == name },
    )

    /** Renames the task and marks it done or not done, as the web app's task row does. */
    suspend fun update(id: TaskId, name: String, done: Boolean): ApiResult<Task> =
        api.projects.updateTask(workspace, project, id, TaskBody(name, active = !done)).map { it.toTask(workspace) }

    /** Deletes the task. The server takes it off the time entries that had it, which stay. */
    suspend fun delete(id: TaskId): ApiResult<Unit> = api.projects.deleteTask(workspace, project, id)
}

/** A task of the list, which the tasks route lists without its workspace. */
private fun ProjectTask.toTask(workspace: WorkspaceId) = Task(id, workspace, projectId, name, active)
