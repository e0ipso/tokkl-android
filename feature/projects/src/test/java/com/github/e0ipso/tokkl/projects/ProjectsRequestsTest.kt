package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.ProjectId
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
 * The exact requests the Projects page sends, byte for byte: method, path with query, and body.
 * The MockWebServer's workspace 7 has project 5, Website.
 */
class ProjectsRequestsTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()

    /** Each request's method, target and body, in order. */
    private val requests = CopyOnWriteArrayList<String>()

    private lateinit var repository: ProjectsRepository

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.target} ${request.body?.utf8().orEmpty()}".trim()
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/workspaces/7/projects" -> json("[$WEBSITE]")
                    "GET /api/v9/workspaces/7" -> json("""{"default_currency":"EUR","default_hourly_rate":80.5}""")
                    "POST /api/v9/workspaces/7/projects" -> json("""{"id":6,"workspace_id":7,"name":"Shop"}""")
                    "PUT /api/v9/workspaces/7/projects/5" -> json(WEBSITE)
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
        repository = ServerProjectsRepository { testApi.api(url) }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun eachCallSendsItsRequest() = runTest {
        assertTrue(repository.load(WorkspaceId(7)) is ApiResult.Success)
        val shop = ProjectFields("Shop", "#2da608", null, false, null, null, null)
        assertTrue(repository.create(WorkspaceId(7), shop, known = setOf(ProjectId(5))) is ApiResult.Success)
        val website = ProjectFields("Website", "#0b83d9", ClientId(9), true, 12.5, "USD", 40.0)
        assertTrue(repository.update(WorkspaceId(7), ProjectId(5), website) is ApiResult.Success)
        assertTrue(repository.setActive(WorkspaceId(7), ProjectId(5), active = false) is ApiResult.Success)

        assertEquals(
            listOf(
                "GET /api/v9/workspaces/7/projects",
                "GET /api/v9/workspaces/7",
                """POST /api/v9/workspaces/7/projects {"name":"Shop","color":"#2da608","client_id":0,""" +
                    """"billable":false,"rate":null,"currency":null,"estimated_hours":null}""",
                """PUT /api/v9/workspaces/7/projects/5 {"name":"Website","color":"#0b83d9","client_id":9,""" +
                    """"billable":true,"rate":12.5,"currency":"USD","estimated_hours":40.0}""",
                """PUT /api/v9/workspaces/7/projects/5 {"active":false}""",
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
        const val WEBSITE = """{"id":5,"workspace_id":7,"name":"Website","active":true}"""
    }
}
