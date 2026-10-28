package com.github.e0ipso.tokkl.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The queue sends creates, updates and deletes through [SharedHttpClient.sendingOnce] (TKA-0208),
 * so a request whose connection drops goes out once per try, and the client shares everything
 * else with [SharedHttpClient.instance].
 */
class SharedHttpClientTest {

    private val server = MockWebServer()
    private val credentials = ApiCredentials.ApiToken("tok-123")
    private lateinit var url: ServerUrl
    private lateinit var client: TogglApiClient

    @Before
    fun setUp() {
        server.start()
        url = ServerUrl.parse(server.url("/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        client = TogglApiClient(url, credentials)
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun aQueueRequestGoesOutOnceWhenItsPooledConnectionDropsBeforeTheAnswer() {
        for (method in METHODS) {
            poolAConnection()
            server.enqueue(dropBeforeTheAnswer())
            server.enqueue(answer())

            val result = call(method, resend = false)

            assertTrue("$method: $result", result is ApiResult.Failure && result.error is ApiError.Network)
            assertEquals("one $method on that try", listOf(method), remainingRequests().map { it.method })
            // The answer OkHttp would have used for a second request is still there.
            assertTrue(method, client.get(url.api("me"), JsonObject.serializer()) is ApiResult.Success)
            assertEquals(listOf("GET"), remainingRequests().map { it.method })
        }
    }

    /** The other side of the test above: the shared client still sends such a request again. */
    @Test
    fun otherRequestsGoOutAgainOnANewConnectionWhenAPooledOneDrops() {
        for (method in METHODS) {
            poolAConnection()
            server.enqueue(dropBeforeTheAnswer())
            server.enqueue(answer())

            val result = call(method, resend = true)

            assertTrue("$method: $result", result is ApiResult.Success)
            assertEquals(listOf(method, method), remainingRequests().map { it.method })
        }
    }

    @Test
    fun sendingOnceKeepsThePoolDispatcherInterceptorsAndTrustOfTheSharedClient() {
        val shared = SharedHttpClient.instance
        val once = SharedHttpClient.sendingOnce(shared)

        assertTrue(shared.retryOnConnectionFailure)
        assertFalse(once.retryOnConnectionFailure)
        assertSame(shared.connectionPool, once.connectionPool)
        assertSame(shared.dispatcher, once.dispatcher)
        assertEquals(shared.interceptors, once.interceptors)
        assertEquals(shared.networkInterceptors, once.networkInterceptors)
        // The guard, beside the interceptor that lifts the read timeout for event streams.
        assertEquals(1, once.interceptors.count { it is ConfiguredServerGuard })
        // NoTrustAllTest keeps the socket factory and host name check out of every file but
        // SharedHttpClient.kt, so the test checks the trust manager, which newBuilder copies with them.
        assertSame(shared.x509TrustManager, once.x509TrustManager)
        assertTrue(once.x509TrustManager is PinningTrustManager)
        assertSame(shared.certificatePinner, once.certificatePinner)
        assertFalse(once.followRedirects)
        assertFalse(once.followSslRedirects)
        assertEquals(shared.connectTimeoutMillis, once.connectTimeoutMillis)
        assertEquals(shared.readTimeoutMillis, once.readTimeoutMillis)
        assertEquals(shared.writeTimeoutMillis, once.writeTimeoutMillis)
        assertEquals(shared.callTimeoutMillis, once.callTimeoutMillis)
    }

    @Test
    fun aQueueRequestStillMeetsTheGuardAndTheCleartextCheck() {
        ConfiguredServer.readStoredFrom { ServerUrl.parse("https://elsewhere.example/")!! }
        for (method in METHODS) {
            val refused = call(method, resend = false)
            assertEquals(method, ApiResult.Failure(ApiError.HostNotAllowed(url.api().origin())), refused)
        }

        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        for (method in METHODS) {
            assertEquals(method, ApiResult.Failure(ApiError.CleartextNotConfirmed(url)), call(method, resend = false))
        }
        assertEquals("nothing reached the server", 0, server.requestCount)
    }

    private fun call(method: String, resend: Boolean): ApiResult<*> {
        val target = url.api("workspaces", "7", "time_entries", "55")
        return if (method == "DELETE") {
            client.sendNoContent(method, target, resend = resend)
        } else {
            client.send(method, target, JsonObject(emptyMap()), JsonObject.serializer(), resend = resend)
        }
    }

    /** A read that leaves its connection in OkHttp's pool, so the next request reuses it. */
    private fun poolAConnection() {
        server.enqueue(answer())
        assertTrue(client.get(url.api("me"), JsonObject.serializer()) is ApiResult.Success)
        assertEquals("GET", server.takeRequest().method)
    }

    /** The server reads the request, then the connection drops before the answer. */
    private fun dropBeforeTheAnswer() = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

    private fun answer() = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("{}").build()

    /** Every request the server has received and the test has not taken yet. */
    private fun remainingRequests(): List<RecordedRequest> =
        generateSequence { server.takeRequest(100, TimeUnit.MILLISECONDS) }.toList()

    private companion object {
        val METHODS = listOf("DELETE", "PUT", "POST")
    }
}
