package com.github.e0ipso.tokkl.projects

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.ProjectId
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
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The projects page's model over a MockWebServer whose workspace 7 has one project, Website, with
 * one task, Design, and no defaults, and which answers no change. Unconfined runs each call to the
 * end inside the call.
 */
class ProjectsViewModelTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()

    /** The times the page asked for the projects. */
    private val projectLoads = AtomicInteger()

    /** The project of each request for tasks, in order. */
    private val taskLoads = CopyOnWriteArrayList<ProjectId>()
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private lateinit var model: ProjectsViewModel
    private lateinit var screen: TestScreen
    private val saved = SavedStateHandle()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val project = TASKS.matchEntire(path)?.groupValues?.get(1)
                return when {
                    request.method != "GET" -> MockResponse.Builder().code(404).build()

                    path == "/api/v9/workspaces/7/projects" -> {
                        projectLoads.incrementAndGet()
                        json("""[{"id":5,"workspace_id":7,"name":"Website","active":true}]""")
                    }

                    project != null -> {
                        taskLoads += ProjectId(project.toLong())
                        json("""[{"id":1,"project_id":$project,"name":"Design","active":true}]""")
                    }

                    // No defaults: the workspace's own request fails, which the page bears alone.
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
        model = projectsModel(saved)
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun projectsModel(saved: SavedStateHandle) = ProjectsViewModel(
        repository = ServerProjectsRepository { testApi.api() },
        tasksRepository = ServerTasksRepository { testApi.api() },
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
        assertEquals(listOf(WEBSITE), taskLoads)

        screen.leave()
        screen.show()

        assertEquals(ProjectForm.of(website, projectCount = 1), model.state.value.overlay)
        assertEquals("Design", model.tasks.state.value.data?.tasks?.single()?.name)
        assertEquals(2, projectLoads.get())
        assertEquals(listOf(WEBSITE, WEBSITE), taskLoads)
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
        assertEquals("no task list loads before the page shows", listOf(WEBSITE), taskLoads)
        TestScreen(restored.page, scope, scheduler).show()
        assertEquals(listOf(WEBSITE, WEBSITE), taskLoads)
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

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        val WEBSITE = ProjectId(5)

        /** A project's tasks, with the project's id. */
        val TASKS = Regex("/api/v9/workspaces/7/projects/(\\d+)/tasks")
    }
}
