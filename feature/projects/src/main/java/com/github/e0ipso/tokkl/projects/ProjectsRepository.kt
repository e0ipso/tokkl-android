package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.SignedInApi
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId

/** The workspace's projects on the server, for the projects page. */
interface ProjectsRepository : CatalogRepository<ProjectsData> {

    /**
     * Creates the project once, as [ProjectsServer.create] does. [known] are the ids of the
     * projects the page lists, which the create cannot have made.
     */
    suspend fun create(workspace: WorkspaceId, fields: ProjectFields, known: Set<ProjectId>): ApiResult<ProjectDetails>

    suspend fun update(workspace: WorkspaceId, id: ProjectId, fields: ProjectFields): ApiResult<ProjectDetails>

    /** Archives the project when [active] is false, and restores it when true. */
    suspend fun setActive(workspace: WorkspaceId, id: ProjectId, active: Boolean): ApiResult<ProjectDetails>
}

/** [ProjectsRepository] through the signed-in account's [client] and [ProjectsServer]. */
class ServerProjectsRepository(client: ApiClientProvider) : ProjectsRepository {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = api.workspace(stored)

    /** The workspace's projects and, for the form's hints only, its defaults, which may fail alone. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<ProjectsData> = api.call { client ->
        val server = ProjectsServer(client, workspace)
        when (val projects = server.list()) {
            is ApiResult.Success -> ApiResult.Success(
                ProjectsData(
                    projects = projects.value.filter { it.workspaceId == workspace }.sortedForList(),
                    defaults = (server.defaults() as? ApiResult.Success)?.value,
                ),
            )

            is ApiResult.Failure -> projects
        }
    }

    override suspend fun create(workspace: WorkspaceId, fields: ProjectFields, known: Set<ProjectId>) =
        api.call { ProjectsServer(it, workspace).create(fields, known) }

    override suspend fun update(workspace: WorkspaceId, id: ProjectId, fields: ProjectFields) =
        api.call { ProjectsServer(it, workspace).update(id, fields) }

    override suspend fun setActive(workspace: WorkspaceId, id: ProjectId, active: Boolean) =
        api.call { ProjectsServer(it, workspace).setActive(id, active) }
}

/** The tasks of a project on the server, for the task list in the project form. */
interface TasksRepository {

    /** The workspace the list manages, as [CatalogRepository.workspace] finds it. */
    suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId>

    /** [project]'s tasks, open ones first, as the web app lists them. */
    suspend fun load(workspace: WorkspaceId, project: ProjectId): ApiResult<TasksData>

    /**
     * Creates the task once, as [TasksServer.create] does. [known] are the ids of the tasks the
     * list shows, which the create cannot have made.
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

/** [TasksRepository] through the signed-in account's [client] and [TasksServer]. */
class ServerTasksRepository(client: ApiClientProvider) : TasksRepository {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = api.workspace(stored)

    override suspend fun load(workspace: WorkspaceId, project: ProjectId): ApiResult<TasksData> = api.call { client ->
        when (val answer = TasksServer(client, workspace, project).list()) {
            is ApiResult.Success -> ApiResult.Success(
                TasksData(answer.value.filter { it.projectId == project }.sortedForList()),
            )

            is ApiResult.Failure -> answer
        }
    }

    override suspend fun create(workspace: WorkspaceId, project: ProjectId, name: String, known: Set<TaskId>) =
        api.call { TasksServer(it, workspace, project).create(name, known) }

    override suspend fun update(workspace: WorkspaceId, project: ProjectId, id: TaskId, name: String, done: Boolean) =
        api.call { TasksServer(it, workspace, project).update(id, name, done) }

    override suspend fun delete(workspace: WorkspaceId, project: ProjectId, id: TaskId) =
        api.call { TasksServer(it, workspace, project).delete(id) }
}
