package com.github.e0ipso.tokkl.tile

import android.service.quicksettings.Tile
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
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
    fun aTapStopsTheRunningTimerWithTheNetworkDown() {
        AirplaneMode.set(true, web)
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Stopped from the tile", projectId = project)) }
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
    fun stopAndContinueDoNotWaitForANetworkThatNeverAnswers() {
        HangingRelay(TokklTestServer.url).use { relay ->
            signIn(relay.url, account.apiToken)
            relay.hang()
            val original = runBlocking { app.changes.start(workspace, EntryFields(description = "The network never answers")) }
            QuickSettingsTile.expand()
            QuickSettingsTile.awaitText("the running entry", "The network never answers")
            runBlocking {
                withTimeout(TIMEOUT_MS) {
                    while (relay.hungConnections == 0) delay(20)
                }
            }
            val queued = unsentCount()

            QuickSettingsTile.click()
            runBlocking { withTimeout(AT_ONCE_MS) { app.cache.running.first { it == null } } }
            QuickSettingsTile.awaitText("the stopped timer", context.getString(R.string.tile_continue))

            QuickSettingsTile.click()
            val continued = runBlocking { withTimeout(AT_ONCE_MS) { app.cache.running.first { it != null } } }!!.entry
            assertNotEquals(original, continued.id)
            assertEquals("The network never answers", continued.description)
            assertEquals("both taps stay queued while the request hangs", queued + 2, unsentCount())
            assertTrue("the sender still waits on the network", relay.hungConnections > 0)
            QuickSettingsTile.awaitText("the continued entry", context.getString(R.string.tile_running_subtitle))
        }
    }

    @Test
    fun theTileFollowsStartsAndStopsFromOtherSurfaces() {
        AirplaneMode.set(true, web)
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("an account with no entries", context.getString(R.string.tile_nothing_subtitle))

        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Started in the app")) }
        QuickSettingsTile.awaitText("a start while the panel shows", "Started in the app")
        QuickSettingsTile.awaitText("a start while the panel shows", context.getString(R.string.tile_running_subtitle))

        runBlocking { app.changes.stop(id) }
        QuickSettingsTile.awaitText("a stop while the panel shows", context.getString(R.string.tile_continue))

        QuickSettingsTile.collapse()
        val next = runBlocking { app.changes.start(workspace, EntryFields(description = "Started with the panel closed")) }
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
        val running = runningTileLook(RunningTileContent.Running(entry), context)
        val stopped = runningTileLook(RunningTileContent.Continue(entry.copy(stop = entry.start.plusSeconds(60))), context)
        val nothing = runningTileLook(RunningTileContent.Nothing, context)

        assertEquals(Tile.STATE_ACTIVE, running.state)
        assertEquals("Looked at", running.label)
        assertEquals(R.drawable.ic_stop, running.icon)
        assertEquals("Timer running: Looked at. Tap to stop.", running.contentDescription)
        assertEquals(Tile.STATE_INACTIVE, stopped.state)
        assertEquals("Continue last entry", stopped.label)
        assertEquals("Looked at", stopped.subtitle)
        assertEquals(R.drawable.ic_play, stopped.icon)
        assertEquals("No timer running. Tap to continue Looked at.", stopped.contentDescription)
        assertEquals(Tile.STATE_UNAVAILABLE, nothing.state)
        assertEquals("No entry to continue", nothing.subtitle)
        assertEquals(
            "No description",
            runningTileLook(RunningTileContent.Running(entry.copy(description = "")), context).label,
        )
    }

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    @Serializable
    private class Created(val id: Long)

    private companion object {
        const val AT_ONCE_MS = 2_000L
        const val TIMEOUT_MS = 60_000L
    }
}
