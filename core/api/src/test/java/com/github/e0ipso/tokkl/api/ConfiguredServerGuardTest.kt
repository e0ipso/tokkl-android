package com.github.e0ipso.tokkl.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The shared client sends requests, redirects included, only to the configured server. */
class ConfiguredServerGuardTest {

    private val testApi = TestApi(null)

    @Serializable
    private data class Me(val id: Long)

    private val configured = MockWebServer()
    private val other = MockWebServer()
    private val credentials = ApiCredentials.ApiToken(TOKEN)
    private lateinit var server: ServerUrl
    private lateinit var trust: InMemoryServerTrust
    private lateinit var client: TogglApiClient

    @Before
    fun setUp() {
        configured.start()
        other.start()
        server = ServerUrl.parse(configured.url("/tokkl/").toString())!!
        testApi.stored = server
        // The user has confirmed the MockWebServer's plain http URL.
        trust = InMemoryServerTrust(server)
        testApi.trust = trust
        client = testApi.client(server, credentials)
    }

    @After
    fun tearDown() {
        configured.close()
        other.close()
    }

    @Test
    fun requestToTheConfiguredServerPasses() = runTest {
        configured.enqueue(me())

        assertEquals(ApiResult.Success(Me(7)), run { client.get(server.api("me"), Me.serializer()) })
        assertEquals(1, configured.requestCount)
    }

    @Test
    fun requestToAnotherPortFailsWithTheOriginAndSendsNothing() = runTest {
        val url = other.url("/api/v9/me")

        val result = run { client.get(url, Me.serializer()) }

        assertEquals(ApiResult.Failure(ApiError.HostNotAllowed(origin(url))), result)
        assertEquals(0, other.requestCount)
    }

    @Test
    fun requestToAnotherHostFailsWithTheHostName() = runTest {
        val otherHost = if (configured.hostName == "127.0.0.1") "localhost" else "127.0.0.1"
        val url = server.api("me").newBuilder().host(otherHost).build()

        val error = failure(run { client.get(url, Me.serializer()) })

        assertEquals(ApiError.HostNotAllowed("http://$otherHost:${configured.port}"), error)
        assertEquals(0, configured.requestCount)
    }

    @Test
    fun requestWithAnotherSchemeFails() = runTest {
        val url = server.api("me").newBuilder().scheme("https").port(configured.port).build()

        assertEquals(ApiError.HostNotAllowed(origin(url)), failure(run { client.get(url, Me.serializer()) }))
        assertEquals(0, configured.requestCount)
    }

    @Test
    fun refusalCarriesNoCredentials() {
        val password = ApiCredentials.EmailPassword("ana@example.com", PASSWORD)
        for (credentials in listOf(this.credentials, password)) {
            val request = Request.Builder()
                .url(other.url("/api/v9/me?token=$TOKEN"))
                .header("Authorization", credentials.authorizationHeader)
                .build()

            val refusal = try {
                testApi.http.instance.newCall(request).execute().close()
                fail("the request to another host went through")
                return
            } catch (e: HostNotAllowedException) {
                e
            }

            assertNull(refusal.cause)
            val apiError = failure(
                runBlocking { testApi.client(server, credentials).get(request.url, Me.serializer()) },
            )
            val printed = listOf(refusal.toString(), refusal.stackTraceToString(), refusal.origin, apiError.toString())
            for (text in printed) {
                assertFalse("the refusal holds the Authorization header", credentials.authorizationHeader in text)
                assertFalse("the refusal holds the token", TOKEN in text)
                assertFalse("the refusal holds the password", PASSWORD in text)
                assertFalse("the refusal holds the path or query", "/api/v9/me" in text)
            }
        }
        assertEquals(0, other.requestCount)
    }

