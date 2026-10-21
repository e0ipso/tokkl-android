package com.github.e0ipso.tokkl.entry

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.github.e0ipso.tokkl.TokklNavHost
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.trash.TrashViewModel
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
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
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
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
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import kotlinx.serialization.json.Json
import com.github.e0ipso.tokkl.time.SnappedSpan
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The entry form against the real Tokkl container: an entry the web app made, opened from the
 * history, edited and saved through the change queue (docs/PRD.md H2), also from an older day than
 * the phone keeps, and past entries added
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
    private var originalResolution: Long? = null

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    /** Two days ago in Madrid, inside the two weeks the phone keeps. */
    private val day: LocalDate = ZonedDateTime.now(MADRID).toLocalDate().minusDays(2)

    /** Twenty days ago in Madrid, before the two weeks the phone keeps, on the history's first older page. */
    private val olderDay: LocalDate = ZonedDateTime.now(MADRID).toLocalDate().minusDays(20)

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
            originalResolution?.let { setResolution(it) }
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

    /**
     * An entry from a day older than the cache opens from the history's older days, and its save
     * reaches the server. The refresh after the send drops it from the phone again, and the
     * history then shows the edit, not the older page's copy.
     */
    @Test
    fun anEntryFromAnOlderDayOpensAndTheHistoryShowsItsEditAfterTheRefresh() {
        val entry = createEntry("Old plan", at(olderDay, 9, 0), at(olderDay, 10, 0))
        launch {
            showOlderEntry(entry)
            openEntry("Old plan")
            field(R.string.entry_description).performTextClearance()
            field(R.string.entry_description).performTextInput("Old plan, fixed")
            replace(R.string.entry_end, "10:45")
            save()

            drainUntilEmpty()
            composeRule.waitUntil("the history shows the edit after the refresh", NETWORK_TIMEOUT.toMillis()) {
                shows(composeRule.onNode(hasText("Old plan, fixed") and hasClickAction()))
            }
            assertTrue("the refresh dropped it from the phone", runBlocking { app.cache.entries.first() }.none { it.entry.id == entry })
            assertFalse("the server has it", shows(composeRule.onNode(hasText(string(R.string.history_unsent), ignoreCase = true))))
            assertFalse("the older page's copy is gone", shows(composeRule.onNode(hasText("Old plan") and hasClickAction())))
        }

        val saved = web.get(web.server.api("me", "time_entries", entry.toString()), JsonObject.serializer()).orFail("GET the entry")
        assertEquals("Old plan, fixed", saved.string("description"))
        assertEquals(at(olderDay, 9, 0), instant(saved.string("start")))
        assertEquals(at(olderDay, 10, 45), instant(saved.string("stop")))
    }

    @Test
    fun anOfflineEditOfAnOlderEntryShowsAsNotSentAndReachesTheServerWhenTheNetworkReturns() {
        val entry = createEntry("Old call", at(olderDay, 14, 0), at(olderDay, 15, 0))
        launch {
            showOlderEntry(entry)
            AirplaneMode.set(true, web)
            openEntry("Old call")
            field(R.string.entry_description).performTextClearance()
            field(R.string.entry_description).performTextInput("Old call, noted")
            save()
            awaitShows("the history says the server lacks it") {
                composeRule.onNode(hasText("Old call, noted") and hasText(string(R.string.history_unsent), ignoreCase = true))
            }

            AirplaneMode.set(false, web)
            drainUntilEmpty()
        }

        val saved = web.get(web.server.api("me", "time_entries", entry.toString()), JsonObject.serializer()).orFail("GET the entry")
        assertEquals("Old call, noted", saved.string("description"))
        assertEquals(at(olderDay, 14, 0), instant(saved.string("start")))
        assertEquals(at(olderDay, 15, 0), instant(saved.string("stop")))
    }

    /**
     * An older entry's form opened on the phone's copy of an offline edit still saves once the send
     * and the refresh after it have dropped that copy, and the second edit reaches the server.
     */
    @Test
    fun anOlderEntryReopenedBeforeItsEditWasSentSavesAfterTheRefreshDropsIt() {
        val entry = createEntry("Old review", at(olderDay, 11, 0), at(olderDay, 12, 0))
        launch {
            showOlderEntry(entry)
            AirplaneMode.set(true, web)
            openEntry("Old review")
            replace(R.string.entry_description, "Old review, first")
            save()
            openEntry("Old review, first")

            AirplaneMode.set(false, web)
            drainUntilEmpty()
            assertTrue("the refresh dropped it from the phone", runBlocking { app.cache.entries.first() }.none { it.entry.id == entry })
            replace(R.string.entry_description, "Old review, second")
            save()
            drainUntilEmpty()
        }

        val saved = web.get(web.server.api("me", "time_entries", entry.toString()), JsonObject.serializer()).orFail("GET the entry")
        assertEquals("Old review, second", saved.string("description"))
        assertEquals(at(olderDay, 11, 0), instant(saved.string("start")))
        assertEquals(at(olderDay, 12, 0), instant(saved.string("stop")))
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

    /**
     * Back on a phone closes the form at once and drops what it held unsaved, as the web app's
     * EntryEditor.tsx does on Escape, its close button or a click outside it: no question, no draft
     * kept. Nothing is queued, the server keeps the entry, and the entry opens again as it was.
     */
    @Test
    fun backOnAPhoneClosesTheFormAndDropsItsUnsavedEdits() {
        createEntry("Plan the sprint", at(day, 9, 0), at(day, 10, 0))
        launch { scenario ->
            openEntry("Plan the sprint")
            assertFalse("the form takes the screen, as on a phone", shows(listSwitch()))
            makeUnsavedEdits("Plan the sprint, half typed")
            hideKeyboard(scenario)
            pressBack(scenario)

            awaitFormCloses()
            assertDroppedWithoutAWord()
            openEntry("Plan the sprint")
            assertOpensAsItWas("Plan the sprint")
        }

        assertServerKept("Plan the sprint")
    }

    /**
     * On an expanded window, a tap on another entry in the history beside the form opens that
     * entry and drops what the open form held unsaved. The web app's EntryEditor.tsx closes on a
     * click outside it and drops its edits the same way, without a question or a kept draft.
     */
    @Test
    fun anotherEntryOpenedBesideTheHistoryDropsTheOpenFormsUnsavedEdits() {
        createEntry("Plan the sprint", at(day, 9, 0), at(day, 10, 0))
        createEntry("Write the notes", at(day, 11, 0), at(day, 12, 0))
        launchInAnExpandedWindow {
            openEntry("Plan the sprint")
            assertTrue("the form shows beside the history", shows(listSwitch()))
            makeUnsavedEdits("Plan the sprint, half typed")
            composeRule.onNode(historyRow("Write the notes")).performClick()

            awaitShows("the other entry opens in the form") { composeRule.onNode(formDescription() and hasText("Write the notes")) }
            assertDroppedWithoutAWord()
            composeRule.onNode(historyRow("Plan the sprint")).performClick()
            assertOpensAsItWas("Plan the sprint")
        }

        assertServerKept("Plan the sprint")
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

    /** Typed spans use the current server contract, independently of the blocked short Stop API. */
    @Test
    fun typedCreateAndEditPredictionsMatchLatestServerAcrossAccountZonesAndDst() {
        setResolution(60)
        val cases = listOf(
            // Madrid: 80 minutes stores 60, not the old ceiling-rounded 120.
            listOf("Europe/Madrid", "2026-10-14", "14:00", "15:20", "2026-10-14T12:00:00Z", "2026-10-14T13:00:00Z"),
            listOf("Asia/Kathmandu", "2026-10-14", "10:05", "10:45", "2026-10-14T04:15:00Z", "2026-10-14T05:15:00Z"),
            // Lord Howe's spring gap leaves a 90-minute span between adjacent hourly grid points.
            listOf("Australia/Lord_Howe", "2026-10-04", "01:40", "03:10", "2026-10-03T14:30:00Z", "2026-10-03T16:00:00Z"),
        )
        for ((index, case) in cases.withIndex()) {
            val changed = web.send("PUT", web.server.api("me"), buildJsonObject { put("timezone", case[0]) }, JsonObject.serializer()).orFail("set account zone")
            runBlocking { app.accountSettings.refreshAfterChange().join() }
            assertEquals("the refreshed account zone", changed.string("timezone"), app.accountSettings.state.value!!.timeZone)
            val time = app.accountSettings.state.value!!.time()!!
            val date = LocalDate.parse(case[1])
            val raw = resolveRange(date, case[2], case[3], time, false, 0)!!
            val expected = SnappedSpan(Instant.parse(case[4]), Instant.parse(case[5]))
            assertEquals("local create prediction", expected, time.snapUnsent(raw.start, raw.stop))
            val description = "Typed snap $index"
            withModel { model ->
                model.new(date)
                model.state.first { it is EntryFormState.Open }
                model.setDescription(description)
                model.setStart(case[2])
                model.setEnd(case[3])
                model.timesDone()
                model.save()
                model.state.first { it == EntryFormState.Saved }
            }
            drainUntilEmpty()
            val created = serverEntry(description, date)
            assertEquals(expected.start, instant(created.string("start")))
            assertEquals(expected.stop, instant(created.string("stop")))
            val id = created.getValue("id").jsonPrimitive.long
            withModel { model ->
                model.edit(id, Json { ignoreUnknownKeys = true }.decodeFromJsonElement(ServerTimeEntry.serializer(), created).toCached())
                model.state.first { it is EntryFormState.Open }
                model.setDescription("$description edited")
                model.setStart(case[2])
                model.setDuration(Duration.between(raw.start, raw.stop).seconds.toString() + "s")
                model.durationDone()
                model.durationDone()
                model.save()
                model.state.first { it == EntryFormState.Saved }
            }
            drainUntilEmpty()
            val edited = web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer()).orFail("GET edited span")
            assertEquals("edit start", expected.start, instant(edited.string("start")))
            assertEquals("edit stop", expected.stop, instant(edited.string("stop")))
        }
    }

    @Test
    fun shortTypedSpansShowValidationAndCreateNoPendingChange() {
        setResolution(60)
        createEntry("Short typed edit", at(day, 8, 0), at(day, 9, 0))
        launch {
            openEntry("Short typed edit")
            replace(R.string.entry_end, "08:10")
            composeRule.onNode(hasText(string(R.string.entry_save)) and hasClickAction()).performScrollTo().performClick()
            awaitShows("the end explains the short span") {
                composeRule.onNode(isField(R.string.entry_end) and hasText(string(R.string.entry_mistake_too_short)))
            }
            assertTrue(field(R.string.entry_end).isError())
            assertEquals("no pending edit", 0, runBlocking { app.changes.unsentCount.first() })
        }
        withModel { model ->
            model.new(day)
            model.state.first { it is EntryFormState.Open }
            model.setStart("12:00")
            model.setDuration("10m")
            model.durationDone()
            model.save()
            model.state.first { it is EntryFormState.Open && it.mistakes == mapOf(TimeField.DURATION to TimeMistake.TOO_SHORT) }
        }
        assertEquals("no pending create", 0, runBlocking { app.changes.unsentCount.first() })
    }

    @Test
    fun aLegacyZeroLengthEntryKeepsItsTimesOnADescriptionEdit() {
        setResolution(60)
        // Latest Tokkl can restore a short timer from trash as a legacy zero-length entry.
        // This disposable fixture uses the server's own clock, not Android's Stop implementation.
        val running = web.send("POST", web.server.api("workspaces", workspace.toString(), "time_entries"), buildJsonObject {
            put("workspace_id", workspace)
            put("created_with", "typed snap fixture")
            put("description", "Legacy zero")
            put("start", Instant.now().toString())
            put("duration", -1)
        }, JsonObject.serializer()).orFail("create fixture timer")
        val id = running.getValue("id").jsonPrimitive.long
        web.send("PATCH", web.server.api("workspaces", workspace.toString(), "time_entries", id.toString(), "stop"), buildJsonObject {}, JsonObject.serializer()).orFail("stop fixture timer")
        val legacy = web.send("POST", web.server.app("time_entries", id.toString(), "restore"), buildJsonObject {}, JsonObject.serializer()).orFail("restore legacy fixture")
        assertEquals(legacy.string("start"), legacy.string("stop"))
        runBlocking { app.accountSettings.refresh().join() }
        withModel { model ->
            model.edit(id)
            model.state.first { it is EntryFormState.Open }
            model.setDescription("Legacy zero renamed")
            model.save()
            model.state.first { it == EntryFormState.Saved }
        }
        drainUntilEmpty()
        val saved = web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer()).orFail("GET legacy entry")
        assertEquals("Legacy zero renamed", saved.string("description"))
        assertEquals(legacy.string("start"), saved.string("start"))
        assertEquals(legacy.string("stop"), saved.string("stop"))
    }

    private fun setResolution(minutes: Long) {
        val admin = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken), httpClient = OkHttpClient())
        val url = admin.server.app("admin", "settings")
        if (originalResolution == null) originalResolution = admin.get(url, JsonObject.serializer()).orFail("GET settings").getValue("time_resolution_minutes").jsonPrimitive.long
        admin.send("PUT", url, buildJsonObject { put("time_resolution_minutes", minutes) }, JsonObject.serializer()).orFail("set resolution")
        runBlocking { app.accountSettings.refreshAfterChange().join() }
    }

    /** The actual form and Room queue, with collection and handlers on the main dispatcher. */
    private fun withModel(steps: suspend (EntryFormModel) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val model = EntryFormModel(app.cache.entries, app.cache.projects, app.cache.tasks, app.accountSettings.state, app.changes, scope)
        val collector = scope.launch { model.state.collect {} }
        try {
            withTimeout(NETWORK_TIMEOUT.toMillis()) { withContext(Dispatchers.Main) { steps(model) } }
        } finally {
            collector.cancel()
            scope.cancel()
        }
    }

    private fun launch(steps: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.awaitTimerPage(NETWORK_TIMEOUT.toMillis())
            // The refresh the launch started, so the cache holds what the server has.
            runBlocking { app.accountSettings.refresh().join() }
            steps(scenario)
        }
    }

    /**
     * The app's navigation host, with MainActivity's view models, in a window forced to
     * [EXPANDED_WINDOW], where the entry form shows beside the Timer page's history. The phone's
     * own window is too narrow for that.
     */
    private fun launchInAnExpandedWindow(steps: () -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.enableEdgeToEdge(activity.window)
                activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                val models = ViewModelProvider(activity)
                val history = models[HistoryViewModel::class.java]
                val timer = models[TimerViewModel::class.java]
                activity.setContent {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(EXPANDED_WINDOW)) {
                        TokklTheme(darkTheme = false) {
                            TokklNavHost(
                                signInModel = models[SignInViewModel::class.java].model,
                                themeSettings = app.themeSettings,
                                signOut = app.signOutModel,
                                sessionTrust = app.sessionTrust,
                                reports = models[ReportsViewModel::class.java].model,
                                history = history.model,
                                entryActions = history.actions,
                                calendar = models[CalendarViewModel::class.java].model,
                                projects = models[ProjectsViewModel::class.java].model,
                                clients = models[ClientsViewModel::class.java].model,
                                tags = models[TagsViewModel::class.java].model,
                                timerView = app.timerView,
                                timer = timer.model,
                                entryForm = models[EntryFormViewModel::class.java].model,
                                cache = app.cache,
                                trash = models[TrashViewModel::class.java].model,
                                capabilities = app.capabilities.state,
                                favorites = timer.favorites,
                            )
                        }
                    }
                }
            }
            composeRule.awaitTimerPage(NETWORK_TIMEOUT.toMillis())
            // MainActivity refreshes on sign-in; this host does not, so the test fills the cache.
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
        val row = historyRow(description)
        composeRule.waitUntil("the entry shows in the history", NETWORK_TIMEOUT.toMillis()) { shows(composeRule.onNode(row)) }
        composeRule.onNode(row).performClick()
        awaitShows("the form opens") { field(R.string.entry_start) }
    }

    /** Scrolls the history to its end until the older day of the entry [entryId] has loaded and shows it. */
    private fun showOlderEntry(entryId: Long) {
        val list = composeRule.onNode(hasScrollToKeyAction())
        composeRule.waitUntil("the older day loads", NETWORK_TIMEOUT.toMillis()) {
            list.performScrollToKey(OLDER_KEY)
            try {
                list.performScrollToKey("entry-$entryId")
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }
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

    /**
     * The history row of the entry described as [description]. The form's description field holds
     * the same text and takes clicks too, so the row is what is clickable and not a text field.
     */
    private fun historyRow(description: String): SemanticsMatcher = hasText(description) and hasClickAction() and !hasSetTextAction()

    /** The Timer page's List and Calendar switch, which shows only while the page does. */
    private fun listSwitch(): SemanticsNodeInteraction = composeRule.onNode(hasText(string(R.string.timer_view_list)) and isSelectable())

    /**
     * In the form, not the timer beside it, which has a description field and billable too. The
     * form is the scrolling column with Save in it.
     */
    private fun inForm(): SemanticsMatcher = hasAnyAncestor(hasScrollAction() and hasAnyDescendant(hasText(string(R.string.entry_save))))

    private fun formDescription(): SemanticsMatcher = isField(R.string.entry_description) and inForm()

    private fun formBillable(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.entry_billable)) and isToggleable() and inForm()).performScrollTo()

    /**
     * Changes the open form's description to [description] and hands it to the form with the
     * keyboard's Done, and turns billable on, without saving either.
     */
    private fun makeUnsavedEdits(description: String) {
        composeRule.onNode(formDescription()).performScrollTo().performTextClearance()
        composeRule.onNode(formDescription()).performTextInput(description)
        composeRule.onNode(formDescription()).performImeAction()
        formBillable().performClick()
        awaitShows("the form holds the edits") { composeRule.onNode(formDescription() and hasText(description)) }
        formBillable().assertIsOn()
    }

    /** Waits until the edit form's heading is gone. */
    private fun awaitFormCloses() {
        val heading = hasText(string(R.string.entry_form_edit)) and isHeading()
        composeRule.waitUntil("the form closes within $AT_ONCE", AT_ONCE.toMillis()) {
            composeRule.onAllNodes(heading).fetchSemanticsNodes().isEmpty()
        }
    }

    /** No dialog asked about the unsaved edits, and nothing went to the queue. */
    private fun assertDroppedWithoutAWord() {
        composeRule.waitForIdle()
        assertTrue("no dialog asks about the edits", composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty())
        assertEquals("nothing queued", 0, runBlocking { app.changes.unsentCount.first() })
    }

    /** The open form shows the entry described as [description] as the server has it, unbillable. */
    private fun assertOpensAsItWas(description: String) {
        awaitShows("the form shows the entry as it was") { composeRule.onNode(formDescription() and hasText(description)) }
        formBillable().assertIsOff()
    }

    /** The server still has the entry described as [description], unbillable. */
    private fun assertServerKept(description: String) {
        assertFalse("the server's entry stays unbillable", serverEntry(description).getValue("billable").jsonPrimitive.boolean)
    }

    /** Hides the keyboard and waits until it is gone, so the next Back reaches the app. */
    private fun hideKeyboard(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
        composeRule.waitUntil("the keyboard hides", NETWORK_TIMEOUT.toMillis()) {
            var shown = true
            scenario.onActivity { shown = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) ?: false }
            !shown
        }
    }

    /** Presses the system Back key once the app's window has input focus, as BottomBarTest does. */
    private fun pressBack(scenario: ActivityScenario<MainActivity>) {
        composeRule.waitUntil("the app window has input focus", NETWORK_TIMEOUT.toMillis()) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /** The entry described as [description], as the server keeps it. */
    private fun serverEntry(description: String, date: LocalDate? = null): JsonObject {
        val url = web.server.api("me", "time_entries").newBuilder().apply {
            if (date != null) {
                addQueryParameter("start_date", date.minusDays(2).toString() + "T00:00:00Z")
                addQueryParameter("end_date", date.plusDays(2).toString() + "T00:00:00Z")
            }
        }.build()
        return web.get(url, JsonArray.serializer()).orFail("GET /api/v9/me/time_entries")
            .map { it.jsonObject }
            .single { it.string("description") == description }
    }

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

        /** A tablet held sideways, an expanded width (docs/PRD.md, Devices). */
        val EXPANDED_WINDOW = DpSize(1280.dp, 800.dp)

        /** The key of the history's row after the days, HistoryList's OLDER_KEY. */
        const val OLDER_KEY = "older"
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
