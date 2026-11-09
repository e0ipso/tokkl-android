package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.CountingRefresh
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
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The projects page's model against a MockWebServer with one project, Website, in workspace 7,
 * and its one task. Unconfined runs each request to the end inside the call.
 */
class ProjectsViewModelTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<String>()
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private lateinit var model: ProjectsViewModel
    private lateinit var screen: TestScreen

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath}"
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/workspaces/7/projects" -> json("[$WEBSITE]")
                    "GET /api/v9/workspaces/7" -> json("""{"default_currency":"EUR","default_hourly_rate":80.5}""")
                    "GET $TASKS" -> json("""[{"id":1,"project_id":5,"name":"Design","active":true}]""")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        testApi.stored = url
        testApi.trust = InMemoryServerTrust(url)
        model = ProjectsViewModel(
            repository = ProjectsRepository { testApi.api(url) },
            tasksRepository = TasksRepository { testApi.api(url) },
            settings = accountSettingsIn(WorkspaceId(7)),
            refreshes = CountingRefresh(),
            scope = scope,
            io = Dispatchers.Unconfined,
            cachedProjects = flowOf(emptyList()),
            cachedClients = flowOf(emptyList()),
            offline = MutableStateFlow(false),
            app = TestApp(scheduler).app,
        )
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Another activity hides the page for longer than its state is shared. The open project form
     * stays with its tasks, and the page loads the projects and the tasks again when it shows.
     */
    @Test
    fun theOpenFormAndItsTasksSurviveTheScreenHiddenForLonger() {
        screen.show()
        val website = model.state.value.data!!.projects.single()
        model.edit(website)
        assertEquals(1, requests.count { it == "GET $TASKS" })

        screen.leave()
        screen.show()

        assertEquals(ProjectForm.Edit(website), model.state.value.overlay)
        assertEquals("Design", model.tasks.state.value.data?.tasks?.single()?.name)
        assertEquals(2, requests.count { it == "GET /api/v9/workspaces/7/projects" })
        assertEquals(2, requests.count { it == "GET $TASKS" })
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val TASKS = "/api/v9/workspaces/7/projects/5/tasks"
        const val WEBSITE = """{"id":5,"workspace_id":7,"name":"Website","active":true}"""
    }
}
