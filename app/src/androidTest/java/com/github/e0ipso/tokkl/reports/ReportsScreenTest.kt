package com.github.e0ipso.tokkl.reports

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.finishWithin
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.Timeout

/**
 * The reports screen against the real Tokkl container (docs/PRD.md R1 and R5), for an account at
 * UTC+14 whose weeks start on Wednesday, unlike the emulator's zone and Monday weeks. The
 * account's workspace rounds each entry up to 15 minutes and its time earns euros and dollars.
 * The summary has to match POST /api/app/report, the web app's own call for the same range.
 */
class ReportsScreenTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val fontScale: TestRule = LargestFontScaleWhenMarked()

    /**
     * Fails a test that hangs instead of the whole run. The compose rule waits for the app's main
     * thread to go idle after each test with no timeout, so a screen that lays itself out on every
     * frame kept it waiting for good (TKA-0166). Order it outside the compose rule and inside the
     * font scale rule, which puts the usual scale back for the next test.
     */
    @get:Rule(order = 2)
    val timeout = Timeout(TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES)

    @get:Rule(order = 3)
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private lateinit var seed: Seed
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun signInToASeededAccount() {
        account = TestAccounts.create(timezone = ZONE)
        seed = Seed(account.client)
        Session(context).save(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        val settings = settings()
        assertEquals(ZONE, settings.timeZone)
        assertEquals(DayOfWeek.WEDNESDAY, settings.beginningOfWeek)
    }

    @After
    fun cleanUp() {
        try {
            scenario?.let { finishWithin("closing the activity") { it.close() } }
        } finally {
            try {
                DeviceShell.run("cmd connectivity airplane-mode disable")
                awaitServer(answers = true)
            } finally {
                try {
                    if (::account.isInitialized) TestAccounts.delete(account)
                } finally {
                    SessionRule.signOut()
                }
            }
        }
    }

    @Test
    fun theSummaryMatchesTheWebAppsReportInTheAccountsTime() {
        assertNotEquals("the account's zone has to differ from the emulator's", ZoneId.systemDefault(), ZoneId.of(ZONE))
        val report = openReports()

        assertEquals("this week in the account's zone and week start", ReportRange(seed.weekStart, seed.weekStart.plusDays(6)), report.range)
        assertMatchesTheWebApp(report)
        // Four entries this week, each rounded up to 15 minutes, in two currencies.
        assertEquals(4, report.totals.entryCount)
        assertEquals(1800L + 4500 + 900 + 1800, report.totals.seconds)
        assertEquals(listOf("EUR" to 2000L, "USD" to 12500L), report.totals.amounts.map { it.currency to it.cents })
        val today = report.series.single { it.start == seed.today }
        assertTrue("the entry at 00:10 counts on today in the account's zone: $today", today.seconds >= 900)

        val settings = settings()
        compose.onNode(hasText(DateWords(context.resources).range(report.range, seed.today), substring = true)).assertExists()
        compose.onNode(hasText(settings.formatDuration(report.totals.seconds))).assertExists()
        for (project in report.projects) {
            compose.onNode(hasText(project.title.ifBlank { string(R.string.reports_without_project) }, substring = true)).performScrollTo()
        }
        compose.onNode(hasText(string(R.string.reports_mixed_currencies))).performScrollTo()
    }

    @Test
    fun theChartTellsTalkBackEveryDaysTime() {
        val report = openReports()

        val description = compose.onNode(hasContentDescription(CHART_DESCRIPTION, substring = true))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.single()
        assertTrue(description, description.startsWith("$CHART_DESCRIPTION, 2 hours, 30 minutes in total. "))
        val words = DateWords(context.resources)
        assertEquals(7, report.series.size)
        for (point in report.series) {
            val day = "${words.bucket(point.start, Resolution.DAY)}: ${spokenDuration(context.resources, point.seconds)}"
            assertTrue("the chart reads \"$day\": $description", description.contains(day))
        }
        assertTrue("an empty day reads as no time: $description", report.series.none { it.seconds == 0L } || description.contains(string(R.string.reports_spoken_no_time)))
    }

    @Test
    fun offlineTheScreenSaysSoAndShowsNoNumbers() {
        val report = openReports()
        assertTrue("the total shows", hasNode(hasText(settings().formatDuration(report.totals.seconds), substring = true)))

        DeviceShell.run("cmd connectivity airplane-mode enable")
        awaitServer(answers = false)
        compose.onNode(hasContentDescription(string(R.string.reports_next_period))).performClick()
        awaitText(string(R.string.reports_offline_title))
        assertNoNumbers(report)

        compose.onNode(hasContentDescription(string(R.string.reports_previous_period))).performClick()
        awaitText(string(R.string.reports_offline_title))
        assertNoNumbers(report)

        compose.onNode(hasText(string(R.string.reports_retry)) and hasClickAction()).performClick()
        awaitText(string(R.string.reports_offline_title))
        assertNoNumbers(report)
        assertEquals(ReportFailure.Unreachable, (modelState() as? ReportsState.Failed)?.failure)
    }

    @AtLargestFontScale
    @Test
    fun theScreenAndItsRangeDialogsFitAtTheLargestFontScale() {
        assertEquals(SystemFontScale.LARGEST, context.resources.configuration.fontScale)
        openReports()

        assertTextsFit()
        assertControlsLabelled()

        compose.onNode(hasText(string(R.string.reports_preset_this_week)) and hasClickAction()).performScrollTo().performClick()
        awaitText(string(R.string.reports_range_title))
        assertTextsFit()
        assertControlsLabelled()

        compose.onNode(hasText(string(R.string.reports_custom_range)) and hasClickAction()).performScrollTo().performClick()
        awaitText(string(R.string.reports_custom_range_title))
        assertTextsFit()
        assertControlsLabelled()

        compose.onNode(hasText(string(R.string.reports_apply)) and hasClickAction()).performClick()
        compose.waitUntil("the custom range dialog closes", LOAD_TIMEOUT_MS) { !hasNode(isDialog()) }
    }

    private fun assertMatchesTheWebApp(report: SummaryReport) {
        val body = buildJsonObject {
            put("start_date", report.range.from.toString())
            put("end_date", report.range.to.toString())
            put("grouping", "projects")
            put("sub_grouping", "projects")
        }
        val web = account.client.send("POST", TokklTestServer.url.app("report"), body, JsonObject.serializer()).orFail("POST /api/app/report")
        assertEquals(web["resolution"]!!.jsonPrimitive.content, report.resolution.name.lowercase())
        val totals = web["totals"]!!.jsonObject
        assertEquals(totals["seconds"]!!.jsonPrimitive.long, report.totals.seconds)
        assertEquals(totals["billable_seconds"]!!.jsonPrimitive.long, report.totals.billableSeconds)
        assertEquals(totals["entry_count"]!!.jsonPrimitive.long, report.totals.entryCount.toLong())
        assertEquals(
            totals["amounts"]!!.jsonArray.map { it.jsonObject["currency"]!!.jsonPrimitive.content to it.jsonObject["cents"]!!.jsonPrimitive.long },
            report.totals.amounts.map { it.currency to it.cents },
        )
        assertEquals(
            web["series"]!!.jsonArray.map {
                val point = it.jsonObject
                Triple(point["start"]!!.jsonPrimitive.content, point["seconds"]!!.jsonPrimitive.long, point["billable_seconds"]!!.jsonPrimitive.long)
            },
            report.series.map { Triple(it.start.toString(), it.seconds, it.billableSeconds) },
        )
        assertEquals(
            web["groups"]!!.jsonArray.map {
                val group = it.jsonObject
                listOf(
                    group["id"]!!.jsonPrimitive.longOrNull,
                    group["title"]!!.jsonPrimitive.content,
                    group["seconds"]!!.jsonPrimitive.long,
                    group["billable_seconds"]!!.jsonPrimitive.long,
                    group["amount_cents"]!!.jsonPrimitive.long,
                )
            },
            report.projects.map { listOf(it.id, it.title, it.seconds, it.billableSeconds, it.amountCents) },
        )
    }

    /**
     * No text on screen or in an open dialog is cut off or leaves the screen's width. Semantics
     * lays text out again at the full width, so only the height says whether it was cut off. Text
     * in a row that scrolls sideways, such as the report tabs at the largest font scale, may lie
     * past the edge, since a scroll brings it in.
     */
    private fun assertTextsFit() {
        compose.waitForIdle()
        val screenWidth = context.resources.displayMetrics.widthPixels
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("no text found", texts.isNotEmpty())
        for (node in texts) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: continue
            textLayout(node)?.let { assertFalse("cut off: $text", it.didOverflowHeight) }
            if (scrollsSideways(node)) continue
            val left = node.positionOnScreen.x
            val right = left + node.size.width
            assertTrue("leaves the screen's width, $left to $right of $screenWidth: $text", left >= -1 && right <= screenWidth + 1)
        }
    }

    private fun scrollsSideways(node: SemanticsNode): Boolean =
        generateSequence(node.parent) { it.parent }.any { SemanticsProperties.HorizontalScrollAxisRange in it.config }

    private fun textLayout(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    /** Every control has a label that TalkBack reads. */
    private fun assertControlsLabelled() {
        for (node in compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertTrue("a control without a label: ${node.config}", text.isNotBlank() || description.isNotBlank())
        }
    }

    private fun assertNoNumbers(report: SummaryReport) {
        val settings = settings()
        val durations = listOf(report.totals.seconds, report.totals.billableSeconds) + report.series.map { it.seconds }.filter { it > 0 }
        for (seconds in durations) {
            val shown = settings.formatDuration(seconds)
            assertFalse("$shown shows offline", hasNode(hasText(shown, substring = true)))
        }
        assertFalse("the totals show offline", hasNode(hasText(string(R.string.reports_total_time), substring = true, ignoreCase = true)))
        assertFalse("the chart shows offline", hasNode(hasContentDescription(CHART_DESCRIPTION, substring = true)))
    }

    /** Launches the app, opens the reports and returns the report once it loads. */
    private fun openReports(): SummaryReport {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.openBarPage(R.string.reports, LOAD_TIMEOUT_MS)
        compose.waitUntil("a report loads", LOAD_TIMEOUT_MS) { modelState().let { it !is ReportsState.Loading && it != ReportsState.Closed } }
        val state = modelState()
        assertTrue("a report loads: $state", state is ReportsState.Loaded)
        compose.waitForIdle()
        return (state as ReportsState.Loaded).report as SummaryReport
    }

    private fun modelState(): ReportsState {
        var state: ReportsState? = null
        finishWithin("reading the report's state") {
            scenario!!.onActivity { state = ViewModelProvider(it)[ReportsViewModel::class.java].model.state.value }
        }
        return state!!
    }

    private fun settings(): AccountSettings = app.accountSettings.state.value ?: throw AssertionError("no account settings")

    private fun hasNode(matcher: SemanticsMatcher): Boolean = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun awaitText(text: String) = compose.waitUntil("\"$text\" shows", LOAD_TIMEOUT_MS) { hasNode(hasText(text, substring = true)) }

    /** Waits until requests to the test server get through, or fail for want of a network. */
    private fun awaitServer(answers: Boolean) {
        if (!::account.isInitialized) return
        val deadline = System.nanoTime() + LOAD_TIMEOUT_MS * 1_000_000
        while (true) {
            val result = account.client.get(account.client.server.api("me"), JsonObject.serializer())
            if (((result as? ApiResult.Failure)?.error !is ApiError.Network) == answers) return
            check(System.nanoTime() < deadline) { "the server ${if (answers) "does not answer" else "still answers"}" }
            Thread.sleep(POLL_MS)
        }
    }

    private fun string(id: Int): String = context.getString(id)

    /**
     * The account's week, workspace and projects, and five entries: four this week, one of them
     * at 00:10 today in the account's zone, and one the day before the week starts.
     */
    private class Seed(private val api: TogglApiClient) {
        val zone: ZoneId = ZoneId.of(ZONE)
        val today: LocalDate = LocalDate.now(zone)
        val weekStart: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))
        private val workspace = api.get(api.server.api("me"), MeIds.serializer()).orFail("GET /api/v9/me").defaultWorkspaceId

        init {
            api.send("PUT", api.server.api("me"), buildJsonObject { put("beginning_of_week", 3) }, JsonObject.serializer()).orFail("PUT /me")
            val rounding = buildJsonObject {
                put("rounding", 1)
                put("rounding_minutes", 15)
                put("default_hourly_rate", 40)
                put("default_currency", "EUR")
            }
            api.send("PUT", api.server.api("workspaces", workspace.toString()), rounding, JsonObject.serializer()).orFail("PUT the workspace")
            api.send("POST", api.server.api("me", "preferences"), buildJsonObject { put("duration_format", "classic") }, JsonObject.serializer())
                .orFail("POST /me/preferences")
            val website = project(buildJsonObject { put("name", "Website"); put("color", "#06aaf5"); put("billable", true) })
            val consulting = project(
                buildJsonObject { put("name", "Consulting"); put("color", "#c7af14"); put("billable", true); put("rate", 100); put("currency", "USD") },
            )
            val internal = project(buildJsonObject { put("name", "Internal"); put("color", "#990099") })
            entry(day(0).atTime(9, 0), 1000, website, billable = true)
            entry(day(1).atTime(14, 0), 3700, consulting, billable = true)
            entry(today.atTime(0, 10), 600, null, billable = false)
            entry(if (day(2) == today) today.atTime(0, 30) else day(2).atTime(23, 50), 1200, internal, billable = false)
            entry(weekStart.minusDays(1).atTime(12, 0), 3600, website, billable = true)
        }

        /** The week's [index]th day, or today when that day is still to come. */
        private fun day(index: Long): LocalDate = minOf(weekStart.plusDays(index), today)

        private fun project(body: JsonObject): Long =
            api.send("POST", api.server.api("workspaces", workspace.toString(), "projects"), body, Created.serializer()).orFail("POST a project").id

        private fun entry(start: LocalDateTime, seconds: Int, project: Long?, billable: Boolean) {
            val body = buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", "Reports")
                put("start", start.atZone(zone).toInstant().toString())
                put("duration", seconds)
                if (project != null) put("project_id", project)
                put("billable", billable)
            }
            api.send("POST", api.server.api("workspaces", workspace.toString(), "time_entries"), body, Created.serializer()).orFail("POST a time entry")
        }
    }

    @Serializable
    private class MeIds(@SerialName("default_workspace_id") val defaultWorkspaceId: Long)

    @Serializable
    private class Created(val id: Long)

    private companion object {
        /** UTC+14 all year, so its day differs from UTC's for ten hours of every day. */
        const val ZONE = "Pacific/Kiritimati"
        const val CHART_DESCRIPTION = "Bar chart of time per day"
        const val LOAD_TIMEOUT_MS = 30_000L
        const val POLL_MS = 200L

        /** Well above the sum of a test's own waits, and far below CI's 45 minutes for the run. */
        const val TEST_TIMEOUT_MINUTES = 5L
    }
}
