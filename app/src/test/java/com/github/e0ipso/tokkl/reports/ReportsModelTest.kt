package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The reports screen's model against a MockWebServer, for an account at UTC+14 whose week starts
 * on Wednesday. The clock reads Tuesday 20 October in UTC, which is already Wednesday the 21st
 * for the account, so a range taken from the phone's day or from Monday weeks would differ.
 *
 * The model's work off the main thread waits in [io] until a test runs it, so a test sees what
 * the screen shows while the answer is on its way.
 *
 * Coming back to the foreground: [ReportsScreen] calls [ReportsModel.wentToBackground] on every
 * stop that is not a configuration change, and [ReportsModel.cameToForeground] on every start,
 * including the one Android replays when the screen starts observing. ReportsForegroundTest
 * checks the rotation on the emulator; these tests check the model's half.
 */
class ReportsModelTest {
    private val server = MockWebServer()
    private val searches = CopyOnWriteArrayList<JsonObject>()
    private val io = QueuedDispatcher()
    private val settings = MutableStateFlow<AccountSettings?>(AccountSettings(ZONE, DayOfWeek.WEDNESDAY, timeResolutionMinutes = null))
    private lateinit var model: ReportsModel

    /** The search gets the 503 of a proxy whose server is down. */
    @Volatile
    private var searchUnreachable = false

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/api/v9/me" -> json("""{"id":1,"default_workspace_id":7}""")
                "/api/v9/workspaces/7" -> json("""{"id":7,"rounding":0,"rounding_minutes":0}""")
                SEARCH -> if (searchUnreachable) {
                    MockResponse.Builder().code(503).build()
                } else {
                    searches += Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                    json("""[{"billable":false,"currency":"EUR","time_entries":[{"seconds":3600,"start":"2026-10-21T00:30:00+14:00"}]}]""")
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        model = ReportsModel(
            accountSettings = settings,
            client = { TogglApiClient(url, ApiCredentials.ApiToken("token")) },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = io,
            clock = CLOCK,
        )
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun opensOnThisWeekInTheAccountsTimezoneAndWeekStart() {
        model.open()
        val thisWeek = RangeSelection(range("2026-10-21", "2026-10-27"), RangePreset.THIS_WEEK)
        assertEquals("no numbers before the answer", ReportsState.Loading(thisWeek), model.state.value)

        io.runAll()

        val loaded = loaded()
        assertEquals(thisWeek, loaded.selection)
        assertEquals(3600L, (loaded.report as SummaryReport).totals.seconds)
        assertSearched("2026-10-21", "2026-10-27")
    }

    @Test
    fun presetsAndShiftsStartFromTheAccountsToday() {
        model.open()
        io.runAll()

        model.select(RangePreset.TODAY)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-21", "2026-10-21"), RangePreset.TODAY), loaded().selection)
        assertSearched("2026-10-21", "2026-10-21")

        model.select(RangePreset.LAST_WEEK)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-14", "2026-10-20"), RangePreset.LAST_WEEK), loaded().selection)

        model.shift(1)
        io.runAll()
        assertEquals("a shifted range names no preset", RangeSelection(range("2026-10-21", "2026-10-27"), null), loaded().selection)

        model.shift(1)
        io.runAll()
        assertEquals(RangeSelection(range("2026-10-28", "2026-11-03"), null), loaded().selection)
        assertSearched("2026-10-28", "2026-11-03")
        assertEquals(5, searches.size)
    }

    @Test
    fun aStartAfterAStopAsksOnceForTheRangeOnScreen() {
        model.open()
        io.runAll()
        model.shift(-1)
        io.runAll()
        val shown = loaded()

        model.wentToBackground()
        assertSame("a stop alone asks nothing", shown, model.state.value)
        model.cameToForeground()

        assertEquals("no old numbers while the answer is on its way", ReportsState.Loading(shown.selection), model.state.value)
        io.runAll()
        assertEquals(shown.selection, loaded().selection)
        assertEquals(3, searches.size)
        assertSearched("2026-10-14", "2026-10-20")

        val reloaded = model.state.value
        model.cameToForeground()
        assertSame("a second start asks nothing", reloaded, model.state.value)
        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(3, searches.size)
    }

    @Test
    fun theStartReplayedWhenTheScreenOpensAsksNothing() {
        model.open()
        io.runAll()
        val shown = loaded()

        model.cameToForeground()

        assertSame(shown, model.state.value)
        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(1, searches.size)
    }

    @Test
    fun aStopWhileTheScreenIsClosedAsksNothing() {
        model.wentToBackground()
        model.open()
        io.runAll()

        model.cameToForeground()

        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(1, searches.size)
    }

    @Test
    fun closingForgetsAStop() {
        model.open()
        io.runAll()
        model.wentToBackground()
        model.close()
        assertEquals(ReportsState.Closed, model.state.value)

        model.cameToForeground()
        assertEquals("a closed screen loads nothing", ReportsState.Closed, model.state.value)

        model.open()
        io.runAll()
        model.cameToForeground()
        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(2, searches.size)
    }

    @Test
    fun aStartWithoutTheServerShowsWhyAndNoNumbers() {
        model.open()
        io.runAll()
        val shown = loaded()
        model.wentToBackground()
        server.close()

        model.cameToForeground()
        io.runAll()

        assertEquals(ReportsState.Failed(shown.selection, ReportFailure.Unreachable), model.state.value)
    }

    @Test
    fun theNetworksReturnAsksAgainForAReportThatGotNoAnswer() {
        searchUnreachable = true
        model.open()
        io.runAll()
        val failed = model.state.value
        assertTrue("no answer: $failed", failed is ReportsState.Failed && failed.failure == ReportFailure.Unreachable)

        searchUnreachable = false
        model.reconnected()
        assertEquals(ReportsState.Loading((failed as ReportsState.Failed).selection), model.state.value)
        io.runAll()

        assertEquals(failed.selection, loaded().selection)
        assertEquals(1, searches.size)
    }

    @Test
    fun theNetworksReturnAsksNothingForAShownOrClosedReport() {
        model.reconnected()
        assertEquals("a closed screen loads nothing", ReportsState.Closed, model.state.value)

        model.open()
        io.runAll()
        val shown = loaded()
        model.reconnected()

        assertSame(shown, model.state.value)
        assertTrue("nothing waits to run", io.isEmpty())
        assertEquals(1, searches.size)
    }

    private fun loaded(): ReportsState.Loaded {
        val state = model.state.value
        assertTrue("loaded: $state", state is ReportsState.Loaded)
        return state as ReportsState.Loaded
    }

    /** The latest search asked for [from] to [to]. */
    private fun assertSearched(from: String, to: String) {
        val body = searches.last()
        assertEquals(from to to, body.getValue("start_date").jsonPrimitive.content to body.getValue("end_date").jsonPrimitive.content)
    }

    private fun range(from: String, to: String) = ReportRange(LocalDate.parse(from), LocalDate.parse(to))

    private fun json(body: String) = MockResponse.Builder().code(200).body(body).addHeader("Content-Type", "application/json").build()

    /** Holds dispatched work until [runAll] runs it on the calling thread. */
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }

        fun isEmpty(): Boolean = queue.isEmpty()
    }

    private companion object {
        /** UTC+14 all year. */
        const val ZONE = "Pacific/Kiritimati"
        const val SEARCH = "/reports/api/v3/workspace/7/search/time_entries"

        /** Tuesday 20 October in UTC, Wednesday the 21st at UTC+14. */
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneOffset.UTC)
    }
}
