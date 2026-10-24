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
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

class AppSummaryModelTest {
    private val http = MockWebServer()
    private val requests = CopyOnWriteArrayList<JsonObject>()
    private val paths = CopyOnWriteArrayList<String>()
    private val io = QueuedDispatcher()
    private lateinit var model: ReportsModel
    @Volatile private var summaryBody = APP_SUMMARY_FIXTURE
    @Volatile private var summaryStatus = 200
    @Volatile private var beforeSummaryReply: (() -> Unit)? = null

    @Before
    fun setUp() {
        http.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.url.encodedPath
                return when (request.url.encodedPath) {
                    "/api/v9/me" -> json("""{"default_workspace_id":7,"role":"user"}""")
                    "/api/v9/workspaces/7" -> json("""{"rounding":1,"rounding_minutes":15}""")
                    "/api/app/report" -> {
                        requests += Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                        beforeSummaryReply?.invoke()
                        MockResponse.Builder().code(summaryStatus).body(summaryBody).build()
                    }
                    "/reports/api/v3/workspace/7/search/time_entries", "/reports/api/v3/workspace/7/weekly/time_entries" -> json("[]")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        http.start()
        val url = ServerUrl.parse(http.url("/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        model = ReportsModel(
            accountSettings = MutableStateFlow(AccountSettings("Pacific/Kiritimati", DayOfWeek.WEDNESDAY, timeResolutionMinutes = null)),
            client = { TogglApiClient(url, ApiCredentials.ApiToken("test-token")) },
            scope = CoroutineScope(Dispatchers.Unconfined), io = io,
            clock = Clock.fixed(Instant.parse("2026-10-20T11:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        model.close()
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        http.close()
    }

    @Test
    fun accountTimezoneAndWeekStartSelectTheAppReportDates() {
        model.open()
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()
        assertEquals("2026-10-21", requests.single().getValue("start_date").jsonPrimitive.content)
        assertEquals("2026-10-27", requests.single().getValue("end_date").jsonPrimitive.content)
        assertEquals(7317L, report().totals.seconds)
        assertEquals(Resolution.WEEK, report().resolution)
        assertTrue(paths.none { it.contains("search/time_entries") })
    }

    @Test
    fun foregroundRefreshDropsOldValuesAndUsesTheNewServerAnswer() {
        model.open(); io.runAll()
        val first = model.state.value
        model.cameToForeground()
        assertSame(first, model.state.value)
        assertEquals(1, requests.size)
        summaryBody = APP_SUMMARY_FIXTURE.replace("7317", "9001")
        model.wentToBackground(); model.cameToForeground()
        assertTrue(model.state.value is ReportsState.Loading)
        io.runAll()
        assertEquals(9001L, report().totals.seconds)
        assertEquals(2, requests.size)
    }

    @Test
    fun latestRangeAndFiltersCancelQueuedLoads() {
        model.open()
        model.select(RangePreset.TODAY)
        model.applyFilters(ReportFilters(tags = listOf(null, 4), tagsNot = true))
        io.runAll()
        assertEquals(1, requests.size)
        assertEquals("2026-10-21", requests.single().getValue("end_date").jsonPrimitive.content)
        assertEquals("[null,4]", requests.single().getValue("exclude_tag_ids").toString())
        assertEquals(7317L, report().totals.seconds)
    }

    @Test
    fun closeCancelsPendingLoadsAndReopenReadsTheAccountAgain() {
        model.open(); model.close(); io.runAll()
        assertEquals(ReportsState.Closed, model.state.value)
        assertTrue(requests.isEmpty())
        model.open(); io.runAll()
        assertEquals(7317L, report().totals.seconds)
        model.close(); model.open(); io.runAll()
        assertEquals(2, paths.count { it == "/api/v9/me" })
    }

    @Test
    fun closingWhileTheServerIsAnsweringCannotPublishItsLateSummary() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        beforeSummaryReply = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "test did not release summary reply" }
        }
        val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val url = ServerUrl.parse(http.url("/").toString())!!
        val live = ReportsModel(
            accountSettings = MutableStateFlow(AccountSettings("Pacific/Kiritimati", DayOfWeek.WEDNESDAY, timeResolutionMinutes = null)),
            client = { TogglApiClient(url, ApiCredentials.ApiToken("test-token")) },
            scope = liveScope, io = Dispatchers.IO,
        )
        try {
            live.open()
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            val loading = liveScope.coroutineContext[Job]!!.children.single()
            live.close()
            assertEquals(ReportsState.Closed, live.state.value)
            release.countDown()
            withTimeout(5_000) { loading.join() }
            assertEquals(ReportsState.Closed, live.state.value)
            assertEquals(1, requests.size)
        } finally {
            release.countDown()
            beforeSummaryReply = null
            live.close()
            liveScope.cancel()
        }
    }

    @Test
    fun failureRefreshShowsNoStaleNumbersAndRetryCanRecover() {
        model.open(); io.runAll()
        summaryBody = "{}"
        model.retry(); io.runAll()
        assertEquals(ReportFailure.Unexpected(null), (model.state.value as ReportsState.Failed).failure)
        assertTrue(paths.none { it.contains("search/time_entries") })
        summaryBody = APP_SUMMARY_FIXTURE
        model.retry(); io.runAll()
        assertEquals(7317L, report().totals.seconds)
    }

    @Test
    fun networkRecoveryRetriesSummaryButAuthAndQuotaRemainVisible() {
        model.open(); io.runAll()
        for ((status, expected) in listOf(503 to ReportFailure.Unreachable, 403 to ReportFailure.AuthFailed, 429 to ReportFailure.Unexpected(429))) {
            summaryStatus = status
            model.retry(); io.runAll()
            assertEquals(expected, (model.state.value as ReportsState.Failed).failure)
            val count = requests.size
            model.reconnected(); io.runAll()
            assertEquals(count + if (status == 503) 1 else 0, requests.size)
        }
        assertTrue(paths.none { it.contains("search/time_entries") })
    }

    @Test
    fun switchingTabsKeepsDetailedAndWeeklyOnTheirExistingEndpoints() {
        model.open(); io.runAll()
        model.selectTab(ReportTab.DETAILED); io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is DetailedReport)
        assertEquals("/reports/api/v3/workspace/7/search/time_entries", paths.last())
        model.selectTab(ReportTab.WEEKLY); io.runAll()
        assertTrue((model.state.value as ReportsState.Loaded).report is WeeklyReport)
        assertEquals("/reports/api/v3/workspace/7/weekly/time_entries", paths.last())
        assertEquals(1, requests.size)
    }

    private fun report() = (model.state.value as ReportsState.Loaded).report as SummaryReport
    private fun json(body: String) = MockResponse.Builder().code(200).body(body).build()
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
}
