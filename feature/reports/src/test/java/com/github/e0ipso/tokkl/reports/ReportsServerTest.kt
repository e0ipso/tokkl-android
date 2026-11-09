package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Reports API v3 requests behind the summary and the decoding of Tokkl's detailed rows,
 * against a MockWebServer: the workspace and its rounding, the search body, paging by
 * `X-Next-Row-Number`, and each entry's day in the account's timezone.
 */
class ReportsServerTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()
    private lateinit var reports: ServerReportsRepository

    @Before
    fun setUp() {
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        testApi.stored = url
        testApi.trust = InMemoryServerTrust(url)
        val api = testApi.api(url, ApiCredentials.ApiToken("token"))
        reports = ServerReportsRepository { api }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun theWorkspaceIsTheDefaultOneWithItsRounding() = runTest {
        server.enqueue(json("""{"id":1,"default_workspace_id":7,"timezone":"UTC","beginning_of_week":1}"""))
        server.enqueue(json("""{"id":7,"rounding":1,"rounding_minutes":15,"default_currency":"EUR"}"""))

        assertEquals(ApiResult.Success(ReportWorkspace(WorkspaceId(7), 1, 15)), run { reports.workspace() })
        assertEquals("/api/v9/me", server.takeRequest().url.encodedPath)
        assertEquals("/api/v9/workspaces/7", server.takeRequest().url.encodedPath)
    }

    @Test
    fun theSearchAsksForTheRangeWithTheWorkspacesRoundingAndFollowsTheNextRow() = runTest {
        server.enqueue(json("[" + row(1, "2026-10-17T10:30:00+00:00", "1250") + "]", NEXT_ROW to "2"))
        server.enqueue(json("[" + row(2, "2026-10-18T09:59:59+00:00", "null") + "]"))
        val range = ReportRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18"))

        val workspace = ReportWorkspace(WorkspaceId(7), -1, 30)
        val answer = run { reports.entries(workspace, range, ZoneId.of("Pacific/Kiritimati"), ReportFilters()) }

        val entries = (answer as ApiResult.Success).value
        assertEquals(2, entries.size)
        // 10:30 UTC is 00:30 the next day at UTC+14.
        assertEquals(LocalDate.parse("2026-10-18"), entries[0].date)
        assertEquals(LocalDate.parse("2026-10-18"), entries[1].date)
        assertEquals(1250L, entries[0].amountCents)
        assertEquals(null, entries[1].amountCents)
        val first = server.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("/reports/api/v3/workspace/7/search/time_entries", first.url.encodedPath)
        val body = first.json()
        assertEquals("2026-10-12", body.string("start_date"))
        assertEquals("2026-10-18", body.string("end_date"))
        assertEquals(-1, body.int("rounding"))
        assertEquals(30, body.int("rounding_minutes"))
        assertEquals("date", body.string("order_by"))
        assertEquals("ASC", body.string("order_dir"))
        assertEquals(1, body.int("first_row_number"))
        val second = server.takeRequest()
        assertEquals("/reports/api/v3/workspace/7/search/time_entries", second.url.encodedPath)
        assertEquals(2, second.json().int("first_row_number"))
        assertEquals("2026-10-12", second.json().string("start_date"))
    }

    @Test
    fun aRowWithSeveralEntriesCountsItsAmountOnce() = runTest {
        val grouped = """{"billable":true,"currency":"EUR","project_id":3,"billable_amount_in_cents":900,
            "time_entries":[{"id":1,"seconds":600,"start":"2026-10-12T09:00:00+00:00"},{"id":2,"seconds":300,"start":"2026-10-13T09:00:00+00:00"}]}"""
        server.enqueue(json("[$grouped]"))

        val answer = run {
            reports.entries(
                ReportWorkspace(WorkspaceId(7), 0, 0),
                ReportRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18")),
                ZoneId.of("UTC"),
                ReportFilters(),
            )
        }

        val entries = (answer as ApiResult.Success).value
        assertEquals(listOf(600L, 300L), entries.map { it.seconds })
        assertEquals(listOf(900L, 0L), entries.map { it.amountCents })
    }

    @Test
    fun aNextRowThatDoesNotMoveForwardIsRefused() = runTest {
        server.enqueue(json("[]", NEXT_ROW to "1"))

        val answer = run {
            reports.entries(ReportWorkspace(WorkspaceId(7), 0, 0), oneDay(), ZoneId.of("UTC"), ReportFilters())
        }

        assertTrue("$answer", (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
        assertEquals(1, server.requestCount)
    }

    /** A Detailed page from row 3 whose next row is 3 again would load the same page forever. */
    @Test
    fun aDetailedPageWhoseNextRowDoesNotMoveForwardIsRefused() = runTest {
        server.enqueue(json("[]", NEXT_ROW to "3"))

        val answer = run {
            reports.detailedPage(
                ReportWorkspace(WorkspaceId(7), 0, 0),
                oneDay(),
                ZoneId.of("UTC"),
                firstRow = 3,
                ReportFilters(),
            )
        }

        assertTrue("$answer", (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aStartThatIsNotADateAndTimeIsAnInvalidResponse() = runTest {
        server.enqueue(
            json("""[{"billable":false,"currency":"EUR","time_entries":[{"seconds":1,"start":"yesterday"}]}]"""),
        )

        val answer = run {
            reports.entries(ReportWorkspace(WorkspaceId(7), 0, 0), oneDay(), ZoneId.of("UTC"), ReportFilters())
        }

        assertTrue("$answer", (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
    }

    /**
     * A Detailed page asks for 50 rows newest first from the row it starts at, with the
     * workspace's rounding and the filters, and reads each entry in the account's zone. The page
     * after it starts at the row the server names, and the last page names none.
     */
    @Test
    fun theDetailedSearchPagesNewestFirstAndReadsEachEntryInTheAccountsZone() = runTest {
        val stop = "\"2026-10-21T01:30:00+14:00\""
        server.enqueue(json("[" + detailedRow(10, "2026-10-21T00:30:00+14:00", stop) + "]", NEXT_ROW to "51"))
        server.enqueue(json("[" + detailedRow(9, "2026-10-20T10:00:00+00:00", "null") + "]"))
        val workspace = ReportWorkspace(WorkspaceId(7), 1, 15)
        val week = ReportRange(LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-27"))
        val zone = ZoneId.of("Pacific/Kiritimati")

        val first = (reports.detailedPage(workspace, week, zone, firstRow = 1, FILTERS) as ApiResult.Success).value

        assertEquals(51, first.nextRow)
        val entry = first.entries.single()
        assertEquals(zone, entry.start.zone)
        assertEquals(LocalDate.parse("2026-10-21"), entry.start.toLocalDate())
        assertEquals(0 to 30, entry.start.hour to entry.start.minute)
        assertEquals(listOf("d10", "Web", "Design"), listOf(entry.description, entry.projectName, entry.taskName))
        val request = server.takeRequest()
        assertEquals("/reports/api/v3/workspace/7/search/time_entries", request.url.encodedPath)
        val body = request.json()
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
            body.strings(
                "order_dir",
                "page_size",
                "first_row_number",
                "start_date",
                "end_date",
                "rounding",
                "rounding_minutes",
            ),
        )
        assertEquals(FILTER_FIELDS, body.filterFields())

        val last = (reports.detailedPage(workspace, week, zone, firstRow = 51, FILTERS) as ApiResult.Success).value

        assertEquals("the last page", null, last.nextRow)
        assertEquals("a running entry has no stop", null, last.entries.single().stop)
        val next = server.takeRequest().json()
        assertEquals("51", next.string("first_row_number"))
        assertEquals(FILTER_FIELDS, next.filterFields())
    }

    /**
     * The weekly report asks for the seven days from the week's start with the workspace's
     * rounding and the filters. A row reads project_hex_color when it has no project_color, and a
     * row without a project has no title.
     */
    @Test
    fun theWeeklyReportAsksForSevenDaysAndReadsEachProjectsRow() = runTest {
        server.enqueue(
            json(
                """[{"project_id":null,"project_name":null,"project_color":null,"seconds":[0,0,0,0,0,0,900]},
                {"project_id":5,"project_name":"Big","project_hex_color":"#990099","seconds":[3600,0,0,0,0,0,0]}]""",
            ),
        )

        val rows = run {
            reports.weekly(ReportWorkspace(WorkspaceId(7), 1, 15), LocalDate.parse("2026-10-21"), FILTERS)
        } as ApiResult.Success

        assertEquals(
            listOf(
                WeeklyRow(null, "", null, listOf(0L, 0L, 0L, 0L, 0L, 0L, 900L)),
                WeeklyRow(5, "Big", "#990099", listOf(3600L, 0L, 0L, 0L, 0L, 0L, 0L)),
            ),
            rows.value,
        )
        val request = server.takeRequest()
        assertEquals("/reports/api/v3/workspace/7/weekly/time_entries", request.url.encodedPath)
        val body = request.json()
        assertEquals(
            mapOf(
                "start_date" to "2026-10-21",
                "end_date" to "2026-10-27",
                "rounding" to "1",
                "rounding_minutes" to "15",
            ),
            body.strings("start_date", "end_date", "rounding", "rounding_minutes"),
        )
        assertEquals(FILTER_FIELDS, body.filterFields())
    }

    @Test
    fun aWeeklyRowWithoutSevenDaysIsAnInvalidResponse() = runTest {
        server.enqueue(json("""[{"project_id":1,"project_name":"x","seconds":[1,2]}]"""))

        val answer = run {
            reports.weekly(ReportWorkspace(WorkspaceId(7), 0, 0), LocalDate.parse("2026-10-21"), ReportFilters())
        }

        assertTrue("$answer", (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
    }

    private fun oneDay() = ReportRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-12"))

    /** A detailed row as Tokkl sends it, with one time entry. */
    private fun row(id: Int, start: String, amount: String) =
        """{"user_id":1,"username":"A","email":"a@example.com","project_id":3,"project_name":"Web","project_color":"#06aaf5",
        "project_hex":"#06aaf5","client_name":null,"task_id":null,"task_name":null,"description":"d","billable":true,
        "tag_ids":[],"tag_names":[],"hourly_rate_in_cents":5000,"billable_amount_in_cents":$amount,"currency":"EUR",
        "row_number":$id,"time_entries":[{"id":$id,"seconds":900,"start":"$start","stop":"x","at":"y"}]}"""

    /** A detailed row of Web's Design task with one entry of 15 minutes, as Tokkl sends it. */
    private fun detailedRow(id: Int, start: String, stop: String) = """{"project_id":3""" +
        ""","project_name":"Web","project_color":"#06aaf5","task_name":"Design","description":"d$id",""" +
        """"billable":true,"billable_amount_in_cents":null,"currency":"EUR","row_number":$id,""" +
        """"time_entries":[{"id":$id,"seconds":900,"start":"$start","stop":$stop,"at":"2026-10-20T10:00:00+00:00"}]}"""

    private fun json(body: String, vararg headers: Pair<String, String>) =
        MockResponse.Builder().code(200).body(body).addHeader("Content-Type", "application/json").apply {
            for ((name, value) in headers) addHeader(name, value)
        }.build()

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    private fun JsonObject.strings(vararg keys: String): Map<String, String> = keys.associateWith { string(it) }

    /** The filter fields of a request body, as JSON text. */
    private fun JsonObject.filterFields(): Map<String, String> = FILTER_KEYS
        .mapNotNull { key -> this[key]?.let { key to it.toString() } }
        .toMap()

    private companion object {
        const val NEXT_ROW = "X-Next-Row-Number"

        /** Project 3 or none, client 8, no tags, billable only. */
        val FILTERS = ReportFilters(
            projects = listOf(3L, null),
            clients = listOf(8L),
            tags = listOf(null),
            billable = BillableFilter.BILLABLE,
        )

        val FILTER_KEYS = listOf("project_ids", "client_ids", "tag_ids", "billable")

        /** [FILTERS] in the fields the web app sends, null for "without". */
        val FILTER_FIELDS = mapOf(
            "project_ids" to "[3,null]",
            "client_ids" to "[8]",
            "tag_ids" to "[null]",
            "billable" to "true",
        )
    }
}
