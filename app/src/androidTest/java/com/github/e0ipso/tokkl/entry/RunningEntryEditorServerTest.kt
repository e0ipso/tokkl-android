package com.github.e0ipso.tokkl.entry

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklNavHost
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.finishWithin
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TimerModel
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.trash.TrashViewModel
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Direct live canaries for the clock-to-running-editor route. Each test owns a real server
 * account. Forced widths use the production navigation and view models, as EntryFormServerTest
 * does; rotation uses MainActivity itself. The sole write barrier delegates to the real queue.
 */
class RunningEntryEditorServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private var originalResolution: Long? = null
    private var relay: HangingRelay? = null
    private var barrier: WriteBarrier? = null
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }
    // A stopped version of this fixture must remain inside the cache's history window.
    private val fixtureDay = ZonedDateTime.now(MADRID).toLocalDate().minusDays(2)
    private val fixtureStart get() = fixtureDay.atTime(13, 7, 41).atZone(MADRID).toInstant()

    @Before
    fun signIn() {
        account = TestAccounts.create(MADRID.id)
        workspace = web.get(web.server.api("me"), JsonObject.serializer()).orFail("GET fixture workspace")
            .getValue("default_workspace_id").jsonPrimitive.long
        signIn(TokklTestServer.url, account.apiToken)
        refresh()
        assertEquals("isolated account starts with an empty queue", 0, queued())
    }

    @After
    fun cleanUp() {
        try {
            barrier?.release()
            relay?.releaseAnswer()
            relay?.release()
            if (::account.isInitialized) drain()
        } finally {
            try {
                try {
                    originalResolution?.let { setResolution(it) }
                } finally {
                    signIn(TokklTestServer.url, TokklTestServer.apiToken)
                    // Successful DELETE removes this account, workspace and all its entries.
                    if (::account.isInitialized) TestAccounts.delete(account)
                }
            } finally {
                try {
                    relay?.close()
                } finally {
                    SessionRule.signOut()
                    assertTrue("fixture cache cleared", runBlocking { app.cache.entries.first() }.isEmpty())
                    assertEquals("fixture queue cleared", 0, queued())
                }
            }
        }
    }

    @Test
    fun compactListAndCalendarClocksOpenTheRunningEditor() {
        seedRunning("Compact running entry")
        withHost(412) {
            assertClockRoute(beside = false)
            selectCalendar()
            assertClockRoute(beside = false)
        }
    }

    @Test
    fun mediumListAndCalendarClocksOpenTheRunningEditor() {
        seedRunning("Medium running entry")
        withHost(720) {
            assertClockRoute(beside = false)
            selectCalendar()
            assertClockRoute(beside = false)
        }
    }

    @Test
    fun expandedListAndCalendarClocksKeepTheirExistingPaneRoutes() {
        seedRunning("Expanded running entry")
        withHost(1280) {
            assertClockRoute(beside = true)
            selectCalendar()
            assertClockRoute(beside = false)
        }
    }

    @Test
    fun cancelBackAndActualRotationsKeepOrDiscardTheRightDraft() {
        val id = seedRunning("Lifecycle original")
        withMain { scenario ->
            turn(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            openClock()
            draft("Cancel draft", fixtureDay.minusDays(1))
            tap(R.string.entry_cancel)
            awaitClosed()
            assertUnchanged(id, "Lifecycle original")

            openClock()
            assertOriginal("Lifecycle original")
            replace(R.string.entry_description, "Back draft")
            hideKeyboard(scenario)
            pressBack(scenario)
            awaitClosed()
            assertUnchanged(id, "Lifecycle original")

            openClock()
            draft("Rotated draft", fixtureDay.minusDays(1))
            billable().performClick()
            hideKeyboard(scenario)
            turn(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            assertDraft("Rotated draft", fixtureDay.minusDays(1))
            turn(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            assertDraft("Rotated draft", fixtureDay.minusDays(1))
            assertUnchanged(id, "Lifecycle original")
            tap(R.string.entry_cancel)
            awaitClosed()
            openClock()
            assertOriginal("Lifecycle original")
            billable().assertIsOff()
            tap(R.string.entry_cancel)
        }
    }

    @Test
    fun metadataSaveKeepsSecondsAndInvalidStartFlushesNothing() {
        val id = seedRunning("Metadata original")
        val project = web.send("POST", web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", "Running project") }, JsonObject.serializer()).orFail("create fixture project")
            .getValue("id").jsonPrimitive.long
        refresh()
        withMain {
            openClock()
            replace(R.string.entry_description, "Metadata saved")
            composeRule.onNode(hasContentDescription(string(R.string.entry_no_project_spoken)) and hasClickAction() and inForm())
                .performScrollTo().performClick()
            val projectRow = hasText("Running project") and hasClickAction()
            composeRule.onNode(hasScrollToNodeAction() and hasAnyDescendant(hasText(string(R.string.picker_project))))
                .performScrollToNode(projectRow)
            composeRule.onNode(projectRow).performClick()
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.picker_search))).performTextInput("running-tag")
            composeRule.onNode(hasText(string(R.string.picker_add_tag, "running-tag")) and hasClickAction()).performClick()
            composeRule.onNode(hasText(string(R.string.entry_picker_done)) and hasClickAction()).performClick()
            billable().performClick()
            save()
            drain()
            val saved = serverEntry(id)
            assertEquals("Metadata saved", saved.text("description"))
            assertEquals(project, saved.getValue("project_id").jsonPrimitive.long)
            assertEquals(listOf("running-tag"), saved.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
            assertTrue(saved.getValue("billable").jsonPrimitive.boolean)
            assertRunning(saved, id, fixtureStart)

            openClock()
            replace(R.string.entry_description, "Must not flush")
            replace(R.string.entry_start, "invalid")
            tap(R.string.entry_save)
            awaitUi("invalid running start is explained") {
                exists(field(R.string.entry_start, string(R.string.entry_mistake_times)))
            }
            assertTrue("start error exposed to accessibility", SemanticsProperties.Error in field(R.string.entry_start).fetchSemanticsNode().config)
            assertEquals("invalid Save queues no metadata", 0, queued())
            assertEquals(saved, serverEntry(id))
            tap(R.string.entry_cancel)
        }
    }

    @Test
    fun offlineMidnightCorrectionPersistsOnDiskAndRemapsOneRunningEntry() {
        setResolution(60)
        web.send("POST", web.server.api("me", "preferences"), buildJsonObject { put("timeofday_format", "h:mm A") },
            JsonObject.serializer()).orFail("set twelve-hour fixture preference")
        routeThroughRelay()
        assertTrue("account uses twelve-hour input", app.accountSettings.state.value!!.twelveHour)
        withMain { scenario ->
            relay!!.hang()
            timerDescription().performTextInput("Offline midnight")
            startButton().performTouchInput { click() }
            val local = awaitRunning("offline Start commits locally") { it.id < 0 }
            assertEquals("one unsent create before editing", 1, queued())
            val openedDay = local.start.atZone(MADRID).toLocalDate()
            val correctedDay = openedDay.minusDays(1)
            val corrected = correctedDay.atTime(23, 53).atZone(MADRID).toInstant()
            openClock()
            chooseDate(openedDay, correctedDay)
            replace(R.string.entry_start, "11:53pm")
            save()
            val cached = awaitRunning("unsnapped midnight correction commits") { it.start == corrected }
            assertEquals(local.id, cached.id)
            assertEquals(workspace, cached.workspaceId)
            assertEquals("create then one running UPDATE remain offline", 2, queued())
            assertElapsedFrom(corrected)
            // A separate Room connection proves committed persistence, not just the active model.
            val persisted = CacheDatabase.open(context)
            try {
                runBlocking(Dispatchers.IO) {
                    assertEquals(cached, persisted.changes().entry(local.id))
                    val pending = persisted.changes().changesFor(local.id)
                    assertEquals(listOf(ChangeKind.CREATE, ChangeKind.UPDATE), pending.map { it.kind })
                    assertEquals(corrected, pending.last().start)
                    assertEquals(null, pending.last().stop)
                }
            } finally {
                persisted.close()
            }
            finishWithin("recreate the offline activity") { scenario.recreate() }
            composeRule.awaitTimerPage(NETWORK_MS)
            openClock()
            assertTrue(field(R.string.entry_start).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.contains("11:53"))
            replace(R.string.entry_description, "Saved after remapping")
            // Keep this editor open on its local identity while CREATE receives the server id.
            relay!!.release()
            drain()
            val mapped = runBlocking { app.changes.currentId(local.id) }
            assertTrue("create received a server id", mapped > 0)
            val current = serverCurrent()
            assertRunning(current, mapped, corrected)
            assertEquals("draft metadata is still unsaved", "Offline midnight", current.text("description"))
            save()
            drain()
            assertRunning(serverEntry(mapped), mapped, corrected)
            assertEquals("Saved after remapping", serverEntry(mapped).text("description"))
            val serverRows = web.get(web.server.api("me", "time_entries"), JsonArray.serializer()).orFail("list fixture entries")
                .map { it.jsonObject }
            assertEquals("one server entry after reconnect", listOf(mapped), serverRows.map { it.getValue("id").jsonPrimitive.long })
            assertEquals("one cached running identity", listOf(mapped), runBlocking { app.cache.entries.first() }.filter { it.entry.stop == null }.map { it.entry.id })
            assertEquals("reconnect drains the queue", 0, queued())
        }
    }

    @Test
    fun clockWaitsForInitialStartAndPendingDescriptionWrites() {
        val writes = WriteBarrier(app.changes)
        barrier = writes
        withHost(412, writes) {
            timerDescription().performTextInput("Start boundary")
            startButton().performTouchInput { click() }
            awaitSignal(writes.startEntered)
            awaitUi("pending Start shows its clock") { exists(clock()) }
            clock().performTouchInput { click() }
            composeRule.waitForIdle()
            assertFalse("no editor while the initial local Start is held", formOpen())
            assertEquals("held Start has not written a row", 0, queued())
            writes.startRelease.complete(Unit)
            awaitEditor()
            assertTrue(exists(field(R.string.entry_description, "Start boundary")))
            tap(R.string.entry_cancel)
            awaitClosed()
            val running = awaitRunning("initial Start has an identity") { true }
            drain()
            val settledId = runBlocking { app.changes.currentId(running.id) }

            writes.holdUpdate = true
            timerDescription().performScrollTo().performTextClearance()
            timerDescription().performTextInput("Latest focused typing")
            // Do not press IME Done: tapping the actual clock must settle this focused text.
            clock().performTouchInput { click() }
            awaitSignal(writes.updateEntered)
            composeRule.waitForIdle()
            assertFalse("no editor while the description's local write is held", formOpen())
            assertEquals("cache still has the settled initial fields", "Start boundary", runBlocking { app.cache.running.first() }!!.entry.description)
            writes.updateRelease.complete(Unit)
            awaitEditor()
            assertTrue(exists(field(R.string.entry_description, "Latest focused typing")))
            assertEquals(settledId, runBlocking { app.cache.running.first() }!!.entry.id)
            tap(R.string.entry_cancel)
            drain()
            assertEquals("Latest focused typing", serverEntry(runBlocking { app.changes.currentId(running.id) }).text("description"))
            assertEquals("one running entry", 1, runBlocking { app.cache.entries.first() }.count { it.entry.stop == null })
        }
    }

    @Test
    fun aStopWhileTheEditorIsOpenCannotBeRevivedBySave() {
        val id = seedRunning("Stopped original")
        routeThroughRelay()
        withMain {
            openClock()
            replace(R.string.entry_description, "Stale stopped draft")
            val stop = fixtureStart.plusSeconds(3600)
            web.send("PATCH", web.server.api("workspaces", workspace.toString(), "time_entries", id.toString(), "stop"),
                buildJsonObject { put("stop", stop.toString()) }, JsonObject.serializer()).orFail("stop the captured fixture")
            refresh()
            assertNotNull(runBlocking { app.cache.entries.first() }.single { it.entry.id == id }.entry.stop)
            val stopped = serverEntry(id)
            relay!!.hang()
            tap(R.string.entry_save)
            awaitMissing()
            assertEquals("stale Save queues nothing", 0, queued())
            assertEquals(stopped, serverEntry(id))
            assertEquals("no cached timer was revived", null, runBlocking { app.cache.running.first() })
        }
    }

    @Test
    fun aPauseWhileTheEditorIsOpenCannotBeRevivedBySave() {
        val id = seedRunning("Paused original")
        routeThroughRelay()
        withMain {
            openClock()
            replace(R.string.entry_description, "Stale paused draft")
            relay!!.hang()
            val paused = runBlocking {
                val captured = app.changes.timerControls.first() as TimerControls.Running
                assertEquals(id, captured.entry.id)
                assertTrue(app.changes.pause(captured.target))
                app.changes.timerControls.first { it is TimerControls.Paused }
            }
            assertTrue(paused is TimerControls.Paused)
            assertEquals("only Pause's Stop waits offline", 1, queued())
            val stopped = runBlocking { app.cache.entries.first() }.single { it.entry.id == id }.entry
            assertNotNull("Pause durably stops the captured segment", stopped.stop)

            tap(R.string.entry_save)
            awaitMissing()

            assertEquals("stale Save adds no UPDATE or Start", 1, queued())
            assertEquals("stale Save leaves the paused chain intact", paused, runBlocking { app.changes.timerControls.first() })
            assertEquals(stopped, runBlocking { app.cache.entries.first() }.single { it.entry.id == id }.entry)
            assertEquals("no cached timer was revived", null, runBlocking { app.cache.running.first() })
            relay!!.release()
            drain()
            val sent = serverEntry(id)
            assertEquals("Paused original", sent.text("description"))
            assertEquals(stopped.stop, OffsetDateTime.parse(sent.text("stop")).toInstant())
            assertFalse("Pause remains stopped on the server", sent["stop"] is JsonNull)
            assertEquals("the paused segment is the only server entry", listOf(id),
                web.get(web.server.api("me", "time_entries"), JsonArray.serializer()).orFail("list paused fixture entries")
                    .map { it.jsonObject.getValue("id").jsonPrimitive.long })
            assertTrue(runBlocking { app.changes.timerControls.first() } is TimerControls.Paused)
        }
    }

    @Test
    fun aSameStartReplacementCannotReceiveTheOpenEditorsStaleSave() {
        val original = seedRunning("Replaced original")
        routeThroughRelay()
        withMain {
            openClock()
            replace(R.string.entry_description, "Stale replacement draft")
            val replacement = createRunning("Same-instant replacement", fixtureStart)
            assertNotEquals(original, replacement)
            refresh()
            awaitRunning("replacement reaches the cache") { it.id == replacement }
            val before = serverEntry(replacement)
            relay!!.hang()
            tap(R.string.entry_save)
            awaitMissing()
            assertEquals("stale Save queues nothing", 0, queued())
            assertEquals(before, serverEntry(replacement))
            assertRunning(serverCurrent(), replacement, fixtureStart)
            assertFalse("old segment stays stopped or absent", runBlocking { app.cache.entries.first() }.any { it.entry.id == original && it.entry.stop == null })
        }
    }

    /** Production navigation and view models in a size override, matching the existing adaptive fixture. */
    private fun withHost(width: Int, changes: EntryChanges? = null, steps: () -> Unit) {
        val scenario = finishWithin("launch sized navigation host") { ActivityScenario.launch(ComponentActivity::class.java) }
        try {
            scenario.onActivity { activity ->
                WindowCompat.enableEdgeToEdge(activity.window)
                activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                val models = ViewModelProvider(activity)
                val history = models[HistoryViewModel::class.java]
                val timer = models[TimerViewModel::class.java]
                val shownTimer = changes?.let {
                    TimerModel(app.changes.timerControls, app.cache.projects, app.cache.tasks, app.accountSettings.state, it, activity.lifecycleScope)
                } ?: timer.model
                activity.setContent {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(DpSize(width.dp, 900.dp))) {
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
                                timer = shownTimer,
                                entryForm = models[EntryFormViewModel::class.java].model,
                                cache = app.cache,
                                trash = models[TrashViewModel::class.java].model,
                                capabilities = app.capabilities.state,
                                settings = app.accountSettings.state,
                                favorites = timer.favorites,
                            )
                        }
                    }
                }
            }
            composeRule.awaitTimerPage(NETWORK_MS)
            steps()
        } finally {
            barrier?.release()
            finishWithin("close sized navigation host") { scenario.close() }
        }
    }

    private fun withMain(steps: (ActivityScenario<MainActivity>) -> Unit) {
        val scenario = finishWithin("launch MainActivity") { ActivityScenario.launch(MainActivity::class.java) }
        try {
            composeRule.awaitTimerPage(NETWORK_MS)
            steps(scenario)
        } finally {
            finishWithin("close MainActivity") { scenario.close() }
        }
    }

    private fun assertClockRoute(beside: Boolean) {
        openClock()
        field(R.string.entry_start).assertIsDisplayed()
        field(R.string.entry_end).assertDoesNotExist()
        field(R.string.entry_duration).assertDoesNotExist()
        composeRule.onNode(hasText(string(R.string.history_delete)) and hasClickAction() and inForm()).assertDoesNotExist()
        assertTrue("running description exists", exists(field(R.string.entry_description)))
        assertTrue("running date exists", exists(dateButton()))
        assertEquals("List shares a pane only at expanded width; Calendar uses its screen", beside,
            composeRule.onAllNodes(hasText(string(R.string.timer_view_list)) and isSelectable()).fetchSemanticsNodes().isNotEmpty())
        tap(R.string.entry_cancel)
        awaitClosed()
        assertEquals("opening and Cancel queue no change", 0, queued())
    }

    private fun selectCalendar() {
        val calendar = hasText(string(R.string.timer_view_calendar)) and isSelectable()
        composeRule.onNode(calendar).performScrollTo().performTouchInput { click() }
        awaitUi("Calendar is selected") { composeRule.onAllNodes(calendar and isSelected()).fetchSemanticsNodes().size == 1 }
    }

    private fun openClock() {
        awaitUi("labeled running clock is available") { exists(clock()) }
        clock().assertIsDisplayed().performTouchInput { click() }
        awaitEditor()
    }

    private fun clock(): SemanticsNodeInteraction = composeRule.onNode(
        hasContentDescription(string(R.string.timer_edit_running), substring = true) and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
    )

    private fun awaitEditor() = awaitUi("running editor opens") { formOpen() && exists(field(R.string.entry_start)) }
    private fun formOpen(): Boolean = composeRule.onAllNodes(hasText(string(R.string.entry_form_edit_running)) and isHeading()).fetchSemanticsNodes().size == 1
    private fun awaitClosed() = awaitUi("running editor closes back to the Timer page") {
        val title = hasText(string(R.string.entry_form_edit_running)) or hasText(string(R.string.entry_form_edit)) or hasText(string(R.string.entry_form_new))
        composeRule.onAllNodes(title and isHeading()).fetchSemanticsNodes().isEmpty() && exists(clock())
    }
    private fun awaitMissing() = awaitUi("stale running edit is rejected") { exists(composeRule.onNode(hasText(string(R.string.entry_form_missing)))) }
    private fun awaitUi(message: String, condition: () -> Boolean) = composeRule.waitUntil(message, AT_ONCE_MS, condition)

    private fun inForm(): SemanticsMatcher = hasAnyAncestor(hasScrollAction() and hasAnyDescendant(hasText(string(R.string.entry_save))))
    private fun field(@StringRes label: Int, value: String? = null): SemanticsNodeInteraction {
        val labeled = hasSetTextAction() and hasText(string(label)) and inForm()
        return composeRule.onNode(if (value == null) labeled else labeled and hasText(value))
    }
    private fun timerDescription(): SemanticsNodeInteraction = composeRule.onNode(hasSetTextAction() and hasText(string(R.string.entry_description)) and !inForm())
    private fun startButton(): SemanticsNodeInteraction = composeRule.onNode(hasContentDescription(string(R.string.timer_start_spoken)) and hasClickAction())
    private fun billable(): SemanticsNodeInteraction = composeRule.onNode(hasText(string(R.string.entry_billable)) and isToggleable() and inForm()).performScrollTo()
    private fun dateButton(): SemanticsNodeInteraction = composeRule.onNode(hasContentDescription(string(R.string.entry_date_spoken, ""), substring = true) and hasClickAction() and inForm())

    private fun replace(@StringRes label: Int, value: String) {
        field(label).performScrollTo().performTextClearance()
        field(label).performTextInput(value)
    }

    private fun tap(@StringRes label: Int) = composeRule.onNode(hasText(string(label)) and hasClickAction() and inForm()).performScrollTo().performClick()
    private fun save() {
        tap(R.string.entry_save)
        awaitClosed()
    }

    private fun draft(description: String, date: LocalDate) {
        replace(R.string.entry_description, description)
        replace(R.string.entry_start, "04:37")
        chooseDate(fixtureDay, date)
    }

    private fun assertDraft(description: String, date: LocalDate) {
        assertTrue(exists(field(R.string.entry_description, description)))
        assertEquals("04:37", field(R.string.entry_start).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertTrue(exists(composeRule.onNode(hasContentDescription(spokenDate(date)) and hasClickAction() and inForm())))
        billable().assertIsOn()
        assertEquals("rotation submits no update", 0, queued())
    }

    private fun assertOriginal(description: String) {
        assertTrue(exists(field(R.string.entry_description, description)))
        assertEquals("13:07", field(R.string.entry_start).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertTrue(exists(composeRule.onNode(hasContentDescription(spokenDate(fixtureDay)) and inForm())))
    }

    private fun chooseDate(opened: LocalDate, chosen: LocalDate) {
        dateButton().performScrollTo().performClick()
        if (chosen.month != opened.month || chosen.year != opened.year) {
            // This fixture moves only to yesterday, so at most one month boundary is crossed.
            composeRule.onNode(hasContentDescription("previous month", substring = true, ignoreCase = true) and hasClickAction()).performClick()
        }
        val locale = context.resources.configuration.locales[0]
        val spoken = DateTimeFormatter.ofPattern("MMMM d, yyyy", locale).format(chosen)
        val day = hasText(chosen.dayOfMonth.toString()) or hasText(spoken, substring = true)
        composeRule.onNode(day and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNode(hasText(string(R.string.entry_picker_done)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        awaitUi("chosen account date shows") { exists(composeRule.onNode(hasContentDescription(spokenDate(chosen)) and inForm())) }
    }

    private fun spokenDate(date: LocalDate): String {
        val weekday = context.resources.getStringArray(R.array.reports_weekdays_short)[date.dayOfWeek.value - 1]
        val month = context.resources.getStringArray(R.array.reports_months_short)[date.monthValue - 1]
        val value = string(R.string.entry_date_value, weekday, string(R.string.reports_date_year, date.dayOfMonth, month, date.year))
        return string(R.string.entry_date_spoken, value)
    }

    private fun turn(scenario: ActivityScenario<MainActivity>, requested: Int, orientation: Int) {
        composeRule.waitUntil("MainActivity is resumed before turning", ROTATION_MS) { resumedActivity() != null }
        val before = resumedActivity()!!
        if (before.resources.configuration.orientation == orientation) return
        scenario.onActivity { it.requestedOrientation = requested }
        composeRule.waitUntil("actual orientation recreation", ROTATION_MS) {
            resumedActivity()?.let { it !== before && it.resources.configuration.orientation == orientation } == true
        }
        composeRule.waitForIdle()
    }

    private fun resumedActivity(): MainActivity? {
        var resumed: MainActivity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        }
        return resumed
    }

    private fun hideKeyboard(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
        composeRule.waitUntil("keyboard hidden before Back or rotation", NETWORK_MS) {
            var shown = true
            scenario.onActivity { shown = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) ?: false }
            !shown
        }
    }

    private fun pressBack(scenario: ActivityScenario<MainActivity>) {
        composeRule.waitUntil("app has input focus before Back", NETWORK_MS) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun assertUnchanged(id: Long, description: String) {
        assertEquals("no unsaved edit queued", 0, queued())
        val original = serverEntry(id)
        assertEquals(description, original.text("description"))
        assertRunning(original, id, fixtureStart)
        val cached = runBlocking { app.cache.running.first() }!!.entry
        assertEquals(description, cached.description)
        assertEquals(fixtureStart, cached.start)
    }

    private fun assertElapsedFrom(start: Instant) {
        awaitUi("elapsed clock follows the corrected start") {
            val value = clock().fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            val parts = value.split(':').map { it.toLong() }
            parts.size == 3 && kotlin.math.abs(parts[0] * 3600 + parts[1] * 60 + parts[2] - Duration.between(start, Instant.now()).seconds) <= 1
        }
        val lower = Duration.between(start, Instant.now()).seconds
        val value = clock().fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        val parts = value.split(':').map { it.toLong() }
        assertEquals("elapsed clock has hours, minutes and seconds", 3, parts.size)
        val seconds = parts[0] * 3600 + parts[1] * 60 + parts[2]
        val upper = Duration.between(start, Instant.now()).seconds
        assertTrue("clock follows the corrected start: $value", seconds in (lower - 1)..(upper + 1))
    }

    private fun seedRunning(description: String): Long = createRunning(description, fixtureStart).also { refresh() }
    private fun createRunning(description: String, start: Instant): Long = web.send("POST",
        web.server.api("workspaces", workspace.toString(), "time_entries"), buildJsonObject {
            put("workspace_id", workspace)
            put("created_with", "running editor live canary")
            put("description", description)
            put("start", start.toString())
            put("duration", -1)
        }, JsonObject.serializer()).orFail("create running fixture").getValue("id").jsonPrimitive.long

    private fun assertRunning(entry: JsonObject, id: Long, start: Instant) {
        assertEquals(id, entry.getValue("id").jsonPrimitive.long)
        assertEquals(workspace, entry.getValue("workspace_id").jsonPrimitive.long)
        assertEquals(JsonNull, entry.getValue("stop"))
        assertEquals(start, OffsetDateTime.parse(entry.text("start")).toInstant())
    }

    private fun serverEntry(id: Long): JsonObject = web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer()).orFail("read fixture entry")
    private fun serverCurrent(): JsonObject = web.get(web.server.api("me", "time_entries", "current"), JsonObject.serializer()).orFail("read running fixture")
    private fun queued(): Int = runBlocking { app.changes.unsentCount.first() }
    private fun refresh() = runBlocking { withTimeout(NETWORK_MS) { app.accountSettings.refreshAfterChange().join() } }
    private fun drain() {
        runBlocking {
            withTimeout(NETWORK_MS) {
                while (app.changes.unsentCount.first() > 0) app.sender.sendAndWait()
                app.accountSettings.refreshAfterChange().join()
            }
        }
    }

    private fun awaitRunning(message: String, predicate: (CachedTimeEntry) -> Boolean): CachedTimeEntry = runBlocking {
        try {
            withTimeout(AT_ONCE_MS) { app.cache.running.first { it != null && predicate(it.entry) }!!.entry }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("$message within $AT_ONCE_MS ms", e)
        }
    }

    private fun setResolution(minutes: Long) {
        val admin = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken), httpClient = OkHttpClient())
        val url = admin.server.app("admin", "settings")
        if (originalResolution == null) originalResolution = admin.get(url, JsonObject.serializer()).orFail("read fixture grid")
            .getValue("time_resolution_minutes").jsonPrimitive.long
        admin.send("PUT", url, buildJsonObject { put("time_resolution_minutes", minutes) }, JsonObject.serializer()).orFail("set fixture grid")
        refresh()
    }

    private fun routeThroughRelay() {
        relay = HangingRelay(TokklTestServer.url)
        signIn(relay!!.url, account.apiToken)
        refresh()
    }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    private fun awaitSignal(signal: CompletableDeferred<Unit>) = runBlocking { withTimeout(AT_ONCE_MS) { signal.await() } }
    private fun exists(node: SemanticsNodeInteraction): Boolean = try { node.fetchSemanticsNode(); true } catch (_: AssertionError) { false }
    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Gates only a local-write boundary, then executes the real queue and returns its real identity. */
    private class WriteBarrier(private val real: EntryChanges) : EntryChanges by real {
        val startEntered = CompletableDeferred<Unit>()
        val startRelease = CompletableDeferred<Unit>()
        val updateEntered = CompletableDeferred<Unit>()
        val updateRelease = CompletableDeferred<Unit>()
        @Volatile var holdUpdate = false

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long {
            startEntered.complete(Unit)
            startRelease.await()
            return real.start(workspaceId, fields)
        }

        override suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean {
            if (holdUpdate) {
                updateEntered.complete(Unit)
                updateRelease.await()
            }
            return real.updateRunning(identity, fields, start)
        }

        fun release() {
            startRelease.complete(Unit)
            updateRelease.complete(Unit)
        }
    }

    private companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        const val AT_ONCE_MS = 2_000L
        const val ROTATION_MS = 15_000L
        const val NETWORK_MS = 60_000L
    }
}
