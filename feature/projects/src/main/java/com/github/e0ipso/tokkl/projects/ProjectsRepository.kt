package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ProjectTask
import com.github.e0ipso.tokkl.api.TaskBody
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.getOrElse
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.catalog.managedWorkspace
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.signedIn

/**
 * The workspace's projects, for the projects page. [ServerProjectsRepository] reads and changes
 * them on the server; the view model's unit tests stand in for it without a server.
 */
interface ProjectsRepository : CatalogRepository<ProjectsData> {

    /**
     * Creates the project once. [known] are the ids of the projects the page lists, which the
     * create cannot have made.
     */
    suspend fun create(workspace: WorkspaceId, fields: ProjectFields, known: Set<ProjectId>): ApiResult<ProjectDetails>

    suspend fun update(workspace: WorkspaceId, id: ProjectId, fields: ProjectFields): ApiResult<ProjectDetails>

    /** Archives the project when [active] is false, and restores it when true. */
    suspend fun setActive(workspace: WorkspaceId, id: ProjectId, active: Boolean): ApiResult<ProjectDetails>
}

/**
 * The workspace's projects on the signed-in account's server, from the API v9 project calls. Each
 * call suspends while its request runs.
 */
class ServerProjectsRepository(private val client: ApiClientProvider) : ProjectsRepository {

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = client.managedWorkspace(stored)

    /** The workspace's projects and, for the form's hints only, its defaults, which may fail alone. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<ProjectsData> = client.signedIn { api ->
        api.projectsOf(workspace).map { projects ->
            ProjectsData(
                projects = projects.filter { it.workspaceId == workspace }.sortedForList(),
                defaults = api.account.workspace(workspace)
                    .map { WorkspaceDefaults(it.defaultCurrency, it.defaultHourlyRate) }
                    .getOrElse { null },
            )
        }
    }

    /**
     * Creates the project once, as [createOnce] does. When the answer was lost, the project with
     * the name and client of [fields] whose id is not in [known] counts as the one it made.
     */
    override suspend fun create(workspace: WorkspaceId, fields: ProjectFields, known: Set<ProjectId>) =
        client.signedIn { api ->
            createOnce(
                create = { api.projects.create(workspace, fields.body()).map { it.toDetails() } },
                list = { api.projectsOf(workspace) },
                made = {
                    it.id !in known && it.workspaceId == workspace && it.name == fields.name &&
                        it.clientId == fields.clientId
                },
            )
        }

    override suspend fun update(workspace: WorkspaceId, id: ProjectId, fields: ProjectFields) =
        client.signedIn { api -> api.projects.update(workspace, id, fields.body()).map { it.toDetails() } }

    /** Archives the project when [active] is false and restores it when true. */
    override suspend fun setActive(workspace: WorkspaceId, id: ProjectId, active: Boolean) =
        client.signedIn { api -> api.projects.setActive(workspace, id, active).map { it.toDetails() } }
}

/** Every project of [workspace], archived ones included, in the server's order. */
private suspend fun TokklApi.projectsOf(workspace: WorkspaceId): ApiResult<List<ProjectDetails>> =
    projects.list(workspace).map { projects -> projects.map { it.toDetails() } }

/**
 * The tasks of a project, for the task list in the project form. [ServerTasksRepository] reads and
 * changes them on the server; the task list's unit tests stand in for it without a server.
 */
interface TasksRepository {

    /** The workspace the list manages, as [CatalogRepository.workspace] finds it. */
    suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId>

    /** [project]'s tasks, open ones first, as the web app lists them. */
    suspend fun load(workspace: WorkspaceId, project: ProjectId): ApiResult<TasksData>

    /**
     * Creates the task named [name] once. [known] are the ids of the tasks the list shows, which
     * the create cannot have made.
     */
    suspend fun create(workspace: WorkspaceId, project: ProjectId, name: String, known: Set<TaskId>): ApiResult<Task>

    suspend fun update(
        workspace: WorkspaceId,
        project: ProjectId,
        id: TaskId,
        name: String,
        done: Boolean,
    ): ApiResult<Task>

    suspend fun delete(workspace: WorkspaceId, project: ProjectId, id: TaskId): ApiResult<Unit>
}

/**
 * The tasks of a project on the signed-in account's server, from the API v9 task calls. Each call
 * suspends while its request runs.
 */
class ServerTasksRepository(private val client: ApiClientProvider) : TasksRepository {

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = client.managedWorkspace(stored)

    override suspend fun load(workspace: WorkspaceId, project: ProjectId): ApiResult<TasksData> =
        client.signedIn { api ->
            api.tasksOf(workspace, project).map { tasks ->
                TasksData(tasks.filter { it.projectId == project }.sortedForList())
            }
        }

    /**
     * Creates the task once, as [createOnce] does. When the answer was lost, the project's task
     * named [name] whose id is not in [known] counts as the one it made.
     */
    override suspend fun create(workspace: WorkspaceId, project: ProjectId, name: String, known: Set<TaskId>) =
        client.signedIn { api ->
            createOnce(
                create = { api.projects.createTask(workspace, project, name).map { it.toTask(workspace) } },
                list = { api.tasksOf(workspace, project) },
                made = { it.id !in known && it.projectId == project && it.name == name },
            )
        }

    /** Renames the task and marks it done or not done, as the web app's task row does. */
    override suspend fun update(workspace: WorkspaceId, project: ProjectId, id: TaskId, name: String, done: Boolean) =
        client.signedIn { api ->
            api.projects.updateTask(workspace, project, id, TaskBody(name, active = !done)).map { it.toTask(workspace) }
        }

    /** Deletes the task. The server takes it off the time entries that had it, which stay. */
    override suspend fun delete(workspace: WorkspaceId, project: ProjectId, id: TaskId) =
        client.signedIn { it.projects.deleteTask(workspace, project, id) }
}

/** Every task of [project], done ones included, sorted by name. */
private suspend fun TokklApi.tasksOf(workspace: WorkspaceId, project: ProjectId): ApiResult<List<Task>> =
    projects.tasks(workspace, project).map { tasks -> tasks.map { it.toTask(workspace) } }

/** A task of the list, which the tasks route lists without its workspace. */
private fun ProjectTask.toTask(workspace: WorkspaceId) = Task(id, workspace, projectId, name, active)
