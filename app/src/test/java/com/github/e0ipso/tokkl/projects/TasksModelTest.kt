package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.ServerProblem
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The project form's task list against a MockWebServer with project 5 in workspace 7, which has
 * the open task Design and the done task Brief, and project 6, which has none. Unconfined runs
 * each request to the end inside the call.
 */
class TasksModelTest {

    private val server = MockWebServer()

    /** Each request's method, path and body, in order. */
    private val requests = CopyOnWriteArrayList<String>()
    private var refreshes = 0
    private lateinit var model: TasksModel

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}".trim()
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/workspaces/7/projects/5/tasks" ->
                        json("""[{"id":2,"project_id":5,"name":"brief","active":false},{"id":1,"project_id":5,"name":"Design","active":true}]""")
                    "GET /api/v9/workspaces/7/projects/6/tasks" -> json("[]")
                    "POST /api/v9/workspaces/7/projects/5/tasks" -> json("""{"id":3,"project_id":5,"name":"Copy","active":true}""")
                    "PUT /api/v9/workspaces/7/projects/5/tasks/1" -> json("""{"id":1,"project_id":5,"name":"Wireframes","active":false}""")
                    "DELETE /api/v9/workspaces/7/projects/5/tasks/1" -> MockResponse.Builder().code(200).build()
                    "POST /api/v9/workspaces/7/projects/6/tasks" ->
                        MockResponse.Builder().code(400).addHeader("Content-Type", "application/json").body("\"Can not change tasks of archived project\"").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        ConfiguredServer.readStoredFrom { url }
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        model = TasksModel(
            client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
            workspaceId = { 7L },
            refreshCache = { refreshes++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun openTasksComeBeforeDoneOnesEachByName() {
        model.open(5)

        assertEquals(listOf("Design", "brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(listOf("GET /api/v9/workspaces/7/projects/5/tasks"), requests)
    }

    @Test
    fun aNewTaskGoesWithItsNameTrimmedAndRefreshesTheCache() {
        model.open(5)
        val opened = requests.size

        model.create("  Copy ")

        assertEquals("""POST /api/v9/workspaces/7/projects/5/tasks {"name":"Copy"}""", requests[opened])
        assertEquals(CatalogChange.Created("Copy"), model.state.value.done)
        assertEquals(listOf("Copy", "Design", "brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(1, refreshes)
    }

    @Test
    fun theFormRenamesTheTaskAndMarksItDone() {
        model.open(5)
        val design = model.state.value.data!!.tasks.first()
        model.askToEdit(design)
        val asked = requests.size

        model.save(" Wireframes ", done = true)

        assertEquals("""PUT /api/v9/workspaces/7/projects/5/tasks/1 {"name":"Wireframes","active":false}""", requests[asked])
        assertEquals(CatalogChange.Saved("Wireframes"), model.state.value.done)
        assertNull("the form closes", model.state.value.overlay)
        assertEquals("a done task goes after the open ones", listOf("brief", "Wireframes"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(1, refreshes)
    }

    @Test
    fun deletingAsksFirstAndCancellingKeepsTheTask() {
        model.open(5)
        val design = model.state.value.data!!.tasks.first()
        val opened = requests.size

        model.askToDelete(design)
        assertEquals(TaskOverlay.ConfirmDelete(design), model.state.value.overlay)
        model.dismiss()
        assertEquals("cancelling sends nothing", opened, requests.size)
        assertEquals(2, model.state.value.data!!.tasks.size)

        model.askToDelete(design)
        model.delete()
        assertEquals("DELETE /api/v9/workspaces/7/projects/5/tasks/1", requests[opened])
        assertEquals(CatalogChange.Deleted("Design"), model.state.value.done)
        assertEquals(listOf("brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(1, refreshes)
    }

    @Test
    fun anotherProjectsFormLoadsItsOwnTasks() {
        model.open(5)
        model.open(5)
        model.open(6)

        assertEquals(emptyList<ProjectTask>(), model.state.value.data!!.tasks)
        assertEquals(
            listOf("GET /api/v9/workspaces/7/projects/5/tasks", "GET /api/v9/workspaces/7/projects/6/tasks"),
            requests,
        )
    }

    @Test
    fun aRefusalKeepsTheListWithTheServersReason() {
        model.open(6)

        model.create("Copy")

        assertEquals(ServerProblem.Refused("Can not change tasks of archived project"), model.state.value.changeProblem)
        assertEquals(0, refreshes)
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
