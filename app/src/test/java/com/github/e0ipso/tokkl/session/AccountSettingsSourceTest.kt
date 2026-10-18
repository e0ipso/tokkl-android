package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.SnappedSpan
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A refresh against a MockWebServer whose `GET /api/v9/me` has no `time_resolution_minutes`, as on Toggl Track. */
class AccountSettingsSourceTest {

    private val server = MockWebServer()
    private val saved = mutableListOf<AccountSettings>()
    private lateinit var client: TogglApiClient

    @Before
    fun setUp() {
        server.start()
        val configured = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        ConfiguredServer.readStoredFrom { configured }
        client = TogglApiClient(configured, ApiCredentials.ApiToken("tok-123"))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        server.close()
    }

    @Test
    fun meWithoutTimeResolutionRefreshesWithNoSnapping() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"id":7,"email":"ana@example.com","timezone":"Europe/Madrid","beginning_of_week":1}""")
                .build(),
        )
        val stored = AccountSettings("UTC", DayOfWeek.SUNDAY, timeResolutionMinutes = 5.0)
        // Unconfined runs the refresh to the end inside refresh().
        val source = AccountSettingsSource(
            initial = stored,
            save = { saved += it },
            client = { client },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        val refresh = source.refresh()

        assertTrue("the refresh finished", refresh.isCompleted)
        assertEquals("/api/v9/me", server.takeRequest().url.encodedPath)
        val expected = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, timeResolutionMinutes = null)
        assertEquals(expected, source.state.value)
        assertEquals(listOf(expected), saved)
        val start = Instant.parse("2026-10-18T10:10:07Z")
        val stop = Instant.parse("2026-10-18T10:52:41Z")
        assertEquals(SnappedSpan(start, stop), source.state.value!!.time()!!.snapUnsent(start, stop))
    }
}
