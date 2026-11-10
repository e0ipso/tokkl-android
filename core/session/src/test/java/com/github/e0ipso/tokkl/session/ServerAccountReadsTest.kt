package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.time.DurationFormat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The account refresh's two reads against a MockWebServer: the requests and how their answers read. */
class ServerAccountReadsTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()
    private lateinit var reads: ServerAccountReads

    @Before
    fun setUp() {
        server.start()
        val configured = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server, whose plain http the user confirmed.
        testApi.stored = configured
        testApi.trust = InMemoryServerTrust(configured)
        reads = ServerAccountReads(testApi.api(configured, ApiCredentials.ApiToken("tok-123")))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun meAsksForTheRelatedDataAndPreferencesForTheirOwnPath() = runTest {
        server.enqueue(json(ME))
        server.enqueue(json("""{"duration_format":"classic"}"""))

        val me = reads.meWithRelatedData()
        val preferences = reads.preferences()

        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals("/api/v9/me", first.url.encodedPath)
        assertEquals("true", first.url.queryParameter("with_related_data"))
        assertEquals("/api/v9/me/preferences", server.takeRequest().url.encodedPath)
        assertEquals("Europe/Madrid", (me as ApiResult.Success).value.timezone)
        assertEquals(DurationFormat.CLASSIC, (preferences as ApiResult.Success).value.durationFormat)
    }

    @Test
    fun aFailedMeAndPreferencesThatAreNotAnObjectFail() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(json("""[]"""))

        val me = reads.meWithRelatedData()
        val preferences = reads.preferences()

        assertEquals(503, ((me as ApiResult.Failure).error as ApiError.UnexpectedStatus).status)
        assertTrue(preferences.toString(), (preferences as ApiResult.Failure).error is ApiError.InvalidResponse)
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val ME = """{"id":7,"email":"ana@example.com","timezone":"Europe/Madrid","beginning_of_week":1}"""
    }
}
