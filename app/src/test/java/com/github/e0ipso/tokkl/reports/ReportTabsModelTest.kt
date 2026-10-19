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
import java.time.ZoneId
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Detailed and Weekly tabs of the reports model against a MockWebServer that answers as Tokkl
 * does. The account is at UTC+14 with weeks from Wednesday, and the clock says Tuesday 20 October
 * in UTC, already Wednesday 21 October in the account's zone. Requests run when [io] runs them, so
 * a test can look at the state while they wait.
 */
class ReportTabsModelTest {

    private val server = MockWebServer()
    private val searches = CopyOnWriteArrayList<JsonObject>()
    private val weeklies = CopyOnWriteArrayList<JsonObject>()
    private val io = Queued()
    private val settings = MutableStateFlow<AccountSettings?>(AccountSettings(ZONE, DayOfWeek.WEDNESDAY, timeResolutionMinutes = null))
    private lateinit var model: ReportsModel

    /** The detailed search's pages after the first answer 503, as a proxy does while the server is down. */
    @Volatile
    private var laterPagesFail = false

    /** What the weekly endpoint answers. */
    @Volatile
    private var weeklyBody = WEEKLY

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/api/v9/me" -> json("""{"id":1,"default_workspace_id":7}""")
                "/api/v9/workspaces/7" -> json("""{"id":7,"rounding":1,"rounding_minutes":15}""")
                "/reports/api/v3/workspace/7/search/time_entries" -> search(Json.parseToJsonElement(request.body!!.utf8()).jsonObject)
                "/reports/api/v3/workspace/7/weekly/time_entries" -> {
                    weeklies += Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                    json(weeklyBody)
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        ConfiguredServer.readStoredFrom { url }
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        model = ReportsModel(
            settings,
            { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
            CoroutineScope(Dispatchers.Unconfined),
            io,
            Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    /**
     * The Detailed tab asks for 50 rows at a time, newest first, with the workspace's rounding, and
     * shows each entry in the account's zone. The next page loads from the row the server names,
     * once at a time, and nothing more loads after the last.
     */
    @Test
    fun theDetailedTabPagesAsTheServerSays() {
        model.open()
        io.runAll()
        model.selectTab(ReportTab.DETAILED)
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()

        val first = detailed()
        assertEquals(51, first.nextRow)
        val entry = first.entries.single()
        assertEquals(ZoneId.of(ZONE), entry.start.zone)
        assertEquals(LocalDate.parse("2026-10-21"), entry.start.toLocalDate())
        assertEquals(0 to 30, entry.start.hour to entry.start.minute)
        assertEquals(listOf("d10", "Web", "Design"), listOf(entry.description, entry.projectName, entry.taskName))
        val body = searches.last()
        assertEquals(
            mapOf(
                "order_dir" to "DESC",
                "page_size" to "50",
                "first_row_number" to "1",
                "start_date" to "2026-10-21",
                "end_date" to "2026-10-27",
                "rounding" to "1",
                "rounding_minutes" to "15",
            ),
            body.strings("order_dir", "page_size", "first_row_number", "start_date", "end_date", "rounding", "rounding_minutes"),
        )

        model.loadMore()
        assertTrue("the next page loads", detailed().loadingMore)
        model.loadMore()
        io.runAll()

        val second = detailed()
        assertEquals(listOf(10L, 9L), second.entries.map { it.id })
        assertNull("the last page", second.nextRow)
        assertNull("a running entry has no stop", second.entries[1].stop)
        assertEquals("51", searches.last().string("first_row_number"))
        assertEquals("the summary, then two pages", 3, searches.size)
        model.loadMore()
        assertTrue("nothing more to load", io.isEmpty())
    }

    /** A next page that fails shows why, with no rows, and Try again starts the tab over. */
    @Test
    fun aFailedNextPageShowsWhyAndTryAgainLoadsTheFirstPage() {
        model.selectTab(ReportTab.DETAILED)
        model.open()
        io.runAll()
        laterPagesFail = true

        model.loadMore()
        io.runAll()
        assertEquals(ReportFailure.Unreachable, (model.state.value as ReportsState.Failed).failure)

        model.retry()
        io.runAll()
        assertEquals(1, detailed().entries.size)
        model.selectTab(ReportTab.SUMMARY)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is SummaryReport)
        assertEquals("the summary sums the search oldest first", "ASC", searches.last().string("order_dir"))
    }

    /**
     * The Weekly tab shows one week of the account's: any other range turns into its week, and
     * the rows sort as the web app sorts them, merged by project, time without a project last.
     */
    @Test
    fun theWeeklyTabAsksForTheAccountsWeekAndSortsAsTheWebApp() {
        model.open()
        io.runAll()
        model.select(RangePreset.THIS_MONTH)
        io.runAll()
        model.selectTab(ReportTab.WEEKLY)

        val loading = model.state.value as ReportsState.Loading
        assertEquals(RangeSelection(ReportRange(LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-27")), null), loading.selection)
        io.runAll()
        val report = (model.state.value as ReportsState.Loaded).report as WeeklyReport
        assertEquals(
            mapOf("start_date" to "2026-10-21", "end_date" to "2026-10-27", "rounding" to "1", "rounding_minutes" to "15"),
            weeklies.last().strings("start_date", "end_date", "rounding", "rounding_minutes"),
        )
        assertEquals(listOf(5L, 4L, 3L, null), report.rows.map { it.id })
        assertEquals(listOf(900L, 0, 0, 0, 0, 0, 900), report.rows[1].seconds)
        assertEquals("project_hex_color when there is no project_color", "#990099", report.rows[0].color)
        assertEquals(listOf(4500L, 900, 0, 0, 0, 0, 1800), report.dayTotals)
        assertEquals(7200L, report.total)
        assertEquals(LocalDate.parse("2026-10-21"), report.weekStart)

        model.shift(-1)
        io.runAll()
        assertEquals("2026-10-14", weeklies.last().string("start_date"))
        assertEquals(week("2026-10-14"), loadedRange())
        model.select(RangePreset.TODAY)
        io.runAll()
        assertEquals(week("2026-10-21"), loadedRange())
        model.selectCustom(ReportRange(LocalDate.parse("2026-09-03"), LocalDate.parse("2026-09-20")))
        io.runAll()
        assertEquals("the week of the range's first day", week("2026-09-02"), loadedRange())

        model.selectTab(ReportTab.SUMMARY)
        io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is SummaryReport)
        assertEquals("the summary keeps the week", week("2026-09-02"), loadedRange())
    }

    @Test
    fun aWeeklyRowWithoutSevenDaysIsAnUnexpectedAnswer() {
        weeklyBody = """[{"project_id":1,"project_name":"x","seconds":[1,2]}]"""
        model.selectTab(ReportTab.WEEKLY)
        model.open()
        io.runAll()

        assertEquals(ReportFailure.Unexpected(null), (model.state.value as ReportsState.Failed).failure)
    }

    @Test
    fun weeklyRowsWithTheSameTimeSortByTitleIgnoringCase() {
        val rows = listOf(
            WeeklyRow(2, "beta", null, List(7) { 0L }),
            WeeklyRow(1, "Alpha", null, List(7) { 0L }),
            WeeklyRow(3, "alpha", null, List(7) { 0L }),
        )

        assertEquals(listOf("alpha", "Alpha", "beta"), WeeklyReport.of(LocalDate.parse("2026-10-21"), rows).rows.map { it.title })
    }

    /** A range's week is the one its first day falls in, or this week when the range holds today. */
    @Test
    fun aRangesWeekStartsOnTheAccountsFirstDay() {
        val today = LocalDate.parse("2026-10-21")

        assertEquals(week("2026-10-21"), ReportRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")).week(today, DayOfWeek.WEDNESDAY))
        assertEquals(week("2026-09-30"), ReportRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10")).week(today, DayOfWeek.WEDNESDAY))
    }

    /** The detailed search: the summary's search oldest first, and two pages newest first. */
    private fun search(body: JsonObject): MockResponse {
        searches += body
        val first = body.string("first_row_number").toInt()
        return when {
            body.string("order_dir") == "ASC" -> json(rows(row(10, "2026-10-21T00:30:00+14:00", "2026-10-21T01:30:00+14:00")))
            first == 1 -> json(rows(row(10, "2026-10-21T00:30:00+14:00", "2026-10-21T01:30:00+14:00")), NEXT_ROW to "51")
            laterPagesFail -> MockResponse.Builder().code(503).build()
            else -> json(rows(row(9, "2026-10-20T10:00:00+00:00", stop = null)))
        }
    }

    private fun detailed(): DetailedReport = (model.state.value as ReportsState.Loaded).report as DetailedReport

    private fun loadedRange(): ReportRange = (model.state.value as ReportsState.Loaded).selection.range

    private fun week(start: String): ReportRange = LocalDate.parse(start).let { ReportRange(it, it.plusDays(6)) }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.strings(vararg keys: String): Map<String, String> = keys.associateWith { string(it) }

    private fun rows(vararg rows: String) = rows.joinToString(",", "[", "]")

    /** A detailed row with one entry of 15 minutes, as Tokkl sends it. */
    private fun row(id: Int, start: String, stop: String?) =
        """{"project_id":3,"project_name":"Web","project_color":"#06aaf5","task_name":"Design","description":"d$id",""" +
            """"billable":true,"billable_amount_in_cents":null,"currency":"EUR","row_number":$id,""" +
            """"time_entries":[{"id":$id,"seconds":900,"start":"$start","stop":${stop?.let { "\"$it\"" } ?: "null"},"at":"2026-10-20T10:00:00+00:00"}]}"""

    private fun json(body: String, vararg headers: Pair<String, String>) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .apply { for ((name, value) in headers) addHeader(name, value) }
        .body(body)
        .build()

    /** Runs what is dispatched to it only when [runAll] says so. */
    private class Queued : CoroutineDispatcher() {
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
        const val ZONE = "Pacific/Kiritimati"
        const val NEXT_ROW = "X-Next-Row-Number"

        /** Two rows of Beta to merge, ties by time to sort by title, a project_hex_color, and time without a project. */
        const val WEEKLY = """[
            {"project_id":null,"project_name":null,"project_color":null,"seconds":[0,0,0,0,0,0,900]},
            {"project_id":4,"project_name":"Beta","project_color":"#06aaf5","seconds":[900,0,0,0,0,0,0]},
            {"project_id":3,"project_name":"alpha","project_color":"#06aaf5","seconds":[0,900,0,0,0,0,0]},
            {"project_id":5,"project_name":"Big","project_hex_color":"#990099","seconds":[3600,0,0,0,0,0,0]},
            {"project_id":4,"project_name":"Beta","project_color":"#06aaf5","seconds":[0,0,0,0,0,0,900]}
        ]"""
    }
}
