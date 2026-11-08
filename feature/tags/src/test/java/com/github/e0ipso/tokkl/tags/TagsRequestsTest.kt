package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
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
 * The exact requests the Tags page sends, byte for byte: method, path with query, and body. The
 * MockWebServer's account has default workspace 7 and the tag Work, 3.
 */
class TagsRequestsTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()

    /** Each request's method, target and body, in order. */
    private val requests = CopyOnWriteArrayList<String>()

    private lateinit var repository: ServerTagsRepository

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.target} ${request.body?.utf8().orEmpty()}".trim()
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/me" -> json("""{"default_workspace_id":7}""")
                    "GET /api/v9/workspaces/7/tags" -> json("""[{"id":3,"workspace_id":7,"name":"Work"}]""")
                    "POST /api/v9/workspaces/7/tags" -> json("""{"id":4,"workspace_id":7,"name":"Deep work"}""")
                    "PUT /api/v9/workspaces/7/tags/3" -> json("""{"id":3,"workspace_id":7,"name":"Focus"}""")
                    "DELETE /api/v9/workspaces/7/tags/3" -> MockResponse.Builder().code(200).build()
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
        repository = ServerTagsRepository { testApi.client(url) }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun eachCallSendsItsRequest() = runTest {
        assertEquals(ApiResult.Success(7L), repository.workspace(stored = null))
        assertTrue(repository.load(7) is ApiResult.Success)
        assertTrue(repository.create(7, "Deep work", known = setOf(3)) is ApiResult.Success)
        assertTrue(repository.rename(7, 3, "Focus") is ApiResult.Success)
        assertEquals(ApiResult.Success(Unit), repository.delete(7, 3))

        assertEquals(
            listOf(
                "GET /api/v9/me",
                "GET /api/v9/workspaces/7/tags",
                """POST /api/v9/workspaces/7/tags {"name":"Deep work"}""",
                """PUT /api/v9/workspaces/7/tags/3 {"name":"Focus"}""",
                "DELETE /api/v9/workspaces/7/tags/3",
            ),
            requests,
        )
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
