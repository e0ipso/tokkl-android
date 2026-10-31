package com.github.e0ipso.tokkl.widget

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TapToVisibleDeadline
import com.github.e0ipso.tokkl.TimerActionToasts
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitControls
import com.github.e0ipso.tokkl.followNoTimerEvents
import com.github.e0ipso.tokkl.timerState
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.notification.stopAction
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.tile.QuickSettingsTile
import com.github.e0ipso.tokkl.time.formatDuration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Real-server fixtures and native widget controls on API 31 and 36. No fake server or periodic widget work. */
class WidgetServerTest {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private var widget: NativeWidget? = null
    private val native get() = checkNotNull(widget)
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun prepareAccountAndWidget() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = account.workspaceId
        signIn(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        RunningNotifications.allow()
        widget = NativeWidget()
        native.awaitText(context.getString(R.string.widget_idle))
        native.awaitDescription(context.getString(R.string.widget_start_description))
    }

    @After
    fun cleanUp() {
        try {
            QuickSettingsTile.collapse()
            QuickSettingsTile.remove()
            widget?.close()
        } finally {
            try {
                assertTrue("sign-out clears the account and queue", runBlocking { app.signOut.run() })
                signIn(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun startAndStopWorkOfflineThroughTheNativeControls() {
        signIn(NO_ROUTE, account.apiToken)
        val queued = unsentCount()
        native.click(context.getString(R.string.widget_start_description))
        val entry = runBlocking { withTimeout(1_000) { app.cache.running.filterNotNull().first().entry } }
        native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
        assertEquals("the app's default workspace", workspace, entry.workspaceId)
        assertEquals("a blank Start has the app's empty fields", "", entry.description)
        assertEquals(queued + 1, unsentCount())

        native.click(context.getString(R.string.widget_stop_description))
        runBlocking { withTimeout(1_000) { app.cache.running.first { it == null } } }
        native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        assertEquals(queued + 2, unsentCount())
        val kept = runBlocking { app.cache.timeEntries.first() }.singleOrNull { it.id == entry.id }
        val undo = runBlocking { app.changes.shortStopUndo.first() }
        if (undo == null) {
            assertNotNull("a full-span Stop keeps the local entry", kept)
            assertNotNull("the local entry was stopped", kept!!.stop)
        } else {
            assertEquals("the native Stop targets this entry's Undo", entry.id, undo.entryId)
            assertEquals("a short Stop saves a zero span", undo.entry.start, undo.entry.stop)
            assertTrue("the short Stop removes the entry from history", kept == null)
        }
    }

    @Test
    fun pauseResumeAndStopWhilePausedGoToTheServerFromTheNativeControls() {
        val first = account.createEntry("Paused on the widget", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS))
        runBlocking { app.accountSettings.refresh().join() }
        native.awaitText("Paused on the widget")

        native.click(pauseLabel)
        val paused = app.awaitControls<TimerControls.Paused>("the widget's Pause reached the server")
        native.awaitDescription(context.getString(R.string.widget_resume_description))
        native.awaitText(context.getString(R.string.widget_paused_entry, "Paused on the widget"))
        native.awaitText(formatDuration(paused.seconds))
        val chain = checkNotNull(web.timerState().pause)
        assertTrue("the server holds the chain paused", chain.paused)
        assertEquals("the widget shows the server's frozen total", chain.seconds, paused.seconds)
        assertNull("nothing runs while paused", runBlocking { app.cache.running.first() })

        native.click(context.getString(R.string.widget_resume_description))
        val resumed = app.awaitControls<TimerControls.Running>("the widget's Resume started the clone") { it.accumulatedSeconds == paused.seconds }
        native.awaitDescription(pauseLabel)
        assertNotEquals("Resume starts a new entry", first, resumed.entry.id)
        assertEquals("the server's clone", web.timerState().pause?.cloneEntryId, resumed.entry.id)
        assertEquals("Paused on the widget", resumed.entry.description)

        native.click(pauseLabel)
        app.awaitControls<TimerControls.Paused>("the widget's second Pause")
        // A Stop drawn for the running clone acts on nothing once it is paused, so wait for the redraw.
        native.awaitDescription(context.getString(R.string.widget_resume_description))
        native.click(context.getString(R.string.widget_stop_description))
        app.awaitControls<TimerControls.Idle>("the widget's Stop while paused")
        native.awaitDescription(context.getString(R.string.widget_start_description))
        assertNull("Stop while paused ended the chain on the server", web.timerState().pause)
        assertEquals("nothing went through the queue", 0, unsentCount())
    }

    @Test
    fun anOfflinePauseSaysWhyAndKeepsTheTimerWhileStopStillQueues() {
        val id = account.createEntry("Paused offline", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS))
        runBlocking { app.accountSettings.refresh().join() }
        native.awaitDescription(pauseLabel)
        AirplaneMode.set(true, web)
        try {
            TimerActionToasts.await(TimerActionToasts.offline) { native.click(pauseLabel) }
            val shown = runBlocking { app.timerControls.first() } as TimerControls.Running
            assertEquals("the timer runs on", id, shown.entry.id)
            native.awaitDescription(pauseLabel)
            assertEquals("Pause queues nothing", 0, unsentCount())

            native.click(context.getString(R.string.widget_stop_description))
            runBlocking { withTimeout(1_000) { app.cache.running.first { it == null } } }
            native.awaitDescription(context.getString(R.string.widget_start_description))
            assertEquals("Stop of the running entry queues offline", 1, unsentCount())
        } finally {
            AirplaneMode.set(false, web)
        }
    }

    @Test
    fun favoritesResizeAndEachNativeTapQueuesAllItsFieldsOffline() {
        val project = web.send("POST", web.server.api("workspaces", "$workspace", "projects"),
            buildJsonObject { put("name", "Widget project"); put("billable", true) }, JsonObject.serializer()).orFail("create project")["id"]!!.jsonPrimitive.long
        val task = web.send("POST", web.server.api("workspaces", "$workspace", "projects", "$project", "tasks"),
            buildJsonObject { put("name", "Widget task") }, JsonObject.serializer()).orFail("create task")["id"]!!.jsonPrimitive.long
        val favorites = (1..3).map { index ->
            Json.parseToJsonElement("""{"description":"Favorite $index","project_id":$project,"task_id":$task,"tags":["widget"],"billable":true}""")
        }
        web.sendNoContent("POST", web.server.api("me", "preferences"), buildJsonObject {
            put("favorites", kotlinx.serialization.json.JsonArray(favorites))
        }).orFail("save favorites")
        runBlocking { app.accountSettings.refresh().join() }
        native.awaitText("Favorite 3")


        native.resize(360, 152)
        native.await("the strip leaves templates out") { (1..3).none { native.hasText("Favorite $it") } }
        native.resize(298, 222)
        native.awaitText("Today")
        native.await("the medium widget leaves templates out") { (1..3).none { native.hasText("Favorite $it") } }
        native.resize(375, 338)
        native.awaitText("Favorite 3")
        signIn(NO_ROUTE, account.apiToken)
        for (index in 1..3) {
            // Resizing and the preceding Start can enqueue redraws after text first appears.
            // Settle that fixture work before the next tap's one-second deadline starts.
            native.awaitUpdatesSettled()
            val queued = unsentCount()
            native.click("Start Favorite $index, Widget project · Widget task")
            val running = runBlocking { withTimeout(1_000) { app.cache.running.filterNotNull().first { it.entry.description == "Favorite $index" }.entry } }
            native.awaitText("Favorite $index", 1_000)
            assertEquals(project, running.projectId)
            assertEquals(task, running.taskId)
            assertEquals(listOf("widget"), running.tags)
            assertTrue(running.billable)
            assertTrue("the favorite uses the offline queue", unsentCount() > queued)
        }
        native.awaitDescription(pauseLabel)
        app.capabilities.forget()
        native.await("a server without favorites hides every favorite") { (1..3).none { native.hasDescription("Start Favorite $it, Widget project · Widget task") } }
        native.await("a server without the timer API hides Pause") { !native.hasDescription(pauseLabel) }
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        assertTrue("the cache still holds favorites, only the capability hid them", runBlocking { app.cache.favorites.first() }.isNotEmpty())
        assertTrue(runBlocking { app.signOut.run() })
        native.awaitDescription(context.getString(R.string.widget_sign_in))
        assertFalse(native.hasText("Favorite 3"))
    }

    @Test
    fun nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod() {
        assertEquals(0, native.provider.updatePeriodMillis)
        assertEquals(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL, native.provider.resizeMode)
        assertTrue(native.provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0)
        signIn(NO_ROUTE, account.apiToken)
        runBlocking { app.changes.start(workspace, EntryFields("A ticking widget")) }
        native.awaitText("A ticking widget")
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        // The active Glance composition can show Start's state before WidgetSync has
        // queued its own redraw. Finish those one-off setup updates before measuring ticks.
        val expected = runBlocking { app.widgetState.first() }.key()
        val store = WidgetStore(context)
        native.await("WidgetSync has queued the running state") { store.load() == expected }
        native.awaitUpdatesSettled()
        native.assertClockTicksWithoutUpdates()
        native.assertClockTicksWithoutUpdates(clockIndex = 1)
    }

    @Test
    fun billableDayClockAddsOnlyStoppedBillableTimeAndTicksNatively() {
        // An hour ago belongs to yesterday just after account midnight. These stopped
        // segments must belong to the same account day as the timer started below.
        val time = checkNotNull(app.accountSettings.state.value?.time())
        val start = time.instantAt(time.today(), 0)
        for ((billable, seconds) in listOf(true to 600L, false to 300L)) {
            web.send("POST", web.server.api("workspaces", "$workspace", "time_entries"), buildJsonObject {
                put("workspace_id", workspace); put("created_with", "tokkl-android tests")
                put("description", "Stopped $billable"); put("billable", billable)
                put("start", start.toString()); put("stop", start.plusSeconds(seconds).toString())
            }, JsonObject.serializer()).orFail("seed stopped segment")
        }
        runBlocking { app.accountSettings.refreshAfterChange().join() }
        signIn(NO_ROUTE, account.apiToken)
        runBlocking { app.changes.start(workspace, EntryFields("Billable ticking", billable = true)) }
        native.awaitText("Billable ticking")
        native.await("three native clocks are drawn") { native.clockBaseValues().size == 3 }
        val bases = native.clockBaseValues()
        assertEquals("day total adds both stopped segments", 900_000.0, (bases[0] - bases[1]).toDouble(), 150.0)
        assertEquals("Billable adds only stopped billable segments", 600_000.0, (bases[0] - bases[2]).toDouble(), 150.0)
        native.awaitUpdatesSettled()
        native.assertClockTicksWithoutUpdates(clockIndex = 2)
    }

    @Test
    fun todayReportTapOpensReportsOnToday() {
        native.awaitDescription(context.getString(R.string.widget_open_report_description))
        native.click(context.getString(R.string.widget_open_report_description))
        compose.awaitText(context.getString(R.string.reports))
        compose.awaitText("Today")
    }

    @Test
    fun theWidgetFollowsTheAppNotificationAndTileWithinASecond() {
        signIn(NO_ROUTE, account.apiToken)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            compose.onNodeWithContentDescription(context.getString(R.string.timer_start_spoken)).performClick()
            native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
            RunningNotifications.awaitShown("the app start")
            RunningNotifications.shown()!!.notification.stopAction().actionIntent.send()
            native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)

            compose.onNodeWithContentDescription(context.getString(R.string.timer_start_spoken)).performClick()
            native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
            compose.onNodeWithContentDescription(context.getString(R.string.timer_stop_spoken)).performClick()
            native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        }
        val fields = EntryFields("Tile to widget")
        val tileEntry = runBlocking { app.changes.start(workspace, fields) }
        // Continue needs a retained stopped row. The prior immediate Stops exercise Trash above.
        runBlocking {
            val running = checkNotNull(app.cache.running.first()).entry
            assertEquals(tileEntry, running.id)
            // Two hours spans even the server's coarsest allowed 60-minute grid.
            val agedStart = running.start.minusSeconds(7_200)
            assertTrue(app.changes.update(tileEntry, fields, agedStart, null))
            withTimeout(1_000) { app.cache.running.first { it?.entry?.start == agedStart } }
        }
        native.awaitText("Tile to widget")
        QuickSettingsTile.add()
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("tile ready", "Tile to widget")
        QuickSettingsTile.click()
        native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        QuickSettingsTile.awaitText("stopped", context.getString(R.string.tile_continue))
        QuickSettingsTile.click()
        native.awaitText("Tile to widget", 1_000)
        native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
    }

