package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.accountSettingsIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The project form's task list over a repository where project 5 in workspace 7 has the open task
 * Design and the done task Brief, and project 6, which is archived, has none. Unconfined runs
 * each call to the end inside the call.
 */
class TasksModelTest {

    private val repository = TwoProjectsRepository()
    private val refresh = CountingRefresh()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val model = TasksModel(
        repository = repository,
        settings = accountSettingsIn(WorkspaceId(7)),
        changes = ServerChanges(scope, Dispatchers.Unconfined, refresh),
        scope = scope,
        io = Dispatchers.Unconfined,
    )

    @Test
    fun openingAProjectsFormLoadsItsTasksOnce() {
        model.open(WEBSITE)

        assertEquals(listOf("Design", "brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(listOf("load 5 in 7"), repository.calls)
    }

    @Test
    fun aNewTaskGoesWithItsNameTrimmedAndRefreshesTheCache() {
        model.open(WEBSITE)
        val opened = repository.calls.size

        model.create("  Copy ")

        assertEquals("create Copy in 5, known [1, 2]", repository.calls[opened])
        assertEquals(CatalogChange.Created("Copy"), model.state.value.done)
        assertEquals(listOf("Copy", "Design", "brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun theFormRenamesTheTaskAndMarksItDone() {
        model.open(WEBSITE)
        val design = model.state.value.data!!.tasks.first()
        model.askToEdit(design)
        val asked = repository.calls.size

        model.save(" Wireframes ", done = true)

        assertEquals("update 1 in 5 to Wireframes, done true", repository.calls[asked])
        assertEquals(CatalogChange.Saved("Wireframes"), model.state.value.done)
        assertNull("the form closes", model.state.value.overlay)
        assertEquals(
            "a done task goes after the open ones",
            listOf("brief", "Wireframes"),
            model.state.value.data!!.tasks.map { it.name },
        )
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun deletingAsksFirstAndCancellingKeepsTheTask() {
        model.open(WEBSITE)
        val design = model.state.value.data!!.tasks.first()
        val opened = repository.calls.size

        model.askToDelete(design)
        assertEquals(TaskOverlay.ConfirmDelete(design), model.state.value.overlay)
        model.dismiss()
        assertEquals("cancelling sends nothing", opened, repository.calls.size)
        assertEquals(2, model.state.value.data!!.tasks.size)

        model.askToDelete(design)
        model.delete()
        assertEquals("delete 1 in 5", repository.calls[opened])
        assertEquals(CatalogChange.Deleted("Design"), model.state.value.done)
        assertEquals(listOf("brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun anotherProjectsFormLoadsItsOwnTasks() {
        model.open(WEBSITE)
        model.open(WEBSITE)
        model.open(ARCHIVED)

        assertEquals(emptyList<Task>(), model.state.value.data!!.tasks)
        assertEquals(listOf("load 5 in 7", "load 6 in 7"), repository.calls)
    }

    @Test
    fun aRefusalKeepsTheListWithTheServersReason() {
        model.open(ARCHIVED)

        model.create("Copy")

        assertEquals(ServerProblem.Refused("Can not change tasks of archived project"), model.state.value.changeProblem)
        assertEquals(0, refresh.afterChanges)
    }

    /**
     * Project 5's tasks, Design open and Brief done, already in the order the server repository
     * sorts them; project 6, archived, with none, which refuses every change. Records each call.
     */
    private class TwoProjectsRepository : TasksRepository {
        val calls = mutableListOf<String>()

        override suspend fun workspace(stored: WorkspaceId?) = ApiResult.Success(checkNotNull(stored))

        override suspend fun load(workspace: WorkspaceId, project: ProjectId): ApiResult<TasksData> {
            calls += "load ${project.value} in ${workspace.value}"
            return ApiResult.Success(
                TasksData(
                    when (project) {
                        WEBSITE -> listOf(
                            Task(TaskId(1), workspace, project, "Design", active = true),
                            Task(TaskId(2), workspace, project, "brief", active = false),
                        )

                        ARCHIVED -> emptyList()

                        else -> throw AssertionError("the test opens no project $project")
                    },
                ),
            )
        }

        override suspend fun create(
            workspace: WorkspaceId,
            project: ProjectId,
            name: String,
            known: Set<TaskId>,
        ): ApiResult<Task> {
            calls += "create $name in ${project.value}, known ${known.map { it.value }.sorted()}"
            if (project == ARCHIVED) return ApiResult.Failure(ApiError.UnexpectedStatus(400, REFUSAL))
            return ApiResult.Success(Task(TaskId(3), workspace, project, name, active = true))
        }

        override suspend fun update(
            workspace: WorkspaceId,
            project: ProjectId,
            id: TaskId,
            name: String,
            done: Boolean,
        ): ApiResult<Task> {
            calls += "update ${id.value} in ${project.value} to $name, done $done"
            return ApiResult.Success(Task(id, workspace, project, name, active = !done))
        }

        override suspend fun delete(workspace: WorkspaceId, project: ProjectId, id: TaskId): ApiResult<Unit> {
            calls += "delete ${id.value} in ${project.value}"
            return ApiResult.Success(Unit)
        }
    }

    private companion object {
        val WEBSITE = ProjectId(5)
        val ARCHIVED = ProjectId(6)
        const val REFUSAL = "Can not change tasks of archived project"
    }
}
