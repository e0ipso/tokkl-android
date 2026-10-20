package com.github.e0ipso.tokkl.reports

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.finishWithin
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
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
 * The report filters against the real Tokkl container (docs/PRD.md R4), in the real activity, on
 * a [ReportAccount] with four entries this week: Website, whose client is Acme, Internal, with no
 * client, and no project; tagged deep, meeting, both or neither; billable or not. Each filter,
 * picked in the sheet, has to give the summary, the detailed list and the weekly grid that the
 * web app's own endpoints give for the same filter and week.
 */
class ReportFiltersTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var report: ReportAccount
    private var scenario: ActivityScenario<MainActivity>? = null
    private var relay: RequestCounter? = null
    private var website = 0L
    private var acme = 0L
    private var meeting = 0L

    @Before
    fun seedTheAccount() {
        report = ReportAccount.create()
        acme = report.client("Acme")
        website = report.project("Website", "#06aaf5", client = acme, billable = true)
        val internal = report.project("Internal", "#990099")
        val deep = report.tag("deep")
        meeting = report.tag("meeting")
        // An hour apart, so the detailed list's order never rests on a tie.
        report.entry(day(0).atTime(1, 0), 3600, "Design", website, listOf(deep), billable = true)
        report.entry(day(1).atTime(2, 0), 1800, "Standup", internal, listOf(meeting), billable = false)
        report.entry(day(2).atTime(3, 0), 1200, "Email", null, billable = false)
        report.entry(day(3).atTime(4, 0), 900, "Review", website, listOf(deep, meeting), billable = false)
        report.entry(report.weekStart.minusDays(1).atTime(12, 0), 3600, "Outside", website, listOf(deep), billable = true)
        Session(context).save(TokklTestServer.url, report.account.apiToken)
        // Fills the cache, where the sheet's choices come from.
        runBlocking { app.accountSettings.refresh().join() }
    }

    @After
    fun cleanUp() {
        if (!::report.isInitialized) return SessionRule.signOut()
        try {
            scenario?.let { finishWithin("closing the activity") { it.close() } }
        } finally {
            try {
                // The relay replaced the stored server and its cleartext confirmation.
                ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                Session(context).save(TokklTestServer.url, report.account.apiToken)
                relay?.close()
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
     * Several values of one filter keep the entries that match any of them, null standing for
     * "without", and filters together keep those that match all. The active filters show as text
     * on the screen, and clearing them brings back every entry.
     */
    @Test
    fun eachFilterNarrowsEveryReportAsTheWebAppDoes() {
        openReports()
        val withoutProject = string(R.string.reports_without_project)
        val withoutClient = string(R.string.reports_filter_without_client)
        val withoutTags = string(R.string.reports_filter_without_tags)

        checkFilter(
            picks = listOf("Website", withoutProject),
            web = filter("project_ids" to ids(website, null)),
            entries = 3,
            lines = listOf(line(R.string.reports_filter_project, "Website, $withoutProject")),
        )
        checkFilter(
            picks = listOf(withoutClient),
            web = filter("client_ids" to ids(null)),
            entries = 2,
            lines = listOf(line(R.string.reports_filter_client, withoutClient)),
        )
        checkFilter(
            picks = listOf("meeting", withoutTags),
            web = filter("tag_ids" to ids(meeting, null)),
            entries = 3,
            lines = listOf(line(R.string.reports_filter_tag, "meeting, $withoutTags")),
        )
        checkFilter(
            picks = listOf("Acme"),
            billable = R.string.reports_filter_billable_only,
            web = filter("client_ids" to ids(acme), billable = true),
            entries = 1,
            lines = listOf(line(R.string.reports_filter_client, "Acme"), string(R.string.reports_filter_billable_only)),
        )
        val last = listOf(line(R.string.reports_filter_project, "Website"), string(R.string.reports_filter_non_billable_only))
        checkFilter(
            picks = listOf("Website"),
            billable = R.string.reports_filter_non_billable_only,
            web = filter("project_ids" to ids(website), billable = false),
            entries = 1,
            lines = last,
        )

        // Website's entries, the only ones of client Acme, all have tags.
        checkFilter(
            picks = listOf("Acme", withoutTags),
            web = filter("client_ids" to ids(acme), "tag_ids" to ids(null)),
            entries = 0,
            lines = listOf(line(R.string.reports_filter_client, "Acme"), line(R.string.reports_filter_tag, withoutTags)),
        )
        assertTrue("the empty report names the filters", exists(hasText(string(R.string.reports_empty_title_filtered))))
        assertTrue(exists(hasText(string(R.string.reports_empty_message_filtered))))

        checkFilter(picks = emptyList(), web = JsonObject(emptyMap()), entries = 4, lines = emptyList())
        for (line in last) assertFalse("\"$line\" goes once the filters are cleared", exists(hasText(line)))
    }

    /**
     * "Is not" drops the entries that match any picked value, null standing for "without", as the
     * web app's exclude fields do, and the text on the screen says the filter is negated.
     */
    @Test
    fun isNotDropsWhatTheWebAppDrops() {
        openReports()
        val withoutClient = string(R.string.reports_filter_without_client)
        val withoutTags = string(R.string.reports_filter_without_tags)

        checkFilter(
            picks = listOf("Website"),
            negate = listOf(R.string.reports_filter_project),
            web = filter("exclude_project_ids" to ids(website)),
            entries = 2,
            lines = listOf(notLine(R.string.reports_filter_project, "Website")),
        )
        checkFilter(
            picks = listOf(withoutClient),
            negate = listOf(R.string.reports_filter_client),
            web = filter("exclude_client_ids" to ids(null)),
            entries = 2,
            lines = listOf(notLine(R.string.reports_filter_client, withoutClient)),
        )
        checkFilter(
            picks = listOf("meeting", withoutTags),
            negate = listOf(R.string.reports_filter_tag),
            web = filter("exclude_tag_ids" to ids(meeting, null)),
            entries = 1,
            lines = listOf(notLine(R.string.reports_filter_tag, "meeting, $withoutTags")),
        )
        // "Is" on one filter and "Is not" on another: Website's entries without meeting.
        checkFilter(
            picks = listOf("Website", "meeting"),
            negate = listOf(R.string.reports_filter_tag),
            web = filter("project_ids" to ids(website), "exclude_tag_ids" to ids(meeting)),
            entries = 1,
            lines = listOf(line(R.string.reports_filter_project, "Website"), notLine(R.string.reports_filter_tag, "meeting")),
        )

        checkFilter(picks = emptyList(), web = JsonObject(emptyMap()), entries = 4, lines = emptyList())
        assertEquals("Clear filters turns \"Is not\" off too", ReportFilters(), model().filters.value)
    }

    /** Against a server that cannot negate, such as Toggl Track, the sheet offers no "Is not". */
    @Test
    fun theSheetOffersNoIsNotWhereTheServerCannotNegate() {
        openReports()
        val project = string(R.string.reports_filter_project)
        openSheet()
        sheet().performScrollToNode(inSheet(modeSwitch(R.string.reports_filter_mode_not_description, project)))
        composeRule.onNode(inSheet(hasContentDescription(string(R.string.reports_cancel)) and hasClickAction())).performClick()
        composeRule.waitUntil("the sheet closes", LOAD_TIMEOUT_MS) { !exists(sheetTitle()) }

        // The refreshes as the app starts are over, so nothing turns the flag back on.
        Thread.sleep(SETTLE_MS)
        app.capabilities.forget()
        assertFalse(app.capabilities.state.value.negatedFilters)
        openSheet()
        sheet().performScrollToNode(inSheet(hasText("Website") and isToggleable()))
        for (filter in listOf(R.string.reports_filter_project, R.string.reports_filter_client, R.string.reports_filter_tag)) {
            assertFalse("no \"Is not\" for ${string(filter)}", exists(inSheet(modeSwitch(R.string.reports_filter_mode_not_description, string(filter)))))
        }
    }

    /** The sheet offers the cached projects, clients and tags, and opening or closing it sends nothing. */
    @Test
    fun theSheetOffersTheCachedChoicesAndAsksNothing() {
        val relay = RequestCounter(TokklTestServer.url, " HTTP/1.1").also { this.relay = it }
        ServerTrustStore(context).confirmCleartext(relay.url)
        Session(context).save(relay.url, report.account.apiToken)
        openReports()
        // The refresh as the app starts, and the report.
        Thread.sleep(SETTLE_MS)
        val before = relay.count
        assertTrue("the app's requests pass the relay", before > 0)

        openSheet()
        val choices = listOf(
            string(R.string.reports_without_project), "Website", "Internal",
            string(R.string.reports_filter_without_client), "Acme",
            string(R.string.reports_filter_without_tags), "deep", "meeting",
        )
        for (choice in choices) sheet().performScrollToNode(inSheet(hasText(choice) and isToggleable()))
        Thread.sleep(SETTLE_MS)
        assertEquals("opening the sheet asks nothing", before, relay.count)

        composeRule.onNode(inSheet(hasContentDescription(string(R.string.reports_cancel)) and hasClickAction())).performClick()
        composeRule.waitUntil("the sheet closes", LOAD_TIMEOUT_MS) { !exists(sheetTitle()) }
        Thread.sleep(SETTLE_MS)
        assertEquals("closing it asks nothing", before, relay.count)
        assertEquals(ReportFilters(), model().filters.value)
        assertEquals("the relay reached the server each time", emptyList<String>(), relay.failures)
    }

    /**
     * Picks [picks], "Is not" on each filter of [negate], and [billable] when given, in a cleared
     * sheet and applies them. Then the summary, the detailed list and the weekly grid have to be
     * the web app's for the [web] filter fields and hold [entries] entries, and the screen has to
     * show [lines].
     */
    private fun checkFilter(
        picks: List<String>,
        @StringRes billable: Int? = null,
        negate: List<Int> = emptyList(),
        web: JsonObject,
        entries: Int,
        lines: List<String>,
    ) {
        pickInTheSheet(picks, billable, negate)
        val summary = awaitReport<SummaryReport>()
        for (line in lines) assertTrue("\"$line\" shows", exists(hasText(line)))
        val range = (state() as ReportsState.Loaded).selection.range
        assertEquals("this week", ReportRange(report.weekStart, report.weekStart.plusDays(6)), range)

        val webSummary = webSummary(
            webBody(web, range) {
                put("grouping", "projects")
                put("sub_grouping", "projects")
            },
        )
        assertEquals("entries for $picks", entries, summary.totals.entryCount)
        val totals = webSummary.getValue("totals").jsonObject
        assertEquals(totals.getValue("entry_count").jsonPrimitive.long, summary.totals.entryCount.toLong())
        assertEquals(totals.getValue("seconds").jsonPrimitive.long, summary.totals.seconds)
        assertEquals(totals.getValue("billable_seconds").jsonPrimitive.long, summary.totals.billableSeconds)
        assertEquals(
            webSummary.getValue("groups").jsonArray.map { it.jsonObject }.map {
                listOf(it.getValue("id").jsonPrimitive.longOrNull, it.getValue("title").jsonPrimitive.content, it.getValue("seconds").jsonPrimitive.long)
            },
            summary.projects.map { listOf(it.id, it.title, it.seconds) },
        )

        selectTab(R.string.reports_tab_detailed)
        val detailed = awaitReport<DetailedReport>()
        val webEntries = report.webReport(
            "entries",
            webBody(web, range) {
                put("sort", "date")
                put("dir", "desc")
                put("page", 1)
                put("page_size", 500)
            },
        )
        assertEquals(webEntries.getValue("entries").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.long }, detailed.entries.map { it.id })
        assertEquals(entries, detailed.entries.size)

        selectTab(R.string.reports_tab_weekly)
        val weekly = awaitReport<WeeklyReport>()
        val webWeekly = report.webReport("weekly", webBody(web, range) { put("rows", "projects") })
        assertEquals(
            webWeekly.getValue("rows").jsonArray.map { it.jsonObject }.map {
                listOf(
                    it.getValue("id").jsonPrimitive.longOrNull,
                    it.getValue("title").jsonPrimitive.content,
                    it.getValue("cells").jsonArray.map { cell -> cell.jsonPrimitive.long },
                )
            },
            weekly.rows.map { listOf(it.id, it.title, it.seconds) },
        )
        assertEquals(webWeekly.getValue("total").jsonPrimitive.long, weekly.total)
        assertEquals(summary.totals.seconds, weekly.total)

        selectTab(R.string.reports_tab_summary)
        awaitReport<SummaryReport>()
    }

    /** Opens the sheet, clears it, picks [picks], "Is not" on each filter of [negate] and [billable], and applies them. */
    private fun pickInTheSheet(picks: List<String>, @StringRes billable: Int?, negate: List<Int>) {
        openSheet()
        val clear = inSheet(hasText(string(R.string.reports_filters_clear)) and hasClickAction())
        sheet().performScrollToNode(clear)
        if (SemanticsProperties.Disabled !in composeRule.onNode(clear).fetchSemanticsNode().config) composeRule.onNode(clear).performClick()
        for (filter in negate) {
            val mode = inSheet(modeSwitch(R.string.reports_filter_mode_not_description, string(filter)))
            sheet().performScrollToNode(mode)
            composeRule.onNode(mode).performClick().assertIsSelected()
        }
        for (pick in picks) {
            val row = inSheet(hasText(pick) and isToggleable())
            sheet().performScrollToNode(row)
            composeRule.onNode(row).performClick().assertIsOn()
        }
        if (billable != null) {
            val row = inSheet(hasText(string(billable)) and isSelectable())
            sheet().performScrollToNode(row)
            composeRule.onNode(row).performClick().assertIsSelected()
        }
        composeRule.onNode(inSheet(hasText(string(R.string.reports_apply)) and hasClickAction())).performClick()
        composeRule.waitUntil("the sheet closes", LOAD_TIMEOUT_MS) { !exists(sheetTitle()) }
    }

    private fun openSheet() {
        composeRule.onNode(hasText(string(R.string.reports_filters)) and hasClickAction()).performClick()
        composeRule.waitUntil("the filter sheet opens", LOAD_TIMEOUT_MS) { exists(sheetTitle()) }
    }

    /** The sheet's list, which scrolls. */
    private fun sheet(): SemanticsNodeInteraction = composeRule.onNode(inSheet(hasScrollToIndexAction()))

    private fun inSheet(matcher: SemanticsMatcher): SemanticsMatcher = matcher and hasAnyAncestor(isDialog())

    private fun selectTab(@StringRes label: Int) {
        composeRule.onNode(hasText(string(label)) and hasClickAction()).performClick()
    }

    /** Starts the app, opens Reports and waits for this week's summary. */
    private fun openReports() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.openBarPage(R.string.reports, LOAD_TIMEOUT_MS)
        awaitReport<SummaryReport>()
    }

    /**
     * Waits for a report of type [T]. A change of the filters or the tab shows Loading at once, so
     * a report seen after one is its answer.
     */
    private inline fun <reified T : Report> awaitReport(): T {
        composeRule.waitUntil("a ${T::class.simpleName} loads", LOAD_TIMEOUT_MS) {
            val state = state()
            check(state !is ReportsState.Failed) { "the report failed: $state" }
            (state as? ReportsState.Loaded)?.report is T
        }
        composeRule.waitForIdle()
        return (state() as ReportsState.Loaded).report as T
    }

    private fun state(): ReportsState = model().state.value

    private fun model(): ReportsModel {
        var model: ReportsModel? = null
        finishWithin("reading the reports model") {
            checkNotNull(scenario).onActivity { model = ViewModelProvider(it)[ReportsViewModel::class.java].model }
        }
        return checkNotNull(model)
    }

    /** The web app's filter fields: each of [fields] with its ids, and billable when given. */
    private fun filter(vararg fields: Pair<String, JsonArray>, billable: Boolean? = null): JsonObject = buildJsonObject {
        for ((field, ids) in fields) put(field, ids)
        if (billable != null) put("billable", billable)
    }

    /** [ids] as a JSON array, null for "without". */
    private fun ids(vararg ids: Long?): JsonArray = JsonArray(ids.map { if (it == null) JsonNull else JsonPrimitive(it) })

    /** A body for the web app's report endpoints: [range], the [filter] fields and [more]. */
    private fun webBody(filter: JsonObject, range: ReportRange, more: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("start_date", range.from.toString())
        put("end_date", range.to.toString())
        for ((key, value) in filter) put(key, value)
        more()
    }

    /** POSTs [body] to `/api/app/report`, the web app's summary, a path [ReportAccount.webReport] does not reach. */
    private fun webSummary(body: JsonObject): JsonObject =
        report.account.client.send("POST", TokklTestServer.url.app("report"), body, JsonObject.serializer()).orFail("POST /api/app/report")

    /** The week's [index]th day, or today when that day is still to come. */
    private fun day(index: Long): LocalDate = minOf(report.weekStart.plusDays(index), report.today)

    private fun line(@StringRes filter: Int, values: String): String = context.getString(R.string.reports_filter_line, string(filter), values)

    private fun notLine(@StringRes filter: Int, values: String): String = context.getString(R.string.reports_filter_line_not, string(filter), values)

    /** The "Is" or "Is not" segment, by [description], of the filter named [filter]. */
    private fun modeSwitch(@StringRes description: Int, filter: String): SemanticsMatcher =
        hasContentDescription(context.getString(description, filter)) and isSelectable()

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int) = context.getString(id)

    private fun sheetTitle(): SemanticsMatcher = inSheet(hasText(string(R.string.reports_filters)) and isHeading())

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L

        /** Long enough for the requests of the app's start to end. */
        const val SETTLE_MS = 2_000L
    }
}