    @Test
    fun widgetStartReachesTheRealServerAndStopSendsAnExistingLongEntry() {
        native.click(context.getString(R.string.widget_start_description))
        // The broadcast runs asynchronously. Wait for its commit before asking the sender to drain.
        runBlocking { withTimeout(1_000) { app.cache.running.filterNotNull().first() } }
        drain()
        val current = web.get(web.server.api("me", "time_entries", "current"), JsonObject.serializer()).orFail("read widget start")
        assertEquals("", current["description"]!!.jsonPrimitive.content)
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        // Use an entry old enough for the existing Stop API. Exact captured short Stop is TKA-0259.
        web.send("POST", web.server.api("workspaces", "$workspace", "time_entries"), buildJsonObject {
            put("workspace_id", workspace); put("created_with", "tokkl-android tests")
            put("description", "Long entry stopped by widget")
            put("start", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS).toString()); put("duration", -1)
        }, JsonObject.serializer()).orFail("seed long entry")
        // The sender's refresh may have read the earlier timer before this server write.
        runBlocking { app.accountSettings.refreshAfterChange().join() }
        val id = runBlocking { app.cache.running.filterNotNull().first().entry.id }
        native.awaitText("Long entry stopped by widget")
        native.click(context.getString(R.string.widget_stop_description))
        native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        drain()
        val entries = web.get(web.server.api("me", "time_entries"), ListSerializer(JsonObject.serializer())).orFail("read stopped entry")
        val stopped = entries.single { it["id"]!!.jsonPrimitive.long == id }
        assertTrue("the real server stored Stop", stopped["stop"] != null && stopped["stop"] != JsonNull)
    }

    @Test
    fun remoteStopClearsTheNativeWidgetAndNotificationAtThePeriodicCheck() {
        val id = account.createEntry("Stopped remotely", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS))
        runBlocking { app.accountSettings.refresh().join() }
        // Without the timer event stream, as on Toggl Track, only the periodic check learns of it.
        app.followNoTimerEvents()
        native.awaitText("Stopped remotely")
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        RunningNotifications.awaitShown("the seeded running entry")
        val workManager = WorkManager.getInstance(context)
        val check = runBlocking {
            withTimeout(60_000) {
                workManager.getWorkInfosForUniqueWorkFlow(RunningCheck.WORK_NAME).filterNotNull()
                    .first { it.singleOrNull()?.state == WorkInfo.State.ENQUEUED }.single()
            }
        }
        web.sendNoContent("PATCH", web.server.api("workspaces", "$workspace", "time_entries", "$id", "stop"), buildJsonObject {}).orFail("remote stop")
        assertNotNull("the phone has not refreshed", runBlocking { app.cache.running.first() })
        RequestCounter(TokklTestServer.url, "/api/v9/me/time_entries/current HTTP/1.1").use { requests ->
            signIn(requests.url, account.apiToken)
            val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
            driver.setInitialDelayMet(check.id)
            driver.setAllConstraintsMet(check.id)
            runBlocking { withTimeout(60_000) { app.cache.running.first { it == null } } }
            native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
            assertFalse(native.hasText("Stopped remotely"))
            RunningNotifications.awaitGone("the remotely stopped timer")
            assertEquals(1, requests.count)
            assertTrue(requests.failures.isEmpty())
            assertEquals(0, unsentCount())
        }
    }

    @Test
    fun widgetActionsDoNotWaitForANetworkThatNeverAnswers() {
        HangingRelay(TokklTestServer.url).use { relay ->
            signIn(relay.url, account.apiToken)
            relay.hang()
            native.awaitDescription(context.getString(R.string.widget_start_description))
            val started = TapToVisibleDeadline("widget Start")
            native.click(context.getString(R.string.widget_start_description))
            started.await("the cache runs") { runBlocking { app.cache.running.first() } != null }
            started.await("the native Stop control shows") {
                native.hasVisibleDescription(context.getString(R.string.widget_stop_description))
            }
            assertEquals("Start stays queued unsent", 1, unsentCount())
            runBlocking {
                withTimeout(60_000) {
                    while (relay.hungConnections == 0) kotlinx.coroutines.delay(20)
                }
            }
            assertTrue("a connection hangs before Stop", relay.hungConnections > 0)
            val stopped = TapToVisibleDeadline("widget Stop")
            native.click(context.getString(R.string.widget_stop_description))
            stopped.await("the cache stops") { runBlocking { app.cache.running.first() } == null }
            stopped.await("the native Start control shows") {
                native.hasVisibleDescription(context.getString(R.string.widget_start_description))
            }
            assertEquals(2, unsentCount())
            assertTrue("the sender still waits on the network", relay.hungConnections > 0)
        }
    }

    private val pauseLabel get() = context.getString(R.string.widget_pause_description)



    private fun drain() {
        runBlocking { app.sender.sendAndWait(); app.accountSettings.refresh().join() }
        assertEquals("every queued change reached the server", 0, unsentCount())
    }

    private fun unsentCount() = runBlocking { app.changes.unsentCount.first() }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    private companion object {
        val NO_ROUTE = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
