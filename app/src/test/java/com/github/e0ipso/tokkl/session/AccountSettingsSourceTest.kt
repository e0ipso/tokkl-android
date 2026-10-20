package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.SnappedSpan
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Refreshes against a MockWebServer that answers `GET /api/v9/me` and `GET /api/v9/me/preferences`. */
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
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json(IMPROVED))
        val stored = AccountSettings("UTC", DayOfWeek.SUNDAY, timeResolutionMinutes = 5.0)
        val source = source(initial = stored)

        val refresh = source.refresh()

        assertTrue("the refresh finished", refresh.isCompleted)
        assertEquals("/api/v9/me", server.takeRequest().url.encodedPath)
        assertEquals("/api/v9/me/preferences", server.takeRequest().url.encodedPath)
        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)
        assertEquals(listOf(MADRID_MONDAY_SETTINGS), saved)
        val start = Instant.parse("2026-10-18T10:10:07Z")
        val stop = Instant.parse("2026-10-18T10:52:41Z")
        assertEquals(SnappedSpan(start, stop), source.state.value!!.time()!!.snapUnsent(start, stop))
    }

    @Test
    fun aRefreshWhileOneRunsJoinsItAndSendsNoSecondRequest() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json(IMPROVED))
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json(IMPROVED))
        // Holds each refresh until the test runs it.
        val queued = ArrayDeque<Runnable>()
        val source = source(initial = null, io = Executor { queued += it }.asCoroutineDispatcher())

        val first = source.refresh()
        val second = source.refresh()

        assertSame("the second call gets the running refresh", first, second)
        assertEquals("one refresh waits to run", 1, queued.size)
        queued.removeFirst().run()
        assertTrue("the refresh finished", first.isCompleted)
        assertEquals("one /me and one /me/preferences", 2, server.requestCount)
        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)

        val next = source.refresh()

        assertNotSame("a call after the refresh finished starts a new one", first, next)
        queued.removeFirst().run()
        assertTrue("the new refresh finished", next.isCompleted)
        assertEquals(4, server.requestCount)
    }

    /** The app-open refresh's `/me` left offline, and the network returns before it fails. */
    @Test
    fun aReconnectDuringARefreshThatFailsRefreshesOnceMore() {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json(IMPROVED))
        val queued = ArrayDeque<Runnable>()
        val source = source(initial = null, io = Executor { queued += it }.asCoroutineDispatcher())

        val opening = source.refresh()
        val reconnect = source.refreshOnReconnect()
        queued.removeFirst().run()

        assertTrue("the opening refresh failed and finished", opening.isCompleted)
        assertNull(source.state.value)
        assertEquals(1, server.requestCount)
        while (queued.isNotEmpty()) queued.removeFirst().run()
        assertTrue(reconnect.isCompleted)
        assertEquals("the failed /me, then one more /me and /me/preferences", 3, server.requestCount)
        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)
    }

    /** The network returns while the app-open refresh's `/me` is out, and it then answers. */
    @Test
    fun aReconnectDuringARefreshThatAnswersSendsNothingMore() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json(IMPROVED))
        val queued = ArrayDeque<Runnable>()
        val source = source(initial = null, io = Executor { queued += it }.asCoroutineDispatcher())

        source.refresh()
        val reconnect = source.refreshOnReconnect()
        while (queued.isNotEmpty()) queued.removeFirst().run()

        assertTrue(reconnect.isCompleted)
        assertEquals("one /me and one /me/preferences", 2, server.requestCount)
        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)
    }

    @Test
    fun aReconnectWithNoRefreshRunningRefreshesOnce() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json(IMPROVED))
        val source = source(initial = null)

        val reconnect = source.refreshOnReconnect()

        assertTrue(reconnect.isCompleted)
        assertEquals(2, server.requestCount)
        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)
    }

    @Test
    fun settingsStoredWhileARefreshRunsAreNotReplacedByItsOlderAnswer() {
        val signedIn = AccountSettings("America/Sao_Paulo", DayOfWeek.SUNDAY, timeResolutionMinutes = 5.0)
        lateinit var source: AccountSettingsSource
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath == "/api/v9/me/preferences") return json(CLASSIC)
                // Sign-in stores the settings from its own /me after this request left.
                source.store(signedIn)
                return json(MADRID_MONDAY)
            }
        }
        source = source(initial = null)

        val refresh = source.refresh()

        assertTrue("the refresh finished", refresh.isCompleted)
        assertEquals(2, server.requestCount)
        assertEquals(signedIn, source.state.value)
        assertEquals("only sign-in wrote", listOf(signedIn), saved)
    }

    /** Sign-out forgets the settings while a refresh's request is out. */
    @Test
    fun aRefreshThatAnswersAfterForgetStoresNothingAndRefreshesNothingElse() {
        var onRefreshRuns = 0
        lateinit var source: AccountSettingsSource
        lateinit var forgetting: Job
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath == "/api/v9/me/preferences") return json(IMPROVED)
                forgetting = CoroutineScope(Dispatchers.Unconfined).launch { source.forget() }
                return json(MADRID_MONDAY)
            }
        }
        source = AccountSettingsSource(
            initial = MADRID_MONDAY_SETTINGS,
            save = { saved += it },
            client = { client },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
            onRefresh = { _, _, _ -> onRefreshRuns++ },
        )

        val refresh = source.refresh()

        assertTrue("the refresh finished", refresh.isCompleted)
        assertTrue("forget waited for it and returned", forgetting.isCompleted)
        assertNull(source.state.value)
        assertEquals("nothing written", emptyList<AccountSettings>(), saved)
        assertEquals("no cache or capabilities refresh", 0, onRefreshRuns)
    }

    @Test
    fun theDurationFormatComesFromThePreferences() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json(CLASSIC))
        val source = source(initial = null)

        source.refresh()

        val classic = MADRID_MONDAY_SETTINGS.copy(durationFormat = DurationFormat.CLASSIC)
        assertEquals(classic, source.state.value)
        assertEquals(listOf(classic), saved)
        assertEquals("5:06 min", source.state.value!!.formatDuration(306))
    }

    /** As in the web app, which writes every one of these as improved. */
    @Test
    fun preferencesWithoutClassicOrDecimalReadAsImproved() {
        val bodies = listOf(
            """{}""",
            """{"duration_format":null}""",
            """{"duration_format":"foo"}""",
            """{"duration_format":5}""",
            """{"favorites":[]}""",
        )
        for (body in bodies) {
            server.enqueue(json(MADRID_MONDAY))
            server.enqueue(json(body))
            val source = source(initial = MADRID_MONDAY_SETTINGS.copy(durationFormat = DurationFormat.DECIMAL))

            source.refresh()

            assertEquals(body, MADRID_MONDAY_SETTINGS, source.state.value)
            assertEquals(body, "0:05:06", source.state.value!!.formatDuration(306))
        }
    }

    @Test
    fun failedPreferencesKeepTheStoredDurationFormat() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(MockResponse.Builder().code(500).build())
        val stored = AccountSettings("UTC", DayOfWeek.SUNDAY, timeResolutionMinutes = null, DurationFormat.DECIMAL)
        val source = source(initial = stored)

        source.refresh()

        val madridDecimal = MADRID_MONDAY_SETTINGS.copy(durationFormat = DurationFormat.DECIMAL)
        assertEquals("/me still updates the rest", madridDecimal, source.state.value)
        assertEquals(listOf(madridDecimal), saved)
        assertEquals("0.09 h", source.state.value!!.formatDuration(306))
    }

    @Test
    fun failedPreferencesBeforeAnyAreStoredReadAsImproved() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(json("""[]"""))
        val source = source(initial = null)

        source.refresh()

        assertEquals(MADRID_MONDAY_SETTINGS, source.state.value)
    }

    @Test
    fun aRefreshStoresTheDefaultWorkspace() {
        server.enqueue(json(MADRID_MONDAY_IN_42))
        server.enqueue(json(IMPROVED))
        val source = source(initial = null)

        source.refresh()

        assertEquals(42L, source.state.value!!.defaultWorkspaceId)
        assertEquals(listOf(42L), saved.map { it.defaultWorkspaceId })
    }

    /** A `/me` that leaves the field out, or sends it as null, says nothing about the workspace. */
    @Test
    fun aMeWithoutTheDefaultWorkspaceKeepsTheStoredOne() {
        val stored = AccountSettings("UTC", DayOfWeek.SUNDAY, timeResolutionMinutes = null, defaultWorkspaceId = 42)
        for (me in listOf(MADRID_MONDAY, MADRID_MONDAY.replace("}", ""","default_workspace_id":null}"""))) {
            saved.clear()
            server.enqueue(json(me))
            server.enqueue(json(IMPROVED))
            val source = source(initial = stored)

            source.refresh()

            val expected = MADRID_MONDAY_SETTINGS.copy(defaultWorkspaceId = 42)
            assertEquals(me, expected, source.state.value)
            assertEquals(me, listOf(expected), saved)
        }
    }

    @Test
    fun aNewDefaultWorkspaceReplacesTheStoredOne() {
        server.enqueue(json(MADRID_MONDAY_IN_42.replace("42", "43")))
        server.enqueue(json(IMPROVED))
        val source = source(initial = MADRID_MONDAY_SETTINGS.copy(defaultWorkspaceId = 42))

        source.refresh()

        assertEquals(43L, source.state.value!!.defaultWorkspaceId)
    }

    @Test
    fun aFailedMeKeepsTheStoredDefaultWorkspace() {
        server.enqueue(MockResponse.Builder().code(503).build())
        val stored = MADRID_MONDAY_SETTINGS.copy(defaultWorkspaceId = 42)
        val source = source(initial = stored)

        source.refresh()

        assertEquals(stored, source.state.value)
        assertTrue("nothing written", saved.isEmpty())
    }

    /** As the web app's `/a/i` test of `timeofday_format`. */
    @Test
    fun theClockFormatComesFromThePreferences() {
        val cases = listOf(
            """{"timeofday_format":"h:mm A"}""" to true,
            """{"timeofday_format":"h:mm a"}""" to true,
            """{"timeofday_format":"H:mm"}""" to false,
            """{"timeofday_format":null}""" to false,
            """{"timeofday_format":12}""" to false,
            """{}""" to false,
        )
        for ((preferences, twelveHour) in cases) {
            server.enqueue(json(MADRID_MONDAY))
            server.enqueue(json(preferences))
            val source = source(initial = MADRID_MONDAY_SETTINGS.copy(twelveHour = !twelveHour))

            source.refresh()

            assertEquals(preferences, twelveHour, source.state.value!!.twelveHour)
            val noon = Instant.parse("2026-10-19T10:00:00Z")
            assertEquals(preferences, if (twelveHour) "12:00 PM" else "12:00", source.state.value!!.formatTime(noon, ZoneId.of("Europe/Madrid")))
        }
    }

    /** As the web app's `prefs.collapseTimeEntries ?? true`, with JavaScript's truthiness. */
    @Test
    fun theGroupingOfSimilarEntriesComesFromThePreferences() {
        val cases = listOf(
            """{"collapseTimeEntries":true}""" to true,
            """{"collapseTimeEntries":false}""" to false,
            """{"collapseTimeEntries":null}""" to true,
            """{}""" to true,
            """{"collapseTimeEntries":0}""" to false,
            """{"collapseTimeEntries":1}""" to true,
            """{"collapseTimeEntries":""}""" to false,
            """{"collapseTimeEntries":"false"}""" to true,
            """{"collapseTimeEntries":[]}""" to true,
        )
        for ((preferences, collapse) in cases) {
            server.enqueue(json(MADRID_MONDAY))
            server.enqueue(json(preferences))
            val source = source(initial = MADRID_MONDAY_SETTINGS.copy(collapseSimilar = !collapse))

            source.refresh()

            assertEquals(preferences, collapse, source.state.value!!.collapseSimilar)
        }
    }

    @Test
    fun failedPreferencesKeepTheStoredGrouping() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(MockResponse.Builder().code(500).build())
        val source = source(initial = MADRID_MONDAY_SETTINGS.copy(collapseSimilar = false))

        source.refresh()

        assertEquals(false, source.state.value!!.collapseSimilar)
    }

    @Test
    fun failedPreferencesKeepTheStoredClockFormat() {
        server.enqueue(json(MADRID_MONDAY))
        server.enqueue(MockResponse.Builder().code(500).build())
        val source = source(initial = AccountSettings("UTC", DayOfWeek.SUNDAY, timeResolutionMinutes = null, twelveHour = true))

        source.refresh()

        assertEquals(MADRID_MONDAY_SETTINGS.copy(twelveHour = true), source.state.value)
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

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val MADRID_MONDAY = """{"id":7,"email":"ana@example.com","timezone":"Europe/Madrid","beginning_of_week":1}"""
        const val MADRID_MONDAY_IN_42 =
            """{"id":7,"email":"ana@example.com","timezone":"Europe/Madrid","beginning_of_week":1,"default_workspace_id":42}"""
        val MADRID_MONDAY_SETTINGS = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, timeResolutionMinutes = null)
        const val IMPROVED = """{"duration_format":"improved"}"""
        const val CLASSIC = """{"duration_format":"classic"}"""
    }
}
