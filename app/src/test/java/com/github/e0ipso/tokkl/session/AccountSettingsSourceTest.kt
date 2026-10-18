package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.SnappedSpan
import java.time.DayOfWeek
import java.time.Instant
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Refreshes against a MockWebServer that answers `GET /api/v9/me`. */
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
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(configured))
        client = TogglApiClient(configured, ApiCredentials.ApiToken("tok-123"))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    /** `GET /api/v9/me` has no `time_resolution_minutes`, as on Toggl Track. */
    @Test
    fun meWithoutTimeResolutionRefreshesWithNoSnapping() {
        server.enqueue(me(MADRID_MONDAY))
        val stored = AccountSettings("UTC", DayOfWeek.SUNDAY, timeResolutionMinutes = 5.0)
        val source = source(initial = stored)

        val refresh = source.refresh()

        assertTrue("the refresh finished", refresh.isCompleted)
        assertEquals("/api/v9/me", server.takeRequest().url.encodedPath)
        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)
        assertEquals(listOf(MADRID_MONDAY_SETTINGS), saved)
        val start = Instant.parse("2026-10-18T10:10:07Z")
        val stop = Instant.parse("2026-10-18T10:52:41Z")
        assertEquals(SnappedSpan(start, stop), source.state.value!!.time()!!.snapUnsent(start, stop))
    }

    @Test
    fun aRefreshWhileOneRunsJoinsItAndSendsNoSecondRequest() {
        server.enqueue(me(MADRID_MONDAY))
        server.enqueue(me(MADRID_MONDAY))
        // Holds each refresh until the test runs it.
        val queued = ArrayDeque<Runnable>()
        val source = source(initial = null, io = Executor { queued += it }.asCoroutineDispatcher())

        val first = source.refresh()
        val second = source.refresh()

        assertSame("the second call gets the running refresh", first, second)
        assertEquals("one refresh waits to run", 1, queued.size)
        queued.removeFirst().run()
        assertTrue("the refresh finished", first.isCompleted)
        assertEquals(1, server.requestCount)
        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)

        val next = source.refresh()

        assertNotSame("a call after the refresh finished starts a new one", first, next)
        queued.removeFirst().run()
        assertTrue("the new refresh finished", next.isCompleted)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun settingsStoredWhileARefreshRunsAreNotReplacedByItsOlderAnswer() {
        val signedIn = AccountSettings("America/Sao_Paulo", DayOfWeek.SUNDAY, timeResolutionMinutes = 5.0)
        lateinit var source: AccountSettingsSource
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Sign-in stores the settings from its own /me after this request left.
                source.store(signedIn)
                return me(MADRID_MONDAY)
            }
        }
        source = source(initial = null)

        val refresh = source.refresh()

        assertTrue("the refresh finished", refresh.isCompleted)
        assertEquals(1, server.requestCount)
        assertEquals(signedIn, source.state.value)
        assertEquals("only sign-in wrote", listOf(signedIn), saved)
    }

    /** Unconfined runs the refresh to the end inside refresh() unless [io] holds it. */
    private fun source(initial: AccountSettings?, io: CoroutineContext = Dispatchers.Unconfined) =
        AccountSettingsSource(
            initial = initial,
            save = { saved += it },
            client = { client },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = io,
        )

    private fun me(json: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(json)
        .build()

    private companion object {
        const val MADRID_MONDAY = """{"id":7,"email":"ana@example.com","timezone":"Europe/Madrid","beginning_of_week":1}"""
        val MADRID_MONDAY_SETTINGS = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, timeResolutionMinutes = null)
    }
}
