package com.github.e0ipso.tokkl.tile

import android.net.ConnectivityManager
import android.service.quicksettings.Tile
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.BlackholeDiagnostics
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TapToVisibleDeadline
import com.github.e0ipso.tokkl.TimerActionToasts
import com.github.e0ipso.tokkl.awaitControls
import com.github.e0ipso.tokkl.followNoTimerEvents
import com.github.e0ipso.tokkl.timerState
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.reachesInternet
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Quick Settings tile (docs/PRD.md N4) in the device's real SystemUI, against the real Tokkl
 * container. The tile is added first in the panel and tapped through the shell, as a user taps
 * it, and the panel is read through the accessibility tree. Starts and stops from other surfaces
 * go through [TokklApplication.changes], which the app, the notification and the widget all
 * write. Each test seeds its own account and deletes it after.
 */
class RunningTileServerTest {

    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private var project = 0L

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInAndAddTheTile() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = web.get(web.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long
        project = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", "Tile") },
            Created.serializer(),
        ).orFail("POST a project").id
        signIn(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        QuickSettingsTile.add()
    }

    @After
    fun cleanUp() {
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<TileControlsActivity>().forEach { it.finish() }
            }
            QuickSettingsTile.collapse()
            QuickSettingsTile.remove()
            // Waits for the network, which the account's deletion and the next test need.
            if (::account.isInitialized) AirplaneMode.set(false, web) else AirplaneMode.off()
        } finally {
            try {
                signIn(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun openingTheShadeFindsATimerStartedOnTheServer() {
        val remote = account.createEntry("Started on the laptop", Instant.now().minusSeconds(7_200))
        assertEquals("the app has not fetched the remote start", null, runBlocking { app.cache.running.first() })

        QuickSettingsTile.expand()

        QuickSettingsTile.awaitText("the remote timer without opening the app", "Started on the laptop")
        QuickSettingsTile.awaitText("the remote timer", context.getString(R.string.tile_running_subtitle))
        assertEquals(remote, runBlocking { app.cache.running.first() }!!.entry.id)
    }

    @Test
    fun openingTheShadeTwiceWithinAMinuteFetchesOnlyOnce() {
        account.createEntry("One shade refresh", Instant.now().minusSeconds(7_200))
        RequestCounter(TokklTestServer.url, CURRENT_REQUEST).use { requests ->
            signIn(requests.url, account.apiToken)
            QuickSettingsTile.expand()
            QuickSettingsTile.awaitText("the first refresh completed", "One shade refresh")
            QuickSettingsTile.collapse()
            QuickSettingsTile.expand()
            QuickSettingsTile.awaitText("the cached timer on reopening", "One shade refresh")
            QuickSettingsTile.collapse()

            assertEquals("two openings within a minute", 1, requests.count)
            assertEquals("all counted connections reached the real server", emptyList<String>(), requests.failures)
        }
    }

    @Test
    fun aTapDuringAHeldRefreshContinuesTheDisplayedEntryAtOnce() {
        account.createEntry("Continue while refresh hangs", Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600))
        runBlocking { app.accountSettings.refresh().join() }
        // Without the timer event stream, as on Toggl Track, only the held refresh would show the laptop's timer.
        app.followNoTimerEvents()
        account.createEntry("Laptop timer hidden by the held reply", Instant.now().minusSeconds(1_800))
        HangingRelay(TokklTestServer.url).use { relay ->
            RequestCounter(relay.url, CURRENT_REQUEST).use { requests ->
                signIn(requests.url, account.apiToken)
                relay.holdAnswerTo("GET /api/v9/me/time_entries/current ")
                QuickSettingsTile.expand()
                QuickSettingsTile.awaitText("the cached entry before the refresh answers", "Continue while refresh hangs")
                runBlocking { withTimeout(TIMEOUT_MS) { while (!relay.holdsAnswer) delay(20) } }
                QuickSettingsTile.collapse()
                QuickSettingsTile.expand()
                QuickSettingsTile.awaitText("the cached entry during the same refresh", "Continue while refresh hangs")
                assertEquals("reopening joins the in-flight fetch", 1, requests.count)

                QuickSettingsTile.click()

                val continued = runBlocking { withTimeout(AT_ONCE_MS) { app.cache.running.first { it != null } } }!!.entry
                assertEquals("the tap uses the displayed entry", "Continue while refresh hangs", continued.description)
                assertTrue("the tap did not wait for the current-entry reply", relay.holdsAnswer)
                QuickSettingsTile.awaitText("the immediate cached start", context.getString(R.string.tile_running_subtitle))
                relay.releaseAnswer()
            }
        }
    }

    @Test
    fun openingTheShadeOfflineAttemptsNoRequest() {
        // The timer event stream reconnects to the relay on its own; this counts only the tile's requests.
        app.followNoTimerEvents()
        BlackholeDiagnostics.begin("tile-offline")
        try {
            HangingRelay(TokklTestServer.url).use { relay ->
                // Both relay hops use loopback, so an attempted request is observable even offline.
                RequestCounter(relay.url, CURRENT_REQUEST).use { requests ->
                    BlackholeDiagnostics.mark("tile.hang.enter")
                    relay.hang()
                    BlackholeDiagnostics.mark("tile.hang.return")
                    // The system can bind the tile while airplane mode is still taking the network
                    // down, and an online refresh then is allowed. Only sign in to the counted
                    // server once the phone is offline, so every counted request is an offline one.
                    BlackholeDiagnostics.mark("tile.airplane.enter")
                    tileNetworkSnapshot()
                    AirplaneMode.set(true, web)
                    // A failed server probe can precede Android losing its default network.
                    // The tile uses that network state, so settle it before changing servers.
                    val connectivity = context.getSystemService(ConnectivityManager::class.java)
                    runBlocking {
                        withTimeout(TIMEOUT_MS) {
                            while (connectivity.reachesInternet()) delay(20)
                        }
                    }
                    BlackholeDiagnostics.mark("tile.airplane.return.web_network_failure")
                    tileNetworkSnapshot()
                    BlackholeDiagnostics.mark("tile.session.save.enter")
                    signIn(requests.url, account.apiToken)
                    BlackholeDiagnostics.mark("tile.session.save.return")
                    QuickSettingsTile.expand()
                    QuickSettingsTile.awaitText("the offline cache", context.getString(R.string.tile_nothing_subtitle))
                    QuickSettingsTile.collapse()
                    QuickSettingsTile.expand()
                    QuickSettingsTile.awaitText("the offline cache on reopening", context.getString(R.string.tile_nothing_subtitle))
                    QuickSettingsTile.collapse()

                    BlackholeDiagnostics.record("tile.pre_assert count=${requests.count} hung=${relay.hungConnections} relay_failures=${requests.failures.size}")
                    BlackholeDiagnostics.mark("tile.assertions")
                    tileNetworkSnapshot()
                    assertEquals("offline openings send no current-entry request", 0, requests.count)
                    assertEquals("offline openings attempt no loopback connection", 0, relay.hungConnections)
                    assertEquals(emptyList<String>(), requests.failures)
                }
            }
        } finally {
            BlackholeDiagnostics.finish()
        }
    }

    @Test
    fun aTapStopsTheRunningTimerWithTheNetworkDown() {
        AirplaneMode.set(true, web)
        val fields = EntryFields(description = "Stopped from the tile", projectId = project)
        val id = runBlocking { app.changes.start(workspace, fields) }
        ageRunning(id, fields)
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("the running entry", "Stopped from the tile")
        QuickSettingsTile.awaitText("the running entry", context.getString(R.string.tile_running_subtitle))
        val queued = unsentCount()

        QuickSettingsTile.click()

        runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it == null } } }
        val stopped = runBlocking { app.cache.timeEntries.first() }.single { it.id == id }
        assertNotNull("the tap stopped the entry the tile showed", stopped.stop)
        assertEquals("the stop waits in the queue", queued + 1, unsentCount())
        QuickSettingsTile.awaitText("the stopped timer", context.getString(R.string.tile_continue))
        QuickSettingsTile.awaitText("the stopped timer", "Stopped from the tile")
    }

    @Test
    fun aTapContinuesTheLastEntryWithTheNetworkDown() {
        AirplaneMode.set(true, web)
        // Equal starts must prefer the newest local queue ID, through the tile's real tap too.
        val start = Instant.parse("2026-10-19T08:00:00Z")
        val stop = start.plusSeconds(3600)
        runBlocking { app.changes.create(workspace, EntryFields(description = "Earlier entry"), start, stop) }
        val last = runBlocking {
            app.changes.create(workspace, EntryFields(description = "Continued from the tile", projectId = project, tags = listOf("tile")), start, stop)
        }
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("no timer runs", context.getString(R.string.tile_continue))
        QuickSettingsTile.awaitText("the entry a tap continues", "Continued from the tile")
        val queued = unsentCount()

        QuickSettingsTile.click()

        val running = runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it != null } } }!!.entry
        assertNotEquals("a new entry", last, running.id)
        assertEquals("Continued from the tile", running.description)
        assertEquals(project, running.projectId)
        assertEquals(listOf("tile"), running.tags)
        assertEquals("the start waits in the queue", queued + 1, unsentCount())
        QuickSettingsTile.awaitText("the continued entry", context.getString(R.string.tile_running_subtitle))
    }

    @Test
    fun longPressPausesResumesAndStopsWhilePausedOnTheServer() {
        val fields = EntryFields(description = "Long press controls", projectId = project)
        val first = account.createEntry(fields.description, Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS))
        runBlocking { app.accountSettings.refresh().join() }
        val press = openCompactControls(fields.description)

        press(R.string.timer_pause_spoken)
        val paused = app.awaitControls<TimerControls.Paused>("the long press Pause")
        assertEquals("the server's frozen total", checkNotNull(web.timerState().pause).seconds, paused.seconds)
        assertEquals("Pause stopped the entry on the server", null, runBlocking { app.cache.running.first() })

        press(R.string.timer_resume_spoken)
        val resumed = app.awaitControls<TimerControls.Running>("the long press Resume") { it.accumulatedSeconds == paused.seconds }
        assertNotEquals("Resume creates a new segment", first, resumed.entry.id)
        assertEquals("the server's clone", web.timerState().pause?.cloneEntryId, resumed.entry.id)
        assertEquals(fields.description, resumed.entry.description)

        press(R.string.timer_pause_spoken)
        app.awaitControls<TimerControls.Paused>("the second long press Pause")
        // A Stop drawn for the running clone acts on nothing once it is paused, so wait for the redraw.
        awaitControl(R.string.timer_resume_spoken)
        press(R.string.timer_stop_spoken)
        app.awaitControls<TimerControls.Idle>("the long press Stop while paused")
        assertEquals("Stop while paused ended the chain on the server", null, web.timerState().pause)
        assertEquals("nothing went through the queue", 0, unsentCount())
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Stop closes the compact dialog", device.wait(Until.gone(By.pkg(context.packageName)
            .desc(context.getString(R.string.timer_stop_spoken))), TIMEOUT_MS))
    }

    @Test
    fun anOfflineLongPressPauseSaysWhyAndStopStillQueues() {
        AirplaneMode.set(true, web)
        val fields = EntryFields(description = "Offline long press", projectId = project)
        val id = runBlocking { app.changes.start(workspace, fields) }
        ageRunning(id, fields)
        val press = openCompactControls(fields.description)
        val queued = unsentCount()

        TimerActionToasts.await(TimerActionToasts.offline) { press(R.string.timer_pause_spoken) }
        assertEquals("the timer runs on", id, (runBlocking { app.timerControls.first() } as TimerControls.Running).entry.id)
        assertEquals("Pause queues nothing", queued, unsentCount())

        press(R.string.timer_stop_spoken)
        runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it == null } } }
        assertNotNull(runBlocking { app.cache.timeEntries.first() }.single { it.id == id }.stop)
        assertEquals("Stop of the running entry waits in the queue", queued + 1, unsentCount())
    }

    @Test
    fun aTapOnThePausedTileResumesOnTheServerAndOfflineSaysWhy() {
        account.createEntry("Paused on the laptop", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS))
        runBlocking { app.accountSettings.refresh().join() }
        assertEquals("the server pauses it", TimerActionResult.Done, runBlocking { app.timerActions.pause() })
        val paused = app.awaitControls<TimerControls.Paused>("the server's Pause")
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("the paused timer", context.getString(R.string.tile_paused_subtitle))

        AirplaneMode.set(true, web)
        TimerActionToasts.await(TimerActionToasts.offline) { QuickSettingsTile.click() }
        assertEquals("the chain stays paused", paused, runBlocking { app.timerControls.first() })
        AirplaneMode.set(false, web)

        QuickSettingsTile.click()
        val resumed = app.awaitControls<TimerControls.Running>("the paused tile's Resume") { it.accumulatedSeconds == paused.seconds }
        assertEquals("the server's clone", web.timerState().pause?.cloneEntryId, resumed.entry.id)
        QuickSettingsTile.awaitText("the resumed timer", context.getString(R.string.tile_running_subtitle))
    }

    @Test
    fun stopAndContinueDoNotWaitForANetworkThatNeverAnswers() {
        HangingRelay(TokklTestServer.url).use { relay ->
            signIn(relay.url, account.apiToken)
            relay.hang()
            val fields = EntryFields(description = "The network never answers")
            val original = runBlocking { app.changes.start(workspace, fields) }
            ageRunning(original, fields)
            QuickSettingsTile.expand()
            QuickSettingsTile.awaitText("the running entry", "The network never answers")
            runBlocking {
                withTimeout(TIMEOUT_MS) {
                    while (relay.hungConnections == 0) delay(20)
                }
            }
            val queued = unsentCount()

            val stopped = TapToVisibleDeadline("tile Stop")
            QuickSettingsTile.click()
            stopped.await("the cache stops") { runBlocking { app.cache.running.first() } == null }
            stopped.await("the stopped timer shows") {
                QuickSettingsTile.showsVisibleText(context.getString(R.string.tile_continue))
            }
            assertEquals("Stop stays queued unsent", queued + 1, unsentCount())
            assertTrue("a connection still hangs before Continue", relay.hungConnections > 0)

            val started = TapToVisibleDeadline("tile Continue")
            QuickSettingsTile.click()
            started.await("the cache runs") { runBlocking { app.cache.running.first() } != null }
            started.await("the continued entry shows") {
                QuickSettingsTile.showsVisibleText(context.getString(R.string.tile_running_subtitle))
            }
            val continued = runBlocking { app.cache.running.first() }!!.entry
            assertNotEquals(original, continued.id)
            assertEquals("The network never answers", continued.description)
            assertEquals("both taps stay queued while the request hangs", queued + 2, unsentCount())
            assertTrue("the sender still waits on the network", relay.hungConnections > 0)
        }
    }

    @Test
    fun theTileFollowsStartsAndStopsFromOtherSurfaces() {
        AirplaneMode.set(true, web)
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("an account with no entries", context.getString(R.string.tile_nothing_subtitle))

        val firstFields = EntryFields(description = "Started in the app")
        val id = runBlocking { app.changes.start(workspace, firstFields) }
        ageRunning(id, firstFields)
        QuickSettingsTile.awaitText("a start while the panel shows", "Started in the app")
        QuickSettingsTile.awaitText("a start while the panel shows", context.getString(R.string.tile_running_subtitle))

        runBlocking { app.changes.stop(id) }
        QuickSettingsTile.awaitText("a stop while the panel shows", context.getString(R.string.tile_continue))

        QuickSettingsTile.collapse()
        val nextFields = EntryFields(description = "Started with the panel closed")
        val next = runBlocking { app.changes.start(workspace, nextFields) }
        ageRunning(next, nextFields)
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("a start while the panel was closed", "Started with the panel closed")
        QuickSettingsTile.awaitText("a start while the panel was closed", context.getString(R.string.tile_running_subtitle))

        QuickSettingsTile.collapse()
        runBlocking { app.changes.stop(next) }
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("a stop while the panel was closed", context.getString(R.string.tile_continue))
        QuickSettingsTile.awaitText("a stop while the panel was closed", "Started with the panel closed")
    }

    @Test
    fun runningAndNotDifferInLabelAndIconNotOnlyInColour() {
        // Keep this local fixture's ID stable while checking the label/icon rendering.
        AirplaneMode.set(true, web)
        val entry = runBlocking {
            val id = app.changes.start(workspace, EntryFields(description = "Looked at"))
            app.cache.running.first()!!.entry.also { assertEquals(id, it.id) }
        }
        val running = runningTileLook(RunningTileContent.Running(entry, TimerTarget.server(entryId = entry.id)), context)
        val stopped = runningTileLook(RunningTileContent.Continue(entry.copy(stop = entry.start.plusSeconds(60))), context)
        val nothing = runningTileLook(RunningTileContent.Nothing, context)
        val paused = runningTileLook(RunningTileContent.Paused("Looked at", TimerTarget.server(revision = 3)), context)

        assertEquals(Tile.STATE_ACTIVE, running.state)
        assertEquals("Looked at", running.label)
        assertEquals("Running · Hold for controls", running.subtitle)
        assertEquals(R.drawable.ic_stop, running.icon)
        assertEquals("Timer running: Looked at. Tap to stop. Hold for controls.", running.contentDescription)
        assertEquals(Tile.STATE_INACTIVE, stopped.state)
        assertEquals("Continue last entry", stopped.label)
        assertEquals("Looked at", stopped.subtitle)
        assertEquals(R.drawable.ic_play, stopped.icon)
        assertEquals("No timer running. Tap to continue Looked at.", stopped.contentDescription)
        assertEquals(Tile.STATE_UNAVAILABLE, nothing.state)
        assertEquals("No entry to continue", nothing.subtitle)
        assertEquals(Tile.STATE_INACTIVE, paused.state)
        assertEquals("Looked at", paused.label)
        assertEquals("Paused · Hold for controls", paused.subtitle)
        assertEquals(R.drawable.ic_play, paused.icon)
        assertEquals("Timer paused: Looked at. Tap to resume. Hold for controls.", paused.contentDescription)
        assertEquals(
            "No description",
            runningTileLook(RunningTileContent.Running(entry.copy(description = ""), TimerTarget.server(entryId = entry.id)), context).label,
        )
    }

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    /**
     * Opens the shade, long-presses the tile showing [description], and returns what presses one
     * of the compact controls by its spoken label.
     */
    private fun openCompactControls(description: String): (Int) -> Unit {
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("the entry offered by long press", description)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Where an earlier test allowed notifications, the running notification's title in the shade
        // reads the same description, and a long press there opens the notification's controls.
        // So press the tile's own label.
        val tile = checkNotNull(device.wait(Until.findObject(By.res("com.android.systemui", "tile_label").text(description)), TIMEOUT_MS)) {
            "SystemUI did not expose the running tile"
        }
        tile.longClick()
        return { label ->
            val button = awaitControl(label)
            // The control is in the accessibility tree before its window has drawn, and a touch
            // sent then is dropped. Wait until the window has settled.
            device.waitForIdle()
            button.click()
        }
    }

    /** The compact control spoken as [label], once the dialog shows it. */
    private fun awaitControl(label: Int): UiObject2 {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        return checkNotNull(device.wait(Until.findObject(By.pkg(context.packageName).desc(context.getString(label))), TIMEOUT_MS)) {
            "Compact controls did not show ${context.getString(label)}"
        }
    }



    /** A stopped row is required for Continue; a fresh Start can instead enter short Stop Trash. */
    private fun ageRunning(id: Long, fields: EntryFields) {
        runBlocking {
            val running = checkNotNull(app.cache.running.first()).entry
            assertEquals("the fixture still targets the running entry", id, running.id)
            // Two hours spans even the server's coarsest allowed 60-minute grid.
            val agedStart = running.start.minusSeconds(7_200)
            assertTrue("the Continue fixture has a full span", app.changes.update(id, fields, agedStart, null))
            withTimeout(TIMEOUT_MS) { app.cache.running.first { it?.entry?.start == agedStart } }
        }
    }

    private fun tileNetworkSnapshot() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        BlackholeDiagnostics.record("connectivity.snapshot internet=${connectivity.reachesInternet()}")
    }

    /**
     * Signs in to [server]. The app changes account only through a sign-out, which ends the
     * account's running checks and with them the tile's once-a-minute refresh throttle, so take
     * that step first: a refresh the tile made for the previous session must not hold back the
     * first one for this session.
     */
    private fun signIn(server: ServerUrl, token: String) {
        RunningCheck.cancelAndWait(context)
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    @Serializable
    private class Created(val id: Long)

    private companion object {
        const val CURRENT_REQUEST = "/api/v9/me/time_entries/current HTTP/1.1"
        const val AT_ONCE_MS = 2_000L
        const val TIMEOUT_MS = 60_000L
    }
}
