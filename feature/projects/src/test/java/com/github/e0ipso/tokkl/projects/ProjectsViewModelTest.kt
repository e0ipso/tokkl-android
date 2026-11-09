package com.github.e0ipso.tokkl.projects

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.accountSettingsIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The projects page's model over repositories with one project, Website, in workspace 7, and its
 * one task, Design. Unconfined runs each call to the end inside the call.
 */
class ProjectsViewModelTest {

    private val projects = WebsiteRepository()
    private val tasks = DesignTaskRepository()
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private lateinit var model: ProjectsViewModel
    private lateinit var screen: TestScreen
    private val saved = SavedStateHandle()

    @Before
    fun setUp() {
        model = projectsModel(saved)
        screen = TestScreen(model.page, scope, scheduler)
    }

    private fun projectsModel(saved: SavedStateHandle) = ProjectsViewModel(
        repository = projects,
        tasksRepository = tasks,
        settings = accountSettingsIn(WorkspaceId(7)),
        changes = ServerChanges(scope, Dispatchers.Unconfined, CountingRefresh()),
        scope = scope,
        io = Dispatchers.Unconfined,
        cachedProjects = flowOf(emptyList()),
        cachedClients = flowOf(emptyList()),
        offline = MutableStateFlow(false),
        app = TestApp(scheduler).app,
        saved = saved,
    )

    /** The page's model in a new process, which gets back only what the old one's saved state held. */
    private fun projectsModelAfterProcessDeath(): ProjectsViewModel =
        projectsModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))

    /**
     * Another activity hides the page for longer than its state is shared. The open project form
     * stays with its tasks, and the page loads the projects and the tasks again when it shows.
     */
    @Test
    fun theOpenFormAndItsTasksSurviveTheScreenHiddenForLonger() {
        screen.show()
        val website = model.state.value.data!!.projects.single()
        model.edit(website)
        assertEquals(listOf(WEBSITE), tasks.loads)

        screen.leave()
        screen.show()

        assertEquals(ProjectForm.of(website, projectCount = 1), model.state.value.overlay)
        assertEquals("Design", model.tasks.state.value.data?.tasks?.single()?.name)
        assertEquals(2, projects.loads)
        assertEquals(listOf(WEBSITE, WEBSITE), tasks.loads)
    }

    /**
     * The process dies with Website's form open. The page that a new process restores opens that
     * form again with what was typed, and loads its tasks once the page shows, not before.
     */
    @Test
    fun aNewProcessOpensTheEditFormAsItWasTypedAndLoadsItsTasksOnShow() {
        screen.show()
        val website = model.state.value.data!!.projects.single()
        model.edit(website)
        val typed = model.state.value.overlay!!.draft.copy(name = "Web site", rate = "12,5", billable = true)
        model.editDraft(typed)

        val restored = projectsModelAfterProcessDeath()

        assertEquals(ProjectForm.Edit(website, typed), restored.state.value.overlay)
        assertEquals("no task list loads before the page shows", listOf(WEBSITE), tasks.loads)
        TestScreen(restored.page, scope, scheduler).show()
        assertEquals(listOf(WEBSITE, WEBSITE), tasks.loads)
        assertEquals("Design", restored.tasks.state.value.data?.tasks?.single()?.name)
        assertEquals(ProjectForm.Edit(website, typed), restored.state.value.overlay)
    }

    /** A new project's form comes back with what was typed, and a closed form stays closed. */
    @Test
    fun aNewProcessOpensTheNewProjectFormAsItWasTypedAndNoClosedOne() {
        screen.show()
        model.newProject()
        val typed = model.state.value.overlay!!.draft.copy(name = "Mural", estimate = "40")
        model.editDraft(typed)

        assertEquals(ProjectForm.New(typed), projectsModelAfterProcessDeath().state.value.overlay)

        model.closeForm()
        assertNull(projectsModelAfterProcessDeath().state.value.overlay)
    }

    /** Lists Website in workspace 7 and counts the loads; the changes are not sent. */
    private class WebsiteRepository : ProjectsRepository {
        var loads = 0

        override suspend fun workspace(stored: WorkspaceId?) = ApiResult.Success(checkNotNull(stored))

        override suspend fun load(workspace: WorkspaceId): ApiResult<ProjectsData> {
            loads++
            return ApiResult.Success(ProjectsData(listOf(ProjectDetails(WEBSITE, workspace, "Website")), null))
        }

        override suspend fun create(workspace: WorkspaceId, fields: ProjectFields, known: Set<ProjectId>) =
            notSent("create")

        override suspend fun update(workspace: WorkspaceId, id: ProjectId, fields: ProjectFields) = notSent("update")

        override suspend fun setActive(workspace: WorkspaceId, id: ProjectId, active: Boolean) = notSent("setActive")
    }

    /** Lists Design, Website's one task, and records the project of each load; the changes are not sent. */
    private class DesignTaskRepository : TasksRepository {
        val loads = mutableListOf<ProjectId>()

        override suspend fun workspace(stored: WorkspaceId?) = ApiResult.Success(checkNotNull(stored))

        override suspend fun load(workspace: WorkspaceId, project: ProjectId): ApiResult<TasksData> {
            loads += project
            check(project == WEBSITE) { "the test opens Website's tasks only, not $project's" }
            return ApiResult.Success(TasksData(listOf(Task(TaskId(1), workspace, project, "Design", active = true))))
        }

        override suspend fun create(workspace: WorkspaceId, project: ProjectId, name: String, known: Set<TaskId>) =
            notSent("create")

        override suspend fun update(
            workspace: WorkspaceId,
            project: ProjectId,
            id: TaskId,
            name: String,
            done: Boolean,
        ) = notSent("update")

        override suspend fun delete(workspace: WorkspaceId, project: ProjectId, id: TaskId) = notSent("delete")
    }

    private companion object {
        val WEBSITE = ProjectId(5)

        fun notSent(call: String): Nothing = throw AssertionError("the test sends no $call")
    }
}
