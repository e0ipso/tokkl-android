package com.github.e0ipso.tokkl.reports

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createEmptyComposeRule
import com.github.e0ipso.tokkl.finishWithin
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import java.time.DayOfWeek
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The reports screen's Weekly tab against the real Tokkl container, in the real activity, on a
 * [ReportAccount] with 13 entries this week over three projects and time without one, and one the
 * day before the week. The grid has to match what the web app's own endpoint,
 * `/api/app/report/weekly`, sends for the account's week.
 */
class WeeklyReportServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(LargestFontScaleWhenMarked()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var report: ReportAccount
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun seedTheAccount() {
        report = ReportAccount.create()
        val website = report.project("Website", "#06aaf5")
        val internal = report.project("Internal", "#990099")
        val long = report.project("Graffiti wall maintenance and repainting", "#d94182")
        for (i in 0 until 12) {
            val day = minOf(report.weekStart.plusDays((i % 7).toLong()), report.today)
            val project = when (i % 4) {
                0 -> null
                1 -> website
                2 -> internal
                else -> long
            }
            report.entry(day.atTime(8, 0).plusMinutes(i * 37L), 1000 + i * 611, "Entry $i", project)
        }
        report.entry(report.today.atTime(0, 10), 600, "Midnight", website)
        report.entry(report.weekStart.minusDays(1).atTime(12, 0), 3600, "Outside", website)
        Session(context).save(TokklTestServer.url, report.account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        assertEquals(DayOfWeek.WEDNESDAY, settings().beginningOfWeek)
    }

    @After
    fun cleanUp() {
        if (!::report.isInitialized) return SessionRule.signOut()
        try {
            scenario?.let { finishWithin("closing the activity") { it.close() } }
        } finally {
            try {
                AirplaneMode.set(false, report.account.client)
            } finally {
                try {
                    TestAccounts.delete(report.account)
                } finally {
                    SessionRule.signOut()
                }
            }
        }
    }

    /**
     * The grid is the web app's for the account's week from Wednesday: rows, cells, day totals and
     * the week's total. TalkBack reads each cell with its project and day, an empty one as no
     * time, and the week's total. The arrow steps a week, and the summary keeps the week shown.
     */
    @Test
    fun theGridMatchesTheWebAppInTheAccountsWeek() {
        val weekly = openWeekly()
        assertEquals("the account's week from Wednesday", report.weekStart, weekly.weekStart)
        assertEquals(ReportRange(report.weekStart, report.weekStart.plusDays(6)), (state() as ReportsState.Loaded).selection.range)
        val web = report.webReport(
            "weekly",
            buildJsonObject {
                put("start_date", report.weekStart.toString())
                put("end_date", report.weekStart.plusDays(6).toString())
                put("rows", "projects")
            },
        )
        assertEquals("days", web.getValue("mode").jsonPrimitive.content)
        val webRows = web.getValue("rows").jsonArray.map { it.jsonObject }.map {
            listOf(
                it.getValue("id").jsonPrimitive.longOrNull,
                it.getValue("title").jsonPrimitive.content,
                it.getValue("cells").jsonArray.map { cell -> cell.jsonPrimitive.long },
                it.getValue("total").jsonPrimitive.long,
            )
        }
        assertEquals(webRows, weekly.rows.map { listOf(it.id, it.title, it.seconds, it.total) })
        assertEquals(web.getValue("column_totals").jsonArray.map { it.jsonPrimitive.long }, weekly.dayTotals)
        assertEquals(web.getValue("total").jsonPrimitive.long, weekly.total)
        assertEquals(web.getValue("columns").jsonArray.map { it.jsonObject.getValue("start").jsonPrimitive.content }, weekly.days.map { it.toString() })

        val words = DateWords(context.resources)
        val row = weekly.rows.first()
        val day = row.seconds.indexOfFirst { it > 0 }
        val cell = context.getString(
            R.string.reports_weekly_cell_description,
            row.title,
            words.bucket(weekly.days[day], Resolution.DAY),
            spokenDuration(context.resources, row.seconds[day]),
        )
        assertTrue("a cell reads \"$cell\"", exists(hasContentDescription(cell)))
        val sparse = weekly.rows.first { it.seconds.any { seconds -> seconds == 0L } }
        val emptyDay = sparse.seconds.indexOfFirst { it == 0L }
        val empty = context.getString(
            R.string.reports_weekly_cell_description,
            sparse.title.ifBlank { string(R.string.reports_without_project) },
            words.bucket(weekly.days[emptyDay], Resolution.DAY),
            string(R.string.reports_spoken_no_time),
        )
        assertTrue("an empty cell reads \"$empty\"", exists(hasContentDescription(empty)))
        assertTrue(exists(hasContentDescription(weekTotal(weekly))))

        composeRule.onNode(hasContentDescription(string(R.string.reports_previous_period))).performClick()
        composeRule.waitUntil("last week loads", LOAD_TIMEOUT_MS) {
            ((state() as? ReportsState.Loaded)?.report as? WeeklyReport)?.weekStart == report.weekStart.minusDays(7)
        }
        assertEquals("the entry the day before the week", listOf(3600L), ((state() as ReportsState.Loaded).report as WeeklyReport).rows.map { it.total })

        composeRule.onNode(hasText(string(R.string.reports_tab_summary)) and hasClickAction()).performClick()
        composeRule.waitUntil("the summary loads", LOAD_TIMEOUT_MS) { (state() as? ReportsState.Loaded)?.report is SummaryReport }
        assertEquals(report.weekStart.minusDays(7), (state() as ReportsState.Loaded).selection.range.from)
    }

    /** A month chosen on the summary becomes today's week on the Weekly tab, which offers only week presets. */
    @Test
    fun aLongerRangeShowsTodaysWeekAndOnlyWeekPresets() {
        openReports()
        composeRule.onNode(hasText(string(R.string.reports_preset_this_week)) and hasClickAction()).performClick()
        composeRule.waitUntil("the range dialog opens", LOAD_TIMEOUT_MS) { exists(hasText(string(R.string.reports_range_title))) }
        composeRule.onNode(hasText(string(R.string.reports_preset_this_month))).performClick()
        composeRule.waitUntil("this month loads", LOAD_TIMEOUT_MS) { (state() as? ReportsState.Loaded)?.selection?.preset == RangePreset.THIS_MONTH }

        composeRule.onNode(hasText(string(R.string.reports_tab_weekly)) and hasClickAction()).performClick()
        composeRule.waitUntil("the Weekly tab loads", LOAD_TIMEOUT_MS) { (state() as? ReportsState.Loaded)?.report is WeeklyReport }
        assertEquals(ReportRange(report.weekStart, report.weekStart.plusDays(6)), (state() as ReportsState.Loaded).selection.range)

        composeRule.onNode(hasText(string(R.string.reports_preset_this_week)) and hasClickAction()).performClick()
        composeRule.waitUntil("the range dialog opens", LOAD_TIMEOUT_MS) { exists(hasText(string(R.string.reports_range_title))) }
        assertFalse("no month preset", exists(hasText(string(R.string.reports_preset_this_month))))
        assertTrue(exists(hasText(string(R.string.reports_preset_last_week))))
    }

    /** Offline, another week shows the summary's message and no number, and so does Try again. */
    @Test
    fun offlineShowsTheSummarysMessageAndNoNumbers() {
        val weekly = openWeekly()
        assertTrue(exists(hasContentDescription(weekTotal(weekly))))
        AirplaneMode.set(true, report.account.client)

        composeRule.onNode(hasContentDescription(string(R.string.reports_next_period))).performClick()
        composeRule.waitUntil("the offline message shows", LOAD_TIMEOUT_MS) { exists(hasText(string(R.string.reports_offline_title))) }
        assertEquals(ReportFailure.Unreachable, (state() as ReportsState.Failed).failure)
        assertNoNumbers(weekly)

        composeRule.onNode(hasText(string(R.string.reports_retry)) and hasClickAction()).performClick()
        composeRule.waitUntil("Try again fails", LOAD_TIMEOUT_MS) { state() is ReportsState.Failed }
        assertTrue(exists(hasText(string(R.string.reports_offline_message))))
        assertNoNumbers(weekly)
    }

    /**
     * At 200% no text is cut off in height, and the grid, wider than the screen, scrolls sideways
     * with every row together, so the week's total scrolls into view.
     */
    @AtLargestFontScale
    @Test
    fun theGridFitsAndScrollsSidewaysAtTheLargestFontScale() {
        assertEquals(SystemFontScale.LARGEST, context.resources.configuration.fontScale)
        val weekly = openWeekly()
        assertTextsFitHeight()

        val total = composeRule.onNode(hasContentDescription(weekTotal(weekly)))
        total.performScrollTo()
        composeRule.waitForIdle()
        val node = total.fetchSemanticsNode()
        val width = context.resources.displayMetrics.widthPixels
        assertTrue("the week's total is on screen: ${node.positionOnScreen.x}", node.positionOnScreen.x >= 0 && node.positionOnScreen.x + node.size.width <= width)
        // The tab row scrolls sideways too, so it is left out.
        val scrollers = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNodes()
            .filterNot { it.holdsATab() }
        assertEquals("a scroller for the head row, each project's row and the totals", weekly.rows.size + 2, scrollers.size)
        val range = scrollers.first().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue("the grid is wider than the screen", range.maxValue() > 0f)
        assertTrue("every row scrolled with the first", scrollers.all { it.config[SemanticsProperties.HorizontalScrollAxisRange].value() == range.value() })
        assertTextsFitHeight()
        for (control in composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            val text = control.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
            val description = control.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertTrue("a control without a label: ${control.config}", text.isNotBlank() || description.isNotBlank())
        }
    }

    private fun SemanticsNode.holdsATab(): Boolean =
        generateSequence(children) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }
            .flatten()
            .any { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }

    private fun weekTotal(weekly: WeeklyReport): String =
        context.getString(R.string.reports_weekly_total_description, spokenDuration(context.resources, weekly.total))

    private fun assertNoNumbers(weekly: WeeklyReport) {
        val settings = settings()
        for (seconds in weekly.dayTotals.filter { it > 0 } + weekly.total + weekly.rows.map { it.total }) {
            assertFalse("${settings.formatDuration(seconds)} shows offline", exists(hasText(settings.formatDuration(seconds), substring = true)))
            assertFalse(
                "${spokenDuration(context.resources, seconds)} reads offline",
                exists(hasContentDescription(spokenDuration(context.resources, seconds), substring = true)),
            )
        }
        assertFalse(exists(hasText(string(R.string.reports_weekly_title))))
        for (row in weekly.rows) if (row.title.isNotBlank()) assertFalse("${row.title} shows offline", exists(hasText(row.title)))
    }

    /** No text is cut off in height. The grid may run off the side, where it scrolls. */
    private fun assertTextsFitHeight() {
        composeRule.checkAccessibility()
        composeRule.waitForIdle()
        for (node in composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: continue
            layout(node)?.let { assertFalse("cut off: $text", it.didOverflowHeight) }
        }
    }

    private fun layout(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    /** Starts the app and opens Reports on its summary. */
    private fun openReports() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.openBarPage(R.string.reports, LOAD_TIMEOUT_MS)
        composeRule.waitUntil("the summary loads", LOAD_TIMEOUT_MS) { state() is ReportsState.Loaded }
    }

    /** Opens Reports and its Weekly tab, and returns the week shown. */
    private fun openWeekly(): WeeklyReport {
        openReports()
        composeRule.onNode(hasText(string(R.string.reports_tab_weekly)) and hasClickAction()).performClick()
        composeRule.waitUntil("the Weekly tab loads", LOAD_TIMEOUT_MS) { (state() as? ReportsState.Loaded)?.report is WeeklyReport }
        composeRule.waitForIdle()
        return (state() as ReportsState.Loaded).report as WeeklyReport
    }

    private fun state(): ReportsState {
        var state: ReportsState? = null
        finishWithin("reading the reports state") {
            checkNotNull(scenario).onActivity { state = ViewModelProvider(it)[ReportsViewModel::class.java].model.state.value }
        }
        return checkNotNull(state)
    }

    private fun settings(): AccountSettings = checkNotNull(app.accountSettings.state.value)

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int) = context.getString(id)

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
    }
}
