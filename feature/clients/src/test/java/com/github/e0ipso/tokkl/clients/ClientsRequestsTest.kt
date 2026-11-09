package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.ClientId
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
 * The exact requests the Clients page sends, byte for byte: method, path with query, and body.
 * The MockWebServer's account has default workspace 7, with client 1, Acme.
 */
class ClientsRequestsTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()

    /** Each request's method, target and body, in order. */
    private val requests = CopyOnWriteArrayList<String>()

    private lateinit var repository: ClientsRepository

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.target} ${request.body?.utf8().orEmpty()}".trim()
                // No id or timezone in /me: the workspace lookup reads only the default workspace.
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/me" -> json("""{"default_workspace_id":7}""")
                    "GET /api/v9/workspaces/7/clients" -> json("[$ACME]")
                    "GET /api/v9/workspaces/7/projects" -> json("""[{"id":5,"client_id":1}]""")
                    "POST /api/v9/workspaces/7/clients" -> json("""{"id":2,"wid":7,"name":"Surf school"}""")
                    "PUT /api/v9/workspaces/7/clients/1" -> json(ACME)
                    "POST /api/v9/workspaces/7/clients/1/archive" -> json("[5]")
                    "POST /api/v9/workspaces/7/clients/1/restore" -> json(ACME)
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
        repository = ServerClientsRepository { testApi.api(url) }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun eachCallSendsItsRequest() = runTest {
        assertEquals(ApiResult.Success(WorkspaceId(7)), repository.workspace(stored = null))
        assertTrue(repository.load(WorkspaceId(7)) is ApiResult.Success)
        assertTrue(
            repository.create(
                WorkspaceId(7),
                "Surf school",
                notes = null,
                known = setOf(ClientId(1)),
            ) is ApiResult.Success,
        )
        assertTrue(repository.update(WorkspaceId(7), ClientId(1), "Acme", "Pays late") is ApiResult.Success)
        assertEquals(ApiResult.Success(Unit), repository.archive(WorkspaceId(7), ClientId(1)))
        assertTrue(repository.restore(WorkspaceId(7), ClientId(1)) is ApiResult.Success)

        assertEquals(
            listOf(
                "GET /api/v9/me",
                "GET /api/v9/workspaces/7/clients?status=both",
                "GET /api/v9/workspaces/7/projects?active=both",
                """POST /api/v9/workspaces/7/clients {"name":"Surf school","notes":null}""",
                """PUT /api/v9/workspaces/7/clients/1 {"name":"Acme","notes":"Pays late"}""",
                "POST /api/v9/workspaces/7/clients/1/archive {}",
                """POST /api/v9/workspaces/7/clients/1/restore {"restore_all_projects":true}""",
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
        const val ACME = """{"id":1,"wid":7,"name":"Acme","archived":false}"""
    }
}
