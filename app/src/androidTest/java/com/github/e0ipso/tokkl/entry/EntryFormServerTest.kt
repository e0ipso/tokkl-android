package com.github.e0ipso.tokkl.entry

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The entry form against the real Tokkl container: an entry the web app made, opened from the
 * history, edited and saved through the change queue (docs/PRD.md H2), and past entries added
 * from the Timer page (M1), online and in airplane mode. The test's own requests stand in for the web app and go straight to the server. Each test
 * signs in to an account of its own, in Madrid, and deletes it after.
 */
class EntryFormServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    /** Two days ago in Madrid, inside the two weeks the phone keeps. */
    private val day: LocalDate = ZonedDateTime.now(MADRID).toLocalDate().minusDays(2)

    @Before
    fun signIn() {
        account = TestAccounts.create(timezone = MADRID.id)
        workspace = web.get(web.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long
        signIn(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.off()
        } finally {
            try {
                // TestAccounts talks to the server through the app's guard, which follows the session.
                signIn(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun everyFieldOfAnEntryChangesAndReachesTheServer() {
        val entry = createEntry("Fix the report", at(day, 9, 0), at(day, 10, 0))
        val project = createProject("Form project")
        // Another day of the same month, so the date picker shows it without turning the page.
        val newDay = if (day.dayOfMonth > 1) day.minusDays(1) else day.plusDays(1)
        launch {
            openEntry("Fix the report")

            field(R.string.entry_description).performTextClearance()
            field(R.string.entry_description).performTextInput("Fix the weekly report")
            projectButton(null).performClick()
            option("Form project").performClick()
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.picker_search))).performTextInput("call")
            option(string(R.string.picker_add_tag, "call")).performClick()
            composeRule.onNode(hasText(string(R.string.entry_picker_done)) and hasClickAction()).performClick()
            awaitShows("the project shows") { projectButton("Form project") }
            composeRule.onNode(hasText(string(R.string.entry_billable)) and isToggleable()).performClick()

            dateButton().performScrollTo().performClick()
            dayInPicker(newDay).performClick()
            composeRule.onNode(hasText(string(R.string.entry_picker_done)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            awaitShows("the new date shows") { composeRule.onNode(hasContentDescription(spokenDate(newDay)) and hasClickAction()) }

            replace(R.string.entry_start, "930")
            replace(R.string.entry_end, "11")
            save()
            drainUntilEmpty()
        }

        val saved = web.get(web.server.api("me", "time_entries", entry.toString()), JsonObject.serializer()).orFail("GET the entry")
        assertEquals("Fix the weekly report", saved.string("description"))
        assertEquals(project, saved.getValue("project_id").jsonPrimitive.long)
        assertEquals(listOf("call"), saved.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertTrue("billable", saved.getValue("billable").jsonPrimitive.boolean)
        assertEquals(at(newDay, 9, 30), instant(saved.string("start")))
        assertEquals(at(newDay, 11, 0), instant(saved.string("stop")))
    }

    @Test
    fun anEditMadeInAirplaneModeReachesTheServerWhenTheNetworkReturns() {
        val entry = createEntry("Offline edit", at(day, 14, 0), at(day, 15, 0))
        launch {
            AirplaneMode.set(true, web)
            openEntry("Offline edit")
            field(R.string.entry_description).performTextClearance()
            field(R.string.entry_description).performTextInput("Offline edit, saved")
            replace(R.string.entry_end, "16:15")
            save()
            awaitShows("the history says the server lacks it") {
                composeRule.onNode(hasText("Offline edit, saved") and hasText(string(R.string.history_unsent), ignoreCase = true))
            }

            AirplaneMode.set(false, web)
            drainUntilEmpty()
        }

        val saved = web.get(web.server.api("me", "time_entries", entry.toString()), JsonObject.serializer()).orFail("GET the entry")
        assertEquals("Offline edit, saved", saved.string("description"))
        assertEquals(at(day, 14, 0), instant(saved.string("start")))
        assertEquals(at(day, 16, 15), instant(saved.string("stop")))
    }

    @Test
    fun timesThatCannotBeReadShowAMessageAndQueueNothing() {
        createEntry("Wrong times", at(day, 8, 0), at(day, 9, 0))
        launch {
            openEntry("Wrong times")
            replace(R.string.entry_start, "soon")
            composeRule.onNode(hasText(string(R.string.entry_save)) and hasClickAction()).performScrollTo().performClick()

            awaitShows("the start says what is wrong, in words") {
                composeRule.onNode(isField(R.string.entry_start) and hasText(string(R.string.entry_mistake_times)))
            }
            assertTrue("the start field is marked as an error", field(R.string.entry_start).isError())
            assertEquals("nothing queued", 0, runBlocking { app.changes.unsentCount.first() })
            assertTrue("the form stays open", shows(composeRule.onNode(hasText(string(R.string.entry_form_edit)) and isHeading())))
        }
    }

    /** TalkBack reads a name for every field and control, and what each button holds. */
    @Test
    fun everyFieldAndControlHasALabel() {
        createEntry("Labelled", at(day, 11, 0), at(day, 12, 30))
        launch {
            openEntry("Labelled")
            val controls = listOf(
                "back" to composeRule.onNode(hasContentDescription(string(R.string.back)) and hasClickAction()),
                "heading" to composeRule.onNode(hasText(string(R.string.entry_form_edit)) and isHeading()),
                "description" to field(R.string.entry_description),
                "project" to projectButton(null),
                "tags" to composeRule.onNode(hasContentDescription(string(R.string.entry_no_tags_spoken)) and hasClickAction()),
                "billable" to composeRule.onNode(hasText(string(R.string.entry_billable)) and isToggleable()),
                "date" to composeRule.onNode(hasContentDescription(spokenDate(day)) and hasClickAction()),
                "start" to field(R.string.entry_start),
                "end" to field(R.string.entry_end),
                "duration" to field(R.string.entry_duration),
                "cancel" to composeRule.onNode(hasText(string(R.string.entry_cancel)) and hasClickAction()),
                "save" to composeRule.onNode(hasText(string(R.string.entry_save)) and hasClickAction()),
            )
            for ((name, node) in controls) assertTrue("$name has a label", shows(node))
        }
    }

    /** docs/PRD.md M1: a past entry by a start and a duration, and one from 23:00 to 01:00. */
    @Test
    fun pastEntriesByDurationAndAcrossMidnightReachTheServer() {
        val today = ZonedDateTime.now(MADRID).toLocalDate()
        launch {
            addPastEntry("By duration", start = "930", duration = "1,5h")
            addPastEntry("Across midnight", start = "23:00", end = "1:00")
            drainUntilEmpty()
        }

        val byDuration = serverEntry("By duration")
        assertEquals(at(today, 9, 30), instant(byDuration.string("start")))
        assertEquals(at(today, 11, 0), instant(byDuration.string("stop")))
        val acrossMidnight = serverEntry("Across midnight")
        assertEquals(at(today, 23, 0), instant(acrossMidnight.string("start")))
        assertEquals("it ends on the next day", at(today.plusDays(1), 1, 0), instant(acrossMidnight.string("stop")))
    }

    @Test
    fun aPastEntrySavedInAirplaneModeReachesTheServerWhenTheNetworkReturns() {
        val today = ZonedDateTime.now(MADRID).toLocalDate()
        launch {
            AirplaneMode.set(true, web)
            addPastEntry("Offline past entry", start = "8", end = "9:15")
            awaitShows("the history says the server lacks it") {
                composeRule.onNode(hasText("Offline past entry") and hasText(string(R.string.history_unsent), ignoreCase = true))
            }

            AirplaneMode.set(false, web)
            drainUntilEmpty()
        }

        val saved = serverEntry("Offline past entry")
        assertEquals(at(today, 8, 0), instant(saved.string("start")))
        assertEquals(at(today, 9, 15), instant(saved.string("stop")))
    }

    private fun launch(steps: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(NETWORK_TIMEOUT.toMillis())
            // The refresh the launch started, so the cache holds what the server has.
            runBlocking { app.accountSettings.refresh().join() }
            steps()
        }
    }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    /** Opens the form from the history row of the entry described as [description]. */
    private fun openEntry(description: String) {
        val row = hasText(description) and hasClickAction()
        composeRule.waitUntil("the entry shows in the history", NETWORK_TIMEOUT.toMillis()) { shows(composeRule.onNode(row)) }
        composeRule.onNode(row).performClick()
        awaitShows("the form opens") { field(R.string.entry_start) }
    }

    /**
     * Opens the form for a past entry from the Timer page, on today's date, and saves [description]
     * from [start] to [end], or for [duration].
     */
    private fun addPastEntry(description: String, start: String, end: String? = null, duration: String? = null) {
        composeRule.onNode(hasText(string(R.string.timer_add_entry)) and hasClickAction()).performClick()
        awaitShows("the form for a past entry opens") { composeRule.onNode(hasText(string(R.string.entry_form_new)) and isHeading()) }
        composeRule.waitUntil("the account's workspace is known", NETWORK_TIMEOUT.toMillis()) { field(R.string.entry_description).isEnabled() }
        field(R.string.entry_description).performTextInput(description)
        replace(R.string.entry_start, start)
        end?.let { replace(R.string.entry_end, it) }
        duration?.let { replace(R.string.entry_duration, it) }
        save(R.string.entry_form_new)
    }

    /** Taps Save, which queues the change at once and closes the form, titled [title]. */
    private fun save(@StringRes title: Int = R.string.entry_form_edit) {
        composeRule.onNode(hasText(string(R.string.entry_save)) and hasClickAction()).performScrollTo().performClick()
        val heading = hasText(string(title)) and isHeading()
        composeRule.waitUntil("the form closes within $AT_ONCE", AT_ONCE.toMillis()) {
            composeRule.onAllNodes(heading).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Puts [text] in the field labelled [label], in place of what it held. */
    private fun replace(@StringRes label: Int, text: String) {
        field(label).performScrollTo().performTextClearance()
        field(label).performTextInput(text)
    }

    private fun createEntry(description: String, start: Instant, stop: Instant): Long =
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", description)
                put("start", start.toString())
                put("stop", stop.toString())
            },
            JsonObject.serializer(),
        ).orFail("POST an entry").getValue("id").jsonPrimitive.long

    private fun createProject(name: String): Long =
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", name) },
            JsonObject.serializer(),
        ).orFail("POST a project").getValue("id").jsonPrimitive.long

    private fun field(@StringRes label: Int): SemanticsNodeInteraction = composeRule.onNode(isField(label))

    /** The text field labelled [label]. */
    private fun isField(@StringRes label: Int): SemanticsMatcher = hasSetTextAction() and hasText(string(label))

    private fun SemanticsNodeInteraction.isError(): Boolean =
        SemanticsProperties.Error in fetchSemanticsNode().config

    private fun SemanticsNodeInteraction.isEnabled(): Boolean =
        SemanticsProperties.Disabled !in fetchSemanticsNode().config

    /** The entry described as [description], as the server keeps it. */
    private fun serverEntry(description: String): JsonObject =
        web.get(web.server.api("me", "time_entries"), JsonArray.serializer()).orFail("GET /api/v9/me/time_entries")
            .map { it.jsonObject }
            .single { it.string("description") == description }

    private fun dateButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(R.string.entry_date_spoken, ""), substring = true) and hasClickAction())

    /**
     * The day [date] in Material's date picker, which shows its number and gives TalkBack the
     * whole date, such as "Saturday, October 17, 2026".
     */
    private fun dayInPicker(date: LocalDate): SemanticsNodeInteraction {
        val locale = context.resources.configuration.locales[0]
        val spoken = DateTimeFormatter.ofPattern("MMMM d, yyyy", locale).format(date)
        val day = hasText(date.dayOfMonth.toString()) or hasText(spoken, substring = true)
        return composeRule.onNode(day and hasClickAction() and hasAnyAncestor(isDialog()))
    }

    /** What TalkBack reads for the date button on [date]: Date: Mon, 19 Oct 2026. */
    private fun spokenDate(date: LocalDate): String {
        val weekday = context.resources.getStringArray(R.array.reports_weekdays_short)[date.dayOfWeek.value - 1]
        val month = context.resources.getStringArray(R.array.reports_months_short)[date.monthValue - 1]
        val value = string(R.string.entry_date_value, weekday, string(R.string.reports_date_year, date.dayOfMonth, month, date.year))
        return string(R.string.entry_date_spoken, value)
    }

    /** The project button, as TalkBack reads it for [name], or for no project. */
    private fun projectButton(name: String?): SemanticsNodeInteraction {
        val spoken = name?.let { string(R.string.entry_project_spoken, it) } ?: string(R.string.entry_no_project_spoken)
        return composeRule.onNode(hasContentDescription(spoken) and hasClickAction())
    }

    /** A row of the picker, scrolled into view. */
    private fun option(text: String): SemanticsNodeInteraction {
        val matcher = hasText(text) and hasClickAction()
        val list = hasScrollToNodeAction() and hasAnyDescendant(hasText(string(R.string.picker_project)))
        composeRule.onNode(list).performScrollToNode(matcher)
        return composeRule.onNode(matcher)
    }

    /** Whether exactly one node matches. */
    private fun shows(node: SemanticsNodeInteraction): Boolean =
        try {
            node.fetchSemanticsNode()
            true
        } catch (e: AssertionError) {
            false
        }

    private fun awaitShows(what: String, node: () -> SemanticsNodeInteraction) {
        composeRule.waitUntil("$what within $AT_ONCE", AT_ONCE.toMillis()) { shows(node()) }
    }

    /** Sends the queue until it is empty, as each change and refresh would, then refreshes. */
    private fun drainUntilEmpty() {
        val deadline = System.nanoTime() + NETWORK_TIMEOUT.toNanos()
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            if (System.nanoTime() > deadline) throw AssertionError("the queue is not empty")
            runBlocking { app.sender.send().join() }
        }
        runBlocking { app.accountSettings.refresh().join() }
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, minute), MADRID).toInstant()

    private fun instant(text: String): Instant = OffsetDateTime.parse(text).toInstant()

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        val AT_ONCE: Duration = Duration.ofSeconds(2)
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
