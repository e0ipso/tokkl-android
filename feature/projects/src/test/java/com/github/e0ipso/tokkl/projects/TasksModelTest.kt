package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.accountSettingsIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.coroutines.CoroutineContext

/**
 * The project form's task list over a MockWebServer where project 5 in workspace 7 has the open
 * task Design and the done task Brief, and project 6, which is archived, has none and refuses
 * every change. Unconfined runs each call to the end inside the call.
 */
class TasksModelTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()

    /** Each request's method, path and body, in order. */
    private val calls = CopyOnWriteArrayList<String>()
    private val refresh = CountingRefresh()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val model = tasksModel(changesIo = Dispatchers.Unconfined)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val call = "${request.method} ${request.url.encodedPath}"
                val body = request.body?.utf8().orEmpty()
                calls += "$call $body".trim()
                return when (call) {
                    "GET $WEBSITE_TASKS" -> json(
                        """[{"id":1,"project_id":5,"name":"Design","active":true},""" +
                            """{"id":2,"project_id":5,"name":"brief","active":false}]""",
                    )

                    "GET $ARCHIVED_TASKS" -> json("[]")

                    // The task as the list sent it.
                    "POST $WEBSITE_TASKS" -> json("""{"id":3,"project_id":5,""" + body.removePrefix("{"))

                    "PUT $WEBSITE_TASKS/1" -> json("""{"id":1,"project_id":5,""" + body.removePrefix("{"))

                    "DELETE $WEBSITE_TASKS/1" -> MockResponse.Builder().code(200).build()

                    "POST $ARCHIVED_TASKS" -> MockResponse.Builder().code(400).body("\"$REFUSAL\"").build()

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
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** The task list, whose changes go on [changesIo]. */
    private fun tasksModel(changesIo: CoroutineContext) = TasksModel(
        repository = ServerTasksRepository { testApi.api() },
        settings = accountSettingsIn(WorkspaceId(7)),
        changes = ServerChanges(scope, changesIo, refresh),
        scope = scope,
        io = Dispatchers.Unconfined,
    )

    @Test
    fun openingAProjectsFormLoadsItsTasksOnce() {
        model.open(WEBSITE)

        assertEquals(listOf("Design", "brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(listOf("GET $WEBSITE_TASKS"), calls)
    }

    @Test
    fun aNewTaskGoesWithItsNameTrimmedAndRefreshesTheCache() {
        model.open(WEBSITE)
        val opened = calls.size

        model.create("  Copy ")

        assertEquals("""POST $WEBSITE_TASKS {"name":"Copy"}""", calls[opened])
        assertEquals(CatalogChange.Created("Copy"), model.state.value.done)
        assertEquals(listOf("Copy", "Design", "brief"), model.state.value.data!!.tasks.map { it.name })
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun theFormRenamesTheTaskAndMarksItDone() {
        model.open(WEBSITE)
        val design = model.state.value.data!!.tasks.first()
        model.askToEdit(design)
        val asked = calls.size

        model.save(" Wireframes ", done = true)

        assertEquals("""PUT $WEBSITE_TASKS/1 {"name":"Wireframes","active":false}""", calls[asked])
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
        val opened = calls.size

        model.askToDelete(design)
        assertEquals(TaskOverlay.ConfirmDelete(design), model.state.value.overlay)
        model.dismiss()
        assertEquals("cancelling sends nothing", opened, calls.size)
        assertEquals(2, model.state.value.data!!.tasks.size)

        model.askToDelete(design)
        model.delete()
        assertEquals("DELETE $WEBSITE_TASKS/1", calls[opened])
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
        assertEquals(listOf("GET $WEBSITE_TASKS", "GET $ARCHIVED_TASKS"), calls)
    }

    @Test
    fun aLateAnswerForTheProjectBeforeLeavesTheNextProjectsListAlone() {
        // The create waits to go until the test lets it, after the next project's list loaded.
        val held = StandardTestDispatcher()
        val model = tasksModel(changesIo = held)
        model.open(WEBSITE)
        model.create("Copy")
        model.close()
        model.open(ARCHIVED)
        val shown = model.state.value

        held.scheduler.advanceUntilIdle()

        assertEquals("""POST $WEBSITE_TASKS {"name":"Copy"}""", calls.last())
        assertEquals("project 6's list stays as it loaded", shown, model.state.value)
        assertEquals("the accepted change still refreshes the cache", 1, refresh.afterChanges)
    }

    @Test
    fun aRefusalKeepsTheListWithTheServersReason() {
        model.open(ARCHIVED)

        model.create("Copy")

        assertEquals(ServerProblem.Refused("Can not change tasks of archived project"), model.state.value.changeProblem)
        assertEquals(0, refresh.afterChanges)
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        val WEBSITE = ProjectId(5)
        val ARCHIVED = ProjectId(6)
        const val WEBSITE_TASKS = "/api/v9/workspaces/7/projects/5/tasks"
        const val ARCHIVED_TASKS = "/api/v9/workspaces/7/projects/6/tasks"
        const val REFUSAL = "Can not change tasks of archived project"
    }
}
