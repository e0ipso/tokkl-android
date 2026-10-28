package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.session.Me
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import java.io.IOException
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The capability flags from `/me`, the trash probe and the timer state probe, against a MockWebServer. */
class CapabilitiesTest {

    private val server = MockWebServer()
    private lateinit var client: TogglApiClient
    private val answers = mutableMapOf<String, MockResponse>()
    private val saved = mutableListOf<Capabilities>()
    private val timerStates = TimerStateSource(initial = null, save = {}, cache = EmptyCache)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                answers[request.url.encodedPath] ?: MockResponse.Builder().code(404).body("\"Not found\"").build()
        }
        server.start()
        val configured = ServerUrl.parse(server.url("/").toString())!!
        ConfiguredServer.readStoredFrom { configured }
        ConfiguredTrust.readFrom(InMemoryServerTrust(configured))
        client = TogglApiClient(configured, ApiCredentials.ApiToken("tok-123"))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun timeResolutionIsFalseWhenMeLacksTimeResolutionMinutes() {
        answers[TRASH] = json("[]")

        val capabilities = source().refresh(client, me(TOGGL_ME))

        assertFalse(capabilities.timeResolution)
    }

    @Test
    fun favoritesAreFalseWhenMeLacksRole() {
        answers[TRASH] = json("[]")

        val capabilities = source().refresh(client, me("""{"id":1,"timezone":"UTC","beginning_of_week":1,"time_resolution_minutes":5}"""))

        assertFalse(capabilities.favorites)
        assertFalse("Toggl Track's Reports API v3 has no exclude fields", capabilities.negatedFilters)
        assertEquals("Toggl Track has no summary CSV route", false, capabilities.summaryCsv)
        assertEquals("Toggl Track has no detailed CSV route", false, capabilities.detailedCsv)
        assertTrue(capabilities.timeResolution)
    }

    @Test
    fun allFlagsAreTrueOnTokkl() {
        answers[TRASH] = json("[]")
        answers[TIMER] = json(TIMER_STATE)

        val capabilities = source().refresh(client, me(TOKKL_ME))

        assertEquals(ALL, capabilities)
        assertEquals("the flags are stored", listOf(ALL), saved)
    }

    @Test
    fun trashAndRestoreAreFalseWhenTheTrashAnswers404() {
        val capabilities = source(initial = ALL).refresh(client, me(TOKKL_ME))

        assertFalse(capabilities.trash)
        assertFalse(capabilities.restore)
        assertEquals(listOf(ALL.copy(trash = false, restore = false, timerState = false)), saved)
    }

    /** Such as a proxy's HTML page in front of a server without the trash. */
    @Test
    fun aTrashAnswerThatIsNotAListHidesTrashAndRestore() {
        answers[TRASH] = MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html></html>").build()

        val capabilities = source(initial = ALL).refresh(client, me(TOKKL_ME))

        assertFalse(capabilities.trash)
        assertFalse(capabilities.restore)
    }

    @Test
    fun aProbeWithoutAClearAnswerKeepsTheTrashFlags() {
        answers[TRASH] = MockResponse.Builder().code(503).build()
        answers[TIMER] = json(TIMER_STATE)

        val capabilities = source(initial = ALL).refresh(client, me(TOKKL_ME))

        assertEquals(ALL, capabilities)
        assertEquals("nothing changed, so nothing was written", emptyList<Capabilities>(), saved)
    }

    /** The probe never makes up an entry id to try the restore endpoint. */
    @Test
    fun theOnlyProbesAreOneGetOfTheTrashAndOneOfTheTimer() {
        answers[TRASH] = json("[]")
        answers[TIMER] = json(TIMER_STATE)

        source().refresh(client, me(TOKKL_ME))

        assertEquals("no second timer read to seed the holder, and no /me", 2, server.requestCount)
        val requests = listOf(server.takeRequest(), server.takeRequest())
        assertEquals(listOf("GET", "GET"), requests.map { it.method })
        assertEquals(listOf(TRASH, TIMER), requests.map { it.url.encodedPath })
    }

    @Test
    fun aFailedWriteKeepsTheNewFlagsAndTheNextRefreshWritesAgain() {
        answers[TRASH] = json("[]")
        answers[TIMER] = json(TIMER_STATE)
        var failWrites = true
        val source = CapabilitiesSource(initial = null) {
            if (failWrites) throw IOException("disk full")
            saved += it
        }

        assertEquals(ALL, source.refresh(client, me(TOKKL_ME)))
        assertEquals(ALL, source.state.value)
        failWrites = false
        source.refresh(client, me(TOKKL_ME))

        assertEquals(listOf(ALL), saved)
    }

    /** The trash page's own 404 hides the trash and restore, and stores that, as the probe would. */
    @Test
    fun aMissingTrashTurnsTrashAndRestoreOffAndStoresThem() {
        val source = source(initial = ALL)

        source.trashMissing()

        val expected = ALL.copy(trash = false, restore = false)
        assertEquals(expected, source.state.value)
        assertEquals(listOf(expected), saved)
    }

    @Test
    fun aMissingTrashWritesNothingWhenTheFlagsAlreadySaySo() {
        val source = source()

        source.trashMissing()

        assertEquals(Capabilities.NONE, source.state.value)
        assertTrue("nothing was written", saved.isEmpty())
    }

    @Test
    fun aTimerStateMeansPresentAndSeedsTheHolderFromTheSameRequest() {
        answers[TRASH] = json("[]")
        answers[TIMER] = json(TIMER_STATE)

        val capabilities = source().refresh(client, me(TOKKL_ME))

        assertTrue(capabilities.timerState)
        assertEquals(3L, timerStates.state.value?.state?.revision)
        assertEquals(1, server.requests().count { it == TIMER })
    }

    @Test
    fun aMissingTimerRouteMeansAbsentAndTurnsAPresentFlagOff() {
        answers[TRASH] = json("[]")

        val capabilities = source(initial = ALL).refresh(client, me(TOKKL_ME))

        assertFalse(capabilities.timerState)
        assertTrue("the other flags stay on", capabilities.trash && capabilities.favorites)
        assertNull(timerStates.state.value)
    }

    /** Such as a proxy's page in front of a server without the timer API. */
    @Test
    fun aSuccessfulAnswerThatIsNotATimerStateMeansAbsent() {
        answers[TRASH] = json("[]")
        val bodies = listOf("""["x"]""", """{"revision":3}""", TIMER_STATE.replace("2026-10-28T08:13:05.120Z", "yesterday"))
        for (body in bodies) {
            answers[TIMER] = json(body)

            assertFalse(body, source(initial = ALL).refresh(client, me(TOKKL_ME)).timerState)
        }
        answers[TIMER] = MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html></html>").build()
        assertFalse(source(initial = ALL).refresh(client, me(TOKKL_ME)).timerState)
        assertNull("none of them seeded the holder", timerStates.state.value)
    }

    @Test
    fun anAnswerWithoutAClearVerdictKeepsTheFlagAndSeedsNothing() {
        answers[TRASH] = json("[]")
        for (status in listOf(403, 429, 500, 503)) {
            answers[TIMER] = MockResponse.Builder().code(status).build()

            assertTrue("status $status", source(initial = ALL).refresh(client, me(TOKKL_ME)).timerState)
            assertFalse("status $status", source().refresh(client, me(TOKKL_ME)).timerState)
        }
        assertNull(timerStates.state.value)
    }

    @Test
    fun anUnreachableServerKeepsAPresentFlagForAnOfflinePhone() {
        val unreachable = ServerUrl.parse("http://127.0.0.1:1/")!!
        ConfiguredTrust.readFrom(InMemoryServerTrust(unreachable))
        val offline = TogglApiClient(unreachable, ApiCredentials.ApiToken("tok-123"))

        assertTrue(source(initial = ALL).refresh(offline, me(TOKKL_ME)).timerState)
    }

    @Test
    fun theFlagIsStoredWithTheOthersAndSurvivesARestart() {
        answers[TRASH] = json("[]")
        answers[TIMER] = json(TIMER_STATE)
        source().refresh(client, me(TOKKL_ME))
        assertTrue(saved.single().timerState)

        // A new process starts from the stored flags and hears nothing clear from the server.
        answers[TIMER] = MockResponse.Builder().code(503).build()
        val restarted = source(initial = saved.single())
        assertTrue(restarted.state.value.timerState)
        assertTrue(restarted.refresh(client, me(TOKKL_ME)).timerState)
    }

    @Test
    fun signOutForgetsTheFlagAndALateRefreshOfTheOldAccountSeedsNothing() {
        answers[TRASH] = json("[]")
        answers[TIMER] = json(TIMER_STATE)
        val capabilities = source(initial = ALL)

        capabilities.forget()
        assertEquals(Capabilities.NONE, capabilities.state.value)
        assertFalse(capabilities.state.value.timerState)

        timerStates.retire()
        capabilities.refresh(client, me(TOKKL_ME))
        assertNull("a retired holder takes no state", timerStates.state.value)
    }

    private fun CapabilitiesSource.refresh(api: TogglApiClient, me: Me) = refresh(api, me, timerStates)

    private fun MockWebServer.requests(): List<String> =
        List(requestCount) { takeRequest().url.encodedPath }

    private fun source(initial: Capabilities? = null) = CapabilitiesSource(initial) { saved += it }

    private fun me(body: String): Me = Json { ignoreUnknownKeys = true }.decodeFromString(Me.serializer(), body)

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val TRASH = "/api/app/trash"
        const val TIMER = "/api/app/timer"
        const val TIMER_STATE =
            """{"revision":3,"server_time":"2026-10-28T08:13:05.120Z","running_entry":null,"pause":null,"pomodoro":null,"discarded_pomodoro":null}"""
        const val TOKKL_ME = """{"id":1,"timezone":"UTC","beginning_of_week":1,"role":"user","time_resolution_minutes":5}"""
        const val TOGGL_ME = """{"id":1,"timezone":"UTC","beginning_of_week":1}"""
        val ALL = Capabilities(
            trash = true,
            restore = true,
            favorites = true,
            timeResolution = true,
            negatedFilters = true,
            summaryCsv = true,
            detailedCsv = true,
            timerState = true,
        )
    }

    /** No running entry in the cache, which is all these timer states carry. */
    private object EmptyCache : CurrentEntryCache {
        override fun runningCheckStart() = RunningCheckStart(emptyList(), null)

        override fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?): RunningCheckWrites? = null
    }
}
