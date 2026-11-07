package com.github.e0ipso.tokkl.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class TogglApiClientTest {

    private val testApi = TestApi(null)

    @Serializable
    private data class Me(val id: Long, val email: String)

    private val server = MockWebServer()
    private val credentials = ApiCredentials.ApiToken("tok-123")
    private lateinit var client: TogglApiClient

    @Before
    fun setUp() {
        server.start()
        val configured = serverUrl()
        // The shared client sends requests only to the configured server.
        testApi.stored = configured
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(configured)
        client = testApi.client(configured, credentials)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getSendsBasicAuthToThePathBearingUrlAndDecodesJson() {
        server.enqueue(json(200, """{"id":42,"email":"ana@example.com","role":"admin","time_resolution_minutes":5}"""))

        val result = runBlocking { client.get(client.server.api("me"), Me.serializer()) }

        assertEquals(ApiResult.Success(Me(42, "ana@example.com")), result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/tokkl/api/v9/me", request.url.encodedPath)
        assertEquals(credentials.authorizationHeader, request.headers["Authorization"])
    }

    @Test
    fun sendPostsJsonToReports() {
        server.enqueue(json(200, """{"id":1,"email":"ana@example.com"}"""))
        val body = buildJsonObject { put("start_date", "2026-10-01") }

        runBlocking {
            client.send(
                "POST",
                client.server.reports("workspace", "1", "search", "time_entries"),
                body,
                Me.serializer(),
            )
        }

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/tokkl/reports/api/v3/workspace/1/search/time_entries", request.url.encodedPath)
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals("""{"start_date":"2026-10-01"}""", request.body?.utf8())
    }

    @Test
    fun status403IsAnAuthFailure() {
        server.enqueue(json(403, "\"Incorrect username and/or password\""))

        val error = failure(runBlocking { client.get(client.server.api("me"), Me.serializer()) })

        assertEquals(ApiError.AuthFailed, error)
        assertEquals(403, (error as ApiError.Http).status)
    }

    @Test
    fun status429CarriesRetryAfterSeconds() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "37").build())

        assertEquals(
            ApiError.RateLimited(37),
            failure(runBlocking { client.get(client.server.api("me"), Me.serializer()) }),
        )
    }

    @Test
    fun status429WithoutUsableRetryAfterHasNoSeconds() {
        server.enqueue(MockResponse.Builder().code(429).build())
        server.enqueue(
            MockResponse.Builder().code(429).addHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT").build(),
        )

        repeat(2) {
            assertEquals(
                ApiError.RateLimited(null),
                failure(runBlocking { client.get(client.server.api("me"), Me.serializer()) }),
            )
        }
    }

    @Test
    fun otherStatusesKeepTheirCode() {
        server.enqueue(json(500, "{}"))

        assertEquals(
            ApiError.UnexpectedStatus(500),
            failure(runBlocking { client.get(client.server.api("me"), Me.serializer()) }),
        )
    }

    @Test
    fun malformedBodyIsAnInvalidResponse() {
        server.enqueue(json(200, """{"id":"not a number"}"""))

        assertTrue(
            failure(runBlocking { client.get(client.server.api("me"), Me.serializer()) }) is ApiError.InvalidResponse,
        )
    }

    @Test
    fun emptyBodyOnADecodingCallIsAnInvalidResponse() {
        server.enqueue(MockResponse.Builder().code(200).build())

        assertTrue(
            failure(runBlocking { client.get(client.server.api("me"), Me.serializer()) }) is ApiError.InvalidResponse,
        )
    }

    @Test
    fun noContentCallSucceedsOn200WithAnEmptyBody() {
        // Tokkl's DELETE routes answer this way.
        server.enqueue(MockResponse.Builder().code(200).build())

        val result = runBlocking {
            client.sendNoContent(
                "DELETE",
                client.server.api("workspaces", "1", "time_entries", "2"),
            )
        }

        assertEquals(ApiResult.Success(Unit), result)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/tokkl/api/v9/workspaces/1/time_entries/2", request.url.encodedPath)
        assertEquals(credentials.authorizationHeader, request.headers["Authorization"])
    }

    @Test
    fun noContentCallSucceedsOn204() {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(ApiResult.Success(Unit), runBlocking { client.sendNoContent("DELETE", client.server.api("me")) })
    }

    @Test
    fun noContentCallKeepsTheHttpErrorMappings() {
        server.enqueue(json(403, "\"Incorrect username and/or password\""))
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "5").build())
        server.enqueue(json(404, "\"Not found\""))
        val url = client.server.api("workspaces", "1", "time_entries", "2")

        assertEquals(ApiError.AuthFailed, failure(runBlocking { client.sendNoContent("DELETE", url) }))
        assertEquals(ApiError.RateLimited(5), failure(runBlocking { client.sendNoContent("DELETE", url) }))
        // The body is a JSON string, so the error carries it as the server's reason.
        assertEquals(
            ApiError.UnexpectedStatus(404, "Not found"),
            failure(runBlocking { client.sendNoContent("DELETE", url) }),
        )
    }

    @Test
    fun noContentCallReportsANetworkFailure() {
        val url = serverUrl()
        server.close()

        val error = failure(runBlocking { testApi.client(url, credentials).sendNoContent("DELETE", url.api("me")) })

        assertTrue(error.toString(), error is ApiError.Network)
    }

    @Test
    fun refusedConnectionIsANetworkError() {
        val url = serverUrl()
        server.close()

        val error = failure(runBlocking { testApi.client(url, credentials).get(url.api("me"), Me.serializer()) })

        assertTrue(error.toString(), error is ApiError.Network)
        assertFalse(error is ApiError.Http)
    }

    @Test
    fun droppedConnectionIsANetworkError() {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())

        val error = failure(runBlocking { client.get(client.server.api("me"), Me.serializer()) })

        assertTrue(error.toString(), error is ApiError.Network)
    }

    @Test
    fun timeoutIsANetworkError() {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
        // Same settings as the shared client, with a read timeout short enough for a test.
        val impatient = testApi.http.instance.newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val url = serverUrl()

        val error = failure(
            runBlocking { testApi.client(url, credentials, impatient).get(url.api("me"), Me.serializer()) },
        )

        assertTrue(error.toString(), error is ApiError.Network && error.cause is SocketTimeoutException)
    }

    @Test
    fun onlyA2xxAnswerReachesTheSuccessListener() {
        var successes = 0
        val counting = testApi.client(serverUrl(), credentials, successListener = { successes++ })
        server.enqueue(json(200, """{"id":1,"email":"ana@example.com"}"""))
        server.enqueue(json(500, "\"down\""))
        server.enqueue(json(403, "\"Incorrect username and/or password\""))
        server.enqueue(MockResponse.Builder().code(204).build())

        runBlocking { counting.get(counting.server.api("me"), Me.serializer()) }
        assertEquals("after a 200", 1, successes)
        runBlocking { counting.get(counting.server.api("me"), Me.serializer()) }
        runBlocking { counting.get(counting.server.api("me"), Me.serializer()) }
        assertEquals("after a 500 and a 403", 1, successes)
        runBlocking { counting.sendNoContent("DELETE", counting.server.api("me")) }
        assertEquals("after a 204", 2, successes)
    }

    private fun serverUrl(): ServerUrl = ServerUrl.parse(server.url("/tokkl/").toString())!!

    private fun json(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun failure(result: ApiResult<*>): ApiError =
        (result as? ApiResult.Failure)?.error ?: error("expected a failure, got $result")
}
