package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.time.LocalDate
import java.time.ZoneId
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

/**
 * The Reports API v3 requests behind the summary and the decoding of Tokkl's detailed rows,
 * against a MockWebServer: the workspace and its rounding, the search body, paging by
 * `X-Next-Row-Number`, and each entry's day in the account's timezone.
 */
class ReportsServerTest {
    private val server = MockWebServer()
    private lateinit var reports: ReportsServer

    @Before
    fun setUp() {
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        reports = ReportsServer(TogglApiClient(url, ApiCredentials.ApiToken("token")))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun theWorkspaceIsTheDefaultOneWithItsRounding() {
        server.enqueue(json("""{"id":1,"default_workspace_id":7,"timezone":"UTC","beginning_of_week":1}"""))
        server.enqueue(json("""{"id":7,"rounding":1,"rounding_minutes":15,"default_currency":"EUR"}"""))

        assertEquals(ApiResult.Success(ReportWorkspace(7, 1, 15)), reports.workspace())
        assertEquals("/api/v9/me", server.takeRequest().url.encodedPath)
        assertEquals("/api/v9/workspaces/7", server.takeRequest().url.encodedPath)
    }

    @Test
    fun theSearchAsksForTheRangeWithTheWorkspacesRoundingAndFollowsTheNextRow() {
        server.enqueue(json("[" + row(1, "2026-10-17T10:30:00+00:00", "1250") + "]", NEXT_ROW to "2"))
        server.enqueue(json("[" + row(2, "2026-10-18T09:59:59+00:00", "null") + "]"))
        val range = ReportRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18"))

        val answer = reports.entries(ReportWorkspace(7, -1, 30), range, ZoneId.of("Pacific/Kiritimati"))

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
    fun aRowWithSeveralEntriesCountsItsAmountOnce() {
        val grouped = """{"billable":true,"currency":"EUR","project_id":3,"billable_amount_in_cents":900,
            "time_entries":[{"id":1,"seconds":600,"start":"2026-10-12T09:00:00+00:00"},{"id":2,"seconds":300,"start":"2026-10-13T09:00:00+00:00"}]}"""
        server.enqueue(json("[$grouped]"))

        val answer = reports.entries(ReportWorkspace(7, 0, 0), ReportRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18")), ZoneId.of("UTC"))

        val entries = (answer as ApiResult.Success).value
        assertEquals(listOf(600L, 300L), entries.map { it.seconds })
        assertEquals(listOf(900L, 0L), entries.map { it.amountCents })
    }

    @Test
    fun aNextRowThatDoesNotMoveForwardIsRefused() {
        server.enqueue(json("[]", NEXT_ROW to "1"))

        val answer = reports.entries(ReportWorkspace(7, 0, 0), oneDay(), ZoneId.of("UTC"))

        assertTrue("$answer", (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
        assertEquals(1, server.requestCount)
    }

    /** A Detailed page from row 3 whose next row is 3 again would load the same page forever. */
    @Test
    fun aDetailedPageWhoseNextRowDoesNotMoveForwardIsRefused() {
        server.enqueue(json("[]", NEXT_ROW to "3"))

        val answer = reports.detailedPage(ReportWorkspace(7, 0, 0), oneDay(), ZoneId.of("UTC"), firstRow = 3)

        assertTrue("$answer", (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aStartThatIsNotADateAndTimeIsAnInvalidResponse() {
        server.enqueue(json("""[{"billable":false,"currency":"EUR","time_entries":[{"seconds":1,"start":"yesterday"}]}]"""))

        val answer = reports.entries(ReportWorkspace(7, 0, 0), oneDay(), ZoneId.of("UTC"))

        assertTrue("$answer", (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
    }

    private fun oneDay() = ReportRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-12"))

    /** A detailed row as Tokkl sends it, with one time entry. */
    private fun row(id: Int, start: String, amount: String) =
        """{"user_id":1,"username":"A","email":"a@example.com","project_id":3,"project_name":"Web","project_color":"#06aaf5",
        "project_hex":"#06aaf5","client_name":null,"task_id":null,"task_name":null,"description":"d","billable":true,
        "tag_ids":[],"tag_names":[],"hourly_rate_in_cents":5000,"billable_amount_in_cents":$amount,"currency":"EUR",
        "row_number":$id,"time_entries":[{"id":$id,"seconds":900,"start":"$start","stop":"x","at":"y"}]}"""

    private fun json(body: String, vararg headers: Pair<String, String>) =
        MockResponse.Builder().code(200).body(body).addHeader("Content-Type", "application/json").apply {
            for ((name, value) in headers) addHeader(name, value)
        }.build()

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    private companion object {
        const val NEXT_ROW = "X-Next-Row-Number"
    }
}
