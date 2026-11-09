package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The exact requests the project form's task list sends, byte for byte, and how the list reads
 * the answer. The MockWebServer's project 5 in workspace 7 has the done task brief and the open
 * task Design, in that order, and the answer also holds a task of project 9.
 */
class TasksRequestsTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()

    /** Each request's method, target and body, in order. */
    private val requests = CopyOnWriteArrayList<String>()

    private lateinit var repository: ServerTasksRepository

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.target} ${request.body?.utf8().orEmpty()}".trim()
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET $TASKS" -> json(
                        """[{"id":2,"project_id":5,"name":"brief","active":false},""" +
                            """{"id":4,"project_id":9,"name":"Elsewhere","active":true},""" +
                            """{"id":1,"project_id":5,"name":"Design","active":true}]""",
                    )

                    "POST $TASKS" -> json("""{"id":3,"project_id":5,"name":"Copy","active":true}""")

                    "PUT $TASKS/1" -> json("""{"id":1,"project_id":5,"name":"Wireframes","active":false}""")

                    "DELETE $TASKS/1" -> MockResponse.Builder().code(200).build()

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
        repository = ServerTasksRepository { testApi.api(url) }
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** The list keeps the project's own tasks, open ones before done ones, each by name. */
    @Test
    fun theProjectsOpenTasksComeBeforeItsDoneOnesEachByName() = runTest {
        val loaded = repository.load(WorkspaceId(7), ProjectId(5))

        assertEquals(
            listOf("Design", "brief"),
            (loaded as ApiResult.Success).value.tasks.map { it.name },
        )
    }

    @Test
    fun eachCallSendsItsRequest() = runTest {
        assertTrue(repository.load(WorkspaceId(7), ProjectId(5)) is ApiResult.Success)

        val workspace = WorkspaceId(7)
        val project = ProjectId(5)
        assertTrue(repository.create(workspace, project, "Copy", known = setOf(TaskId(1))) is ApiResult.Success)
        assertTrue(repository.update(workspace, project, TaskId(1), "Wireframes", done = true) is ApiResult.Success)
        assertEquals(ApiResult.Success(Unit), repository.delete(workspace, project, TaskId(1)))

        assertEquals(
            listOf(
                "GET $TASKS",
                """POST $TASKS {"name":"Copy"}""",
                """PUT $TASKS/1 {"name":"Wireframes","active":false}""",
                "DELETE $TASKS/1",
            ),
            requests,
        )
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val TASKS = "/api/v9/workspaces/7/projects/5/tasks"
    }
}
