package com.github.e0ipso.tokkl.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    @Serializable
    private data class Me(val id: Long)

    private val configured = MockWebServer()
    private val other = MockWebServer()
    private val credentials = ApiCredentials.ApiToken(TOKEN)
    private lateinit var server: ServerUrl
    private lateinit var client: TogglApiClient

    @Before
    fun setUp() {
        configured.start()
        other.start()
        server = ServerUrl.parse(configured.url("/tokkl/").toString())!!
        ConfiguredServer.readStoredFrom { server }
        client = TogglApiClient(server, credentials)
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        configured.close()
        other.close()
    }

    @Test
    fun requestToTheConfiguredServerPasses() {
        configured.enqueue(me())

        assertEquals(ApiResult.Success(Me(7)), client.get(server.api("me"), Me.serializer()))
        assertEquals(1, configured.requestCount)
    }

    @Test
    fun requestToAnotherPortFailsWithTheOriginAndSendsNothing() {
        val url = other.url("/api/v9/me")

        val result = client.get(url, Me.serializer())

        assertEquals(ApiResult.Failure(ApiError.HostNotAllowed(origin(url))), result)
        assertEquals(0, other.requestCount)
    }

    @Test
    fun requestToAnotherHostFailsWithTheHostName() {
        val otherHost = if (configured.hostName == "127.0.0.1") "localhost" else "127.0.0.1"
        val url = server.api("me").newBuilder().host(otherHost).build()

        val error = failure(client.get(url, Me.serializer()))

        assertEquals(ApiError.HostNotAllowed("http://$otherHost:${configured.port}"), error)
        assertEquals(0, configured.requestCount)
    }

    @Test
    fun requestWithAnotherSchemeFails() {
        val url = server.api("me").newBuilder().scheme("https").port(configured.port).build()

        assertEquals(ApiError.HostNotAllowed(origin(url)), failure(client.get(url, Me.serializer())))
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
                SharedHttpClient.instance.newCall(request).execute().close()
                fail("the request to another host went through")
                return
            } catch (e: HostNotAllowedException) {
                e
            }

            assertNull(refusal.cause)
            val apiError = failure(TogglApiClient(server, credentials).get(request.url, Me.serializer()))
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
    fun redirectToAnotherHostIsRefused() {
        val target = other.url("/api/v9/me")
        configured.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.toString()).build())
        other.enqueue(me())

        val error = failure(client.get(server.api("me"), Me.serializer()))

        assertEquals(ApiError.HostNotAllowed(origin(target)), error)
        assertEquals(1, configured.requestCount)
        assertEquals(0, other.requestCount)
    }

    @Test
    fun redirectToTheSameOriginIsFollowedWithTheCredentials() {
        configured.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/tokkl/api/v9/me/again").build())
        configured.enqueue(me())

        val result = client.get(server.api("me"), Me.serializer())

        assertEquals(ApiResult.Success(Me(7)), result)
        configured.takeRequest()
        val followed = configured.takeRequest()
        assertEquals("/tokkl/api/v9/me/again", followed.url.encodedPath)
        assertEquals(credentials.authorizationHeader, followed.headers["Authorization"])
    }

    @Test
    fun redirectsAfterAPostKeepOkHttpsMethodRules() {
        val body = buildJsonObject { put("name", "x") }
        configured.enqueue(MockResponse.Builder().code(303).addHeader("Location", "/tokkl/seen").build())
        configured.enqueue(me())
        configured.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/tokkl/again").build())
        configured.enqueue(me())

        client.send("POST", server.api("tags"), body, Me.serializer())
        client.send("POST", server.api("tags"), body, Me.serializer())

        val requests = List(4) { configured.takeRequest() }
        assertEquals(listOf("POST", "GET", "POST", "POST"), requests.map { it.method })
        assertEquals(0L, requests[1].bodySize)
        assertEquals("""{"name":"x"}""", requests[3].body?.utf8())
    }

    @Test
    fun withoutAConfiguredServerEveryRequestIsRefused() {
        ConfiguredServer.readStoredFrom { null }

        assertEquals(ApiError.HostNotAllowed(origin(server.api("me"))), failure(client.get(server.api("me"), Me.serializer())))
        assertEquals(0, configured.requestCount)
    }

    @Test
    fun signInMakesTheUrlBeingTriedTheConfiguredServerUntilItReturns() {
        ConfiguredServer.readStoredFrom { null }
        configured.enqueue(me())

        val during = ConfiguredServer.signingIn(server) { client.get(server.api("me"), Me.serializer()) }
        val after = client.get(server.api("me"), Me.serializer())

        assertEquals(ApiResult.Success(Me(7)), during)
        assertEquals(ApiResult.Failure(ApiError.HostNotAllowed(origin(server.api("me")))), after)
        assertEquals(1, configured.requestCount)
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