    @Test
    fun redirectToAnotherHostIsRefused() = runTest {
        val target = other.url("/api/v9/me")
        configured.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.toString()).build())
        other.enqueue(me())

        val error = failure(run { client.get(server.api("me"), Me.serializer()) })

        assertEquals(ApiError.HostNotAllowed(origin(target)), error)
        assertEquals(1, configured.requestCount)
        assertEquals(0, other.requestCount)
    }

    @Test
    fun redirectToTheSameOriginIsFollowedWithTheCredentials() = runTest {
        configured.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/tokkl/api/v9/me/again").build())
        configured.enqueue(me())

        val result = run { client.get(server.api("me"), Me.serializer()) }

        assertEquals(ApiResult.Success(Me(7)), result)
        configured.takeRequest()
        val followed = configured.takeRequest()
        assertEquals("/tokkl/api/v9/me/again", followed.url.encodedPath)
        assertEquals(credentials.authorizationHeader, followed.headers["Authorization"])
    }

    @Test
    fun withoutAConfiguredServerEveryRequestIsRefused() = runTest {
        testApi.stored = null

        assertEquals(
            ApiError.HostNotAllowed(origin(server.api("me"))),
            failure(run { client.get(server.api("me"), Me.serializer()) }),
        )
        assertEquals(0, configured.requestCount)
    }

    @Test
    fun signInMakesTheUrlBeingTriedTheConfiguredServerUntilItReturns() = runTest {
        testApi.stored = null
        configured.enqueue(me())

        val during = run {
            testApi.configured.signingIn(
                server,
            ) { client.get(server.api("me"), Me.serializer()) }
        }
        val after = run { client.get(server.api("me"), Me.serializer()) }

        assertEquals(ApiResult.Success(Me(7)), during)
        assertEquals(ApiResult.Failure(ApiError.HostNotAllowed(origin(server.api("me")))), after)
        assertEquals(1, configured.requestCount)
    }

    /** The check sits in the shared client, so it holds for every call, not only the sign-in button. */
    @Test
    fun plainHttpServerGetsNoRequestUntilTheUserConfirmsIt() = runTest {
        trust.cleartextConfirmed.clear()
        configured.enqueue(me())

        val refused = run { client.get(server.api("me"), Me.serializer()) }

        assertEquals(ApiResult.Failure(ApiError.CleartextNotConfirmed(server)), refused)
        assertEquals("nothing reached the server", 0, configured.requestCount)

        trust.confirmCleartext(server)
        val sent = run { client.get(server.api("me"), Me.serializer()) }

        assertEquals(ApiResult.Success(Me(7)), sent)
        assertEquals(credentials.authorizationHeader, configured.takeRequest().headers["Authorization"])
    }

    @Test
    fun confirmationCoversOnlyTheUrlTheUserConfirmed() = runTest {
        val elsewhere = ServerUrl.parse(configured.url("/other/").toString())!!
        testApi.stored = elsewhere
        configured.enqueue(me())

        val result = run { testApi.client(elsewhere, credentials).get(elsewhere.api("me"), Me.serializer()) }

        assertEquals(ApiResult.Failure(ApiError.CleartextNotConfirmed(elsewhere)), result)
        assertEquals(0, configured.requestCount)
    }

    @Test
    fun httpsServerNeedsNoConfirmation() = runTest {
        trust.cleartextConfirmed.clear()
        // The MockWebServer speaks plain HTTP, so the TLS handshake fails, but only after the guard let the call
        // through.
        val https = ServerUrl.parse(
            configured.url("/").newBuilder().scheme("https").port(configured.port).build().toString(),
        )!!
        testApi.stored = https

        val error = failure(run { testApi.client(https, credentials).get(https.api("me"), Me.serializer()) })

        assertTrue(error.toString(), error is ApiError.Network)
    }

    private fun origin(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"

    private fun me() = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body("""{"id":7}""")
        .build()

    private fun failure(result: ApiResult<*>): ApiError =
        (result as? ApiResult.Failure)?.error ?: error("expected a failure, got $result")

    private companion object {
        const val TOKEN = "0123456789abcdef0123456789abcdef"
        const val PASSWORD = "correct horse:battery"
    }
}
