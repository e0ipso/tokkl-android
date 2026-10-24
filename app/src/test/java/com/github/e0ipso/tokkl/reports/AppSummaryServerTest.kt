package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.DurationFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppSummaryServerTest {
    private val http = MockWebServer()
    private lateinit var server: ReportsServer
    private val range = ReportRange(LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-27"))
    private val workspace = ReportWorkspace(7, 1, 15, appSummaryCandidate = true)
    private val zone = ZoneId.of("Pacific/Kiritimati")
    private val filters = ReportFilters(
        projects = listOf(null, 8), clients = listOf(9, null), tags = listOf(10, null),
        billable = BillableFilter.BILLABLE, projectsNot = true, tagsNot = true,
    )

    @Before
    fun setUp() {
        http.start()
        val url = ServerUrl.parse(http.url("/prefix/tokkl/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        server = ReportsServer(TogglApiClient(url, ApiCredentials.ApiToken("test-token")))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        http.close()
    }

    @Test
    fun defaultWorkspaceAndRoleMakeACandidateWithoutHostnameOrVersionChecks() {
        http.enqueue(json("""{"default_workspace_id":7,"role":"user"}"""))
        http.enqueue(json("""{"rounding":1,"rounding_minutes":15}"""))
        assertEquals(ApiResult.Success(workspace), server.workspace())
        assertEquals("/prefix/tokkl/api/v9/me", http.takeRequest().url.encodedPath)
        assertEquals("/prefix/tokkl/api/v9/workspaces/7", http.takeRequest().url.encodedPath)
    }

    @Test
    fun appPostPreservesPrefixFiltersAndInclusiveDatesButDoesNotOverrideCurrentRounding() {
        http.enqueue(json(APP_SUMMARY_FIXTURE))
        val report = success(server.summary(workspace, range, zone, DayOfWeek.WEDNESDAY, filters))
        assertEquals(7317L, report.totals.seconds)
        val request = http.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/prefix/tokkl/api/app/report", request.url.encodedPath)
        assertTrue(request.headers["Authorization"]!!.startsWith("Basic "))
        assertEquals(
            jsonObject("""{"start_date":"2026-10-21","end_date":"2026-10-27","grouping":"projects","sub_grouping":"projects",
              "exclude_project_ids":[null,8],"client_ids":[9,null],"exclude_tag_ids":[10,null],"billable":true}"""),
            jsonObject(request.body!!.utf8()),
        )
        assertEquals(1, http.requestCount)
    }

    @Test
    fun allFilterVariantsAreForwardedWithoutChangingTheirMeaning() {
        val cases = listOf(
            ReportFilters() to "{}",
            ReportFilters(projects = listOf(null), clients = listOf(null), tags = listOf(null), billable = BillableFilter.NON_BILLABLE, clientsNot = true) to
                """{"project_ids":[null],"exclude_client_ids":[null],"tag_ids":[null],"billable":false}""",
        )
        for ((selected, expected) in cases) {
            http.enqueue(json(APP_SUMMARY_FIXTURE))
            success(server.summary(workspace, range, zone, DayOfWeek.WEDNESDAY, selected))
            val body = jsonObject(http.takeRequest().body!!.utf8())
            assertEquals(jsonObject(expected), JsonObject(body - setOf("start_date", "end_date", "grouping", "sub_grouping")))
        }
    }

    @Test
    fun onlyDefinitiveAppEndpointAbsenceUsesRoundedDetailedFallback() {
        for (status in listOf(404, 410)) {
            http.enqueue(MockResponse.Builder().code(status).build())
            http.enqueue(json(FALLBACK_ROWS))
            val report = success(server.summary(workspace, range, zone, DayOfWeek.WEDNESDAY, filters))
            assertEquals(ReportTotals(1800, 1800, 2, listOf(Amount("EUR", 168))), report.totals)
            assertEquals(1800L, report.series.first().seconds)
            assertEquals(LocalDate.parse("2026-10-21"), report.series.first().start)
            assertEquals("/prefix/tokkl/api/app/report", http.takeRequest().url.encodedPath)
            val fallback = http.takeRequest()
            assertEquals("/prefix/tokkl/reports/api/v3/workspace/7/search/time_entries", fallback.url.encodedPath)
            val body = jsonObject(fallback.body!!.utf8())
            assertEquals(jsonObject("""{"rounding":1,"rounding_minutes":15,"exclude_project_ids":[null,8],"client_ids":[9,null],"exclude_tag_ids":[10,null],"billable":true}"""),
                JsonObject(body - setOf("start_date", "end_date", "order_by", "order_dir", "page_size", "first_row_number")))
        }
    }

    @Test
    fun nonCandidateUsesExistingDetailedSearchWithoutAnAppProbe() {
        http.enqueue(json(FALLBACK_ROWS))
        val report = success(server.summary(workspace.copy(appSummaryCandidate = false), range, zone, DayOfWeek.WEDNESDAY))
        assertEquals(168L, report.totals.amounts.single().cents)
        assertEquals("/prefix/tokkl/reports/api/v3/workspace/7/search/time_entries", http.takeRequest().url.encodedPath)
        assertEquals(1, http.requestCount)
    }

    @Test
    fun authQuotaAndServerFailuresNeverFallBack() {
        for (status in listOf(401, 403, 429, 500, 502, 503, 504)) {
            // A dispatcher also prevents an accidental fallback from hanging the test.
            http.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(status).addHeader("Retry-After", "17").build()
            }
            val before = http.requestCount
            val answer = server.summary(workspace, range, zone, DayOfWeek.WEDNESDAY) as ApiResult.Failure
            assertEquals(status, (answer.error as ApiError.Http).status)
            assertEquals(before + 1, http.requestCount)
            assertEquals("/prefix/tokkl/api/app/report", http.takeRequest().url.encodedPath)
        }
    }

    @Test
    fun malformedSuccessfulResponsesStayErrorsAndDoNotSearch() {
        for (body in listOf("{}", "[]", "not JSON", APP_SUMMARY_FIXTURE.replace("2026-10-28", "bad-date"))) {
            http.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = json(body)
            }
            val before = http.requestCount
            val answer = server.summary(workspace, range, zone, DayOfWeek.WEDNESDAY) as ApiResult.Failure
            assertTrue(answer.error is ApiError.InvalidResponse)
            assertEquals(before + 1, http.requestCount)
            assertEquals("/prefix/tokkl/api/app/report", http.takeRequest().url.encodedPath)
        }
    }

    @Test
    fun unreachableServerStaysANetworkFailure() {
        http.close()
        val answer = server.summary(workspace, range, zone, DayOfWeek.WEDNESDAY) as ApiResult.Failure
        assertTrue(answer.error is ApiError.Network)
    }

    @Test
    fun detailedWeeklyAndCsvKeepTheirExistingContractsForTokklCandidates() {
        http.enqueue(json("[]"))
        server.detailedPage(workspace, range, zone, 1, filters)
        val detailed = http.takeRequest()
        assertEquals("/prefix/tokkl/reports/api/v3/workspace/7/search/time_entries", detailed.url.encodedPath)
        assertEquals("DESC", jsonObject(detailed.body!!.utf8()).getValue("order_dir").toString().trim('"'))
        http.enqueue(json("[]"))
        server.weekly(workspace, range.from, filters)
        assertEquals("/prefix/tokkl/reports/api/v3/workspace/7/weekly/time_entries", http.takeRequest().url.encodedPath)
        for (kind in ReportCsvKind.entries) {
            val old = ReportCsvRequest(kind, range, filters, workspace.copy(appSummaryCandidate = false), DurationFormat.CLASSIC)
            val current = old.copy(workspace = workspace)
            assertEquals(old.body(), current.body())
            assertTrue(current.body().containsKey("rounding"))
        }
        assertEquals(null, ReportCsvKind.of(ReportTab.WEEKLY))
        assertFalse(ReportCsvRequest(ReportCsvKind.DETAILED, range, filters, workspace, DurationFormat.CLASSIC).body().containsKey("grouping"))
    }

    private fun json(body: String) = MockResponse.Builder().code(200).body(body).addHeader("Content-Type", "application/json").build()
    private fun jsonObject(body: String) = Json.parseToJsonElement(body).jsonObject
    private fun success(result: ApiResult<SummaryReport>) = (result as ApiResult.Success).value

    private companion object {
        // Two independently rounded 84-cent entries. Do not derive the expected 168 cents from
        // a rate multiplied by their aggregate duration. The UTC dates become Oct 21 at UTC+14.
        val FALLBACK_ROWS = """[{"billable":true,"currency":"EUR","billable_amount_in_cents":84,
          "time_entries":[{"seconds":900,"start":"2026-10-20T11:00:00Z"}]},
          {"billable":true,"currency":"EUR","billable_amount_in_cents":84,
          "time_entries":[{"seconds":900,"start":"2026-10-20T12:00:00Z"}]}]"""
    }
}
