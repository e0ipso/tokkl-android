package com.github.e0ipso.tokkl.reports

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import com.github.e0ipso.tokkl.finishWithin
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import java.time.DayOfWeek
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The reports screen's Detailed tab against the real Tokkl container, in the real activity, on a
 * [ReportAccount] with 56 entries this week and one the day before it. The rows load 50 at a time
 * from Tokkl's Reports v3 search, and have to match what the web app's own endpoint,
 * `/api/app/report/entries`, lists for the week.
 */
class DetailedReportServerTest {
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
        // 55 entries over the week's days so far, ten minutes apart from 06:00 each day.
        for (i in 0 until 55) {
            val day = minOf(report.weekStart.plusDays((i % 7).toLong()), report.today)
            val project = when (i % 3) {
                0 -> null
                1 -> website
                else -> internal
            }
            report.entry(day.atTime(6, 0).plusMinutes(i * 10L), 300 + i * 7, "Entry $i", project)
        }
        report.entry(report.today.atTime(0, 10), 600, "Midnight", website)
        report.entry(report.weekStart.minusDays(1).atTime(12, 0), 3600, "Outside", website)
        Session(context).save(TokklTestServer.url, report.account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        assertEquals(DayOfWeek.WEDNESDAY, settings().beginningOfWeek)
        assertTrue("the 12-hour clock", settings().twelveHour)
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
     * The first page holds 50 rows and the next loads only at the end of the list. Each row shows
     * the account's day and clock: the entry at 00:10 today reads 12:10 AM under today's date. The
     * rows of both pages are the web app's, in its order.
     */
    @Test
    fun rowsMatchTheWebAppAndTheEndLoadsTheNextPage() {
        val first = openDetailed()
        assertEquals("one page of 50 before any scroll", 50, first.entries.size)
        assertNotNull(first.nextRow)
        composeRule.waitForIdle()
        assertEquals("no page asked for before the end", 50, detailed().entries.size)

        val midnight = first.entries.single { it.description == "Midnight" }
        assertEquals(report.today, midnight.start.toLocalDate())
        assertEquals("12:10 AM", settings().formatTime(midnight.start.toInstant(), midnight.start.zone))
        list().performScrollToIndex(first.entries.indexOf(midnight) + 1)
        composeRule.waitForIdle()
        assertTrue("12:10 AM shows", exists(hasText("12:10 AM", substring = true)))
        assertTrue("today's date shows", exists(hasText(DateWords(context.resources).day(report.today, report.today), substring = true)))

        var shown = first
        var pages = 1
        while (shown.nextRow != null) {
            val before = shown.entries.size
            list().performScrollToIndex(shown.entries.size + 1)
            composeRule.waitUntil("the next page loads", LOAD_TIMEOUT_MS) { detailed().let { !it.loadingMore && it.entries.size > before } }
            shown = detailed()
            pages++
        }
        assertEquals(TOTAL, shown.entries.size)
        assertEquals(2, pages)

        val web = report.webReport(
            "entries",
            buildJsonObject {
                put("start_date", report.weekStart.toString())
                put("end_date", report.weekStart.plusDays(6).toString())
                put("sort", "date")
                put("dir", "desc")
                put("page", 1)
                put("page_size", 500)
            },
        )
        val webRows = web.getValue("entries").jsonArray.map { it.jsonObject }.map {
            listOf(
                it.getValue("id").jsonPrimitive.long,
                OffsetDateTime.parse(it.getValue("start").jsonPrimitive.content).toInstant(),
                it.getValue("stop").jsonPrimitive.contentOrNull?.let { stop -> OffsetDateTime.parse(stop).toInstant() },
                it.getValue("seconds").jsonPrimitive.long,
                it.getValue("description").jsonPrimitive.content,
                it.getValue("project_name").jsonPrimitive.contentOrNull,
                it.getValue("task_name").jsonPrimitive.contentOrNull,
            )
        }
        val phoneRows = shown.entries.map { listOf(it.id, it.start.toInstant(), it.stop?.toInstant(), it.seconds, it.description, it.projectName, it.taskName) }
        assertEquals(webRows, phoneRows)
    }

    /** Offline, a page that cannot load shows the summary's message and no rows, and so do Try again and another week. */
    @Test
    fun offlineShowsTheSummarysMessageAndNoRows() {
        openDetailed()
        assertTrue(exists(hasText(detailed().entries.first().description)))
        AirplaneMode.set(true, report.account.client)

        list().performScrollToIndex(51)
        composeRule.waitUntil("the offline message shows", LOAD_TIMEOUT_MS) { exists(hasText(string(R.string.reports_offline_title))) }
        assertEquals(ReportFailure.Unreachable, (state() as ReportsState.Failed).failure)
        assertNoRows()

        composeRule.onNode(hasText(string(R.string.reports_retry)) and hasClickAction()).performClick()
        composeRule.waitUntil("Try again fails", LOAD_TIMEOUT_MS) { state() is ReportsState.Failed }
        assertTrue(exists(hasText(string(R.string.reports_offline_message))))
        assertNoRows()

        composeRule.onNode(hasContentDescription(string(R.string.reports_next_period))).performClick()
        composeRule.waitUntil("the next week fails", LOAD_TIMEOUT_MS) { state() is ReportsState.Failed }
        assertNoRows()
    }

    /** At 200% no row's text is cut off or leaves the screen, and every control has a label. */
    @AtLargestFontScale
    @Test
    fun rowsFitAtTheLargestFontScale() {
        assertEquals(SystemFontScale.LARGEST, context.resources.configuration.fontScale)
        openDetailed()
        assertTextsFit()
        list().performScrollToIndex(20)
        assertTextsFit()
        for (node in composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertTrue("a control without a label: ${node.config}", text.isNotBlank() || description.isNotBlank())
        }
    }

    private fun assertNoRows() {
        for (i in 0 until 55) assertFalse("Entry $i shows offline", exists(hasText("Entry $i")))
        assertFalse(exists(hasText("Midnight")))
    }

    private fun assertTextsFit() {
        composeRule.waitForIdle()
        val width = context.resources.displayMetrics.widthPixels
        for (node in composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: continue
            layout(node)?.let { assertFalse("cut off: $text", it.didOverflowHeight) }
            val left = node.positionOnScreen.x
            assertTrue("leaves the screen's width: $text", left >= -1 && left + node.size.width <= width + 1)
        }
    }

    private fun layout(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    /** Starts the app, opens Reports and its Detailed tab, and returns the first page. */
    private fun openDetailed(): DetailedReport {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.openBarPage(R.string.reports, LOAD_TIMEOUT_MS)
        composeRule.waitUntil("the summary loads", LOAD_TIMEOUT_MS) { state() is ReportsState.Loaded }
        composeRule.onNode(hasText(string(R.string.reports_tab_detailed)) and hasClickAction()).performClick()
        composeRule.waitUntil("the Detailed tab loads", LOAD_TIMEOUT_MS) { (state() as? ReportsState.Loaded)?.report is DetailedReport }
        composeRule.waitForIdle()
        return detailed()
    }

    private fun list() = composeRule.onNode(hasScrollToIndexAction())

    private fun detailed(): DetailedReport = (state() as ReportsState.Loaded).report as DetailedReport

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

        /** The week's entries: 55, and the one at 00:10 today. */
        const val TOTAL = 56
    }
}
