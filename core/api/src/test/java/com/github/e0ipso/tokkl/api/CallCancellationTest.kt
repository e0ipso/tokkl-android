package com.github.e0ipso.tokkl.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Cancelling the coroutine that waits for an API call cancels the OkHttp call. */
class CallCancellationTest {

    private val server = MockWebServer()
    private lateinit var testApi: TestApi
    private lateinit var client: TogglApiClient

    @Before
    fun setUp() {
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        testApi = TestApi(url)
        client = testApi.client(io = Dispatchers.IO)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun cancellingTheCallerEndsACallTheServerHasNotAnswered() = runBlocking {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())

        val call = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            client.get(client.server.api("me"), JsonObject.serializer())
        }
        assertEquals("the request reached the server", "GET", server.takeRequest(5, TimeUnit.SECONDS)?.method)
        // Far less than the shared client's 10 second read timeout.
        withTimeout(2_000) { call.cancelAndJoin() }

        assertTrue(call.isCancelled)
        assertEquals("no call left running", 0, testApi.http.instance.dispatcher.runningCallsCount())
    }

    @Test
    fun aNonCancellableCallerStillGetsTheAnswer() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("{}")
                .headersDelay(300, TimeUnit.MILLISECONDS)
                .build(),
        )

        var answer: ApiResult<JsonObject>? = null
        val call = launch(Dispatchers.Default) {
            withContext(NonCancellable) { answer = client.get(client.server.api("me"), JsonObject.serializer()) }
        }
        assertEquals("GET", server.takeRequest(5, TimeUnit.SECONDS)?.method)
        call.cancelAndJoin()

        assertEquals(ApiResult.Success(JsonObject(emptyMap())), answer)
    }
}
