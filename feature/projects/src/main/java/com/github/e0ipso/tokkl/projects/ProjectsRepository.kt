package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.getOrElse
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.workspace
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.SignedInApi

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

/** The workspace's projects on the signed-in account's server, through [ProjectsServer]. */
class ServerProjectsRepository(client: ApiClientProvider) : ProjectsRepository {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = api.workspace(stored)

    /** The workspace's projects and, for the form's hints only, its defaults, which may fail alone. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<ProjectsData> = api.call { client ->
        val server = ProjectsServer(client, workspace)
        server.list().map { projects ->
            ProjectsData(
                projects = projects.filter { it.workspaceId == workspace }.sortedForList(),
                defaults = server.defaults().getOrElse { null },
            )
        }
    }

    /** As [ProjectsServer.create] does. */
    override suspend fun create(workspace: WorkspaceId, fields: ProjectFields, known: Set<ProjectId>) =
        api.call { ProjectsServer(it, workspace).create(fields, known) }

    override suspend fun update(workspace: WorkspaceId, id: ProjectId, fields: ProjectFields) =
        api.call { ProjectsServer(it, workspace).update(id, fields) }

    override suspend fun setActive(workspace: WorkspaceId, id: ProjectId, active: Boolean) =
        api.call { ProjectsServer(it, workspace).setActive(id, active) }
}

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

/** The tasks of a project on the signed-in account's server, through [TasksServer]. */
class ServerTasksRepository(client: ApiClientProvider) : TasksRepository {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = api.workspace(stored)

    override suspend fun load(workspace: WorkspaceId, project: ProjectId): ApiResult<TasksData> = api.call { client ->
        TasksServer(client, workspace, project).list().map { tasks ->
            TasksData(tasks.filter { it.projectId == project }.sortedForList())
        }
    }

    /** As [TasksServer.create] does. */
    override suspend fun create(workspace: WorkspaceId, project: ProjectId, name: String, known: Set<TaskId>) =
        api.call { TasksServer(it, workspace, project).create(name, known) }

    override suspend fun update(workspace: WorkspaceId, project: ProjectId, id: TaskId, name: String, done: Boolean) =
        api.call { TasksServer(it, workspace, project).update(id, name, done) }

    override suspend fun delete(workspace: WorkspaceId, project: ProjectId, id: TaskId) =
        api.call { TasksServer(it, workspace, project).delete(id) }
}
