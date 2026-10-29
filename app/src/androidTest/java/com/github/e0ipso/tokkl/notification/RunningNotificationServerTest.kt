package com.github.e0ipso.tokkl.notification

import android.app.Notification
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TapToVisibleDeadline
import com.github.e0ipso.tokkl.TimerActionToasts
import com.github.e0ipso.tokkl.awaitControls
import com.github.e0ipso.tokkl.timerState
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.countsFrom
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.TimerSyncService
import com.github.e0ipso.tokkl.trash.TrashServer
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

/**
 * The running timer's notification (docs/PRD.md N1) against the real Tokkl container. Each test
 * starts and stops through the phone's running-entry state, [TokklApplication.changes], which the
 * app, the widget, the tile and the shortcuts all write, never through a screen. Each test seeds
 * its own account and deletes it after.
 */
class RunningNotificationServerTest {

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
    fun signInToTheServer() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = web.get(web.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long
        project = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", "Notifications") },
            Created.serializer(),
        ).orFail("POST a project").id
        signIn(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        RunningNotifications.allow()
        RunningNotifications.awaitGone("no notification before a timer starts")
    }

    @After
    fun cleanUp() {
        try {
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
    fun anOfflineStartShowsTheEntryWithAClockTheSystemTicks() {
        AirplaneMode.set(true, web)
        runBlocking { app.changes.start(workspace, EntryFields(description = "Started offline", projectId = project)) }
        val start = runBlocking { app.cache.running.first() }!!.entry.start

        val shown = RunningNotifications.awaitShown("the offline start")
        val notification = shown.notification
        assertEquals("Started offline", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Notifications", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("the clock counts from the start", start.toEpochMilli(), notification.`when`)
        assertTrue("the system ticks a chronometer", notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue("ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "Pause and Stop",
            listOf(context.getString(R.string.notification_pause), context.getString(R.string.notification_stop)),
            notification.actions.map { it.title.toString() },
        )
        assertTrue("tapping opens the app", notification.contentIntent.isActivity)
        assertEquals(context.getString(R.string.notification_channel_running), RunningNotifications.channelName().toString())
        assertEquals(context.getString(R.string.notification_channel_running_description), RunningNotifications.channelDescription())

        // Nothing posts it again to tick the clock, and the timer adds no foreground service. The
        // one that runs is the timer sync service, which a Tokkl server with the timer API keeps
        // running whether or not a timer runs (docs/PRD.md N1).
        Thread.sleep(RunningNotifications.STAYS_GONE_MS)
        assertEquals("posted once", shown.postTime, RunningNotifications.shown()!!.postTime)
        assertEquals(listOf(TimerSyncService::class.java.name), RunningNotifications.foregroundServices())
    }

    @Test
    fun anOfflineStartCorrectionMovesTheNotificationsChronometerToTheCachedStart() {
        AirplaneMode.set(true, web)
        val fields = EntryFields(description = "Corrected offline", projectId = project)
        val id = runBlocking { app.changes.start(workspace, fields) }
        val original = runBlocking { app.cache.running.first() }!!.entry
        val before = RunningNotifications.awaitShown("the original offline start") {
            it.notification.`when` == original.start.toEpochMilli()
        }
        val corrected = original.start.minusSeconds(3607)
        assertEquals("only the offline Start is queued", 1, unsentCount())

        assertTrue(runBlocking {
            app.changes.updateRunning(
                RunningEntryIdentity(id, workspace, original.start), fields, corrected,
            )
        })

        val cached = runBlocking { app.cache.running.first() }!!.entry
        assertEquals("correction keeps the running identity", id, cached.id)
        assertEquals(workspace, cached.workspaceId)
        assertEquals("cached start is unsnapped", corrected, cached.start)
        assertNull("correction does not stop the entry", cached.stop)
        assertEquals("Start and corrected UPDATE wait offline", 2, unsentCount())
        val shown = RunningNotifications.awaitShown("the corrected offline chronometer") {
            it.notification.`when` == corrected.toEpochMilli()
        }
        assertEquals(original.start.toEpochMilli(), before.notification.`when`)
        assertEquals("posted clock follows the corrected cache", cached.start.toEpochMilli(), shown.notification.`when`)
        assertTrue("the system still ticks the corrected clock", shown.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue("the corrected notification stays ongoing", shown.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(context.getString(R.string.notification_stop), shown.notification.stopAction().title.toString())
    }

    @Test
    fun aStopRemovesTheNotification() {
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Stopped in the app")) }
        RunningNotifications.awaitShown("the start")

        assertTrue(runBlocking { app.changes.stop(id) })

        RunningNotifications.awaitGone("the stopped timer's notification")
    }

    @Test
    fun aStartStopsTheRunningEntryAndShowsTheNewOne() {
        runBlocking { app.changes.start(workspace, EntryFields(description = "First")) }
        RunningNotifications.awaitShown("the first start") { it.title() == "First" }

        runBlocking { app.changes.start(workspace, EntryFields(description = "")) }

        val shown = RunningNotifications.awaitShown("the second start") { it.title() == context.getString(R.string.notification_no_description) }
        assertNull("no project", shown.notification.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun stopFromTheNotificationWorksOfflineAndTheQueueSendsItWhenTheNetworkReturns() {
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Stopped from the notification")) }
        runBlocking { app.sender.send().join() }
        assertEquals("the start reached the server", 0, unsentCount())
        val serverId = runBlocking { app.changes.currentId(id) }
        val shown = RunningNotifications.awaitShown("the start")

        AirplaneMode.set(true, web)
        shown.notification.stopAction().actionIntent.send()

        runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it == null } } }
        RunningNotifications.awaitGone("the notification after its Stop")
        assertEquals("the stop waits in the queue", 1, unsentCount())
        val shortStop = runBlocking { app.changes.shortStopUndo.first() }?.also {
            assertEquals("the notification's Undo targets its entry", serverId, it.entryId)
        } != null
        // The cache and notification can update before onQueued's enqueue operation completes.
        // Wait for the new send, rather than driving the previous start's cancelled work.
        val work = runBlocking {
            withTimeout(TIMEOUT_MS) {
                WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(BackgroundSend.WORK_NAME)
                    .first { infos -> infos.any { it.state == WorkInfo.State.ENQUEUED } }
                    .single { it.state == WorkInfo.State.ENQUEUED }
            }
        }

        AirplaneMode.set(false, web)
        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(work.id)
        val done = runBlocking {
            withTimeout(TIMEOUT_MS) { WorkManager.getInstance(context).getWorkInfoByIdFlow(work.id).filterNotNull().first { it.state.isFinished } }
        }

        assertEquals(WorkInfo.State.SUCCEEDED, done.state)
        assertEquals(0, unsentCount())
        if (shortStop) {
            val trashed = TrashServer(web).list().orFail("GET /api/app/trash").single { it.id == serverId }
            assertEquals("Stopped from the notification", trashed.description)
            assertEquals("the notification's short Stop reached Trash", trashed.start, trashed.stop)
            assertEquals(0L, trashed.duration)
            assertNotNull("the server deleted the short entry", trashed.deletedAt)
        } else {
            val sent = web.get(web.server.api("me", "time_entries", serverId.toString()), JsonObject.serializer()).orFail("GET the entry")
            assertTrue("the stop reached the server", sent["stop"] !is JsonNull)
        }
    }

    @Test
    fun pauseResumeAndStopWhilePausedFromTheNotificationGoToTheServer() {
        val first = account.createEntry("Paused from the notification", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS))
        runBlocking { app.accountSettings.refresh().join() }
        val running = RunningNotifications.awaitShown("the server's timer") { it.title() == "Paused from the notification" }

        running.notification.action(R.string.notification_pause).actionIntent.send()

        val paused = RunningNotifications.awaitShown("the paused notification") { it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
        val chain = app.awaitControls<TimerControls.Paused>("the notification's Pause")
        assertEquals("the server's frozen total", checkNotNull(web.timerState().pause).seconds, chain.seconds)
        assertEquals(
            "Resume and Stop",
            listOf(context.getString(R.string.notification_resume), context.getString(R.string.notification_stop)),
            paused.notification.actions.map { it.title.toString() },
        )
        assertFalse("the paused total stands still", paused.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))

        paused.notification.action(R.string.notification_resume).actionIntent.send()

        val resumed = app.awaitControls<TimerControls.Running>("the notification's Resume") { it.accumulatedSeconds == chain.seconds }
        val clock = RunningNotifications.awaitShown("the resumed timer") { it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 }
        assertNotEquals("Resume starts a new entry", first, resumed.entry.id)
        assertEquals("the server's clone", web.timerState().pause?.cloneEntryId, resumed.entry.id)
        assertEquals("the clock counts on from the chain's total", resumed.countsFrom.toEpochMilli() - chain.seconds * 1_000, clock.notification.`when`)

        clock.notification.action(R.string.notification_pause).actionIntent.send()
        val again = RunningNotifications.awaitShown("paused again") { it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
        app.awaitControls<TimerControls.Paused>("the notification's second Pause")
        again.notification.stopAction().actionIntent.send()
        RunningNotifications.awaitGone("the notification after Stop while paused")
        assertEquals("the server ended the chain", null, web.timerState().pause)
        assertEquals("nothing went through the queue", 0, unsentCount())
    }

    @Test
    fun anOfflinePauseFromTheNotificationSaysWhyAndChangesNothing() {
        AirplaneMode.set(true, web)
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Paused offline", projectId = project)) }
        val running = RunningNotifications.awaitShown("the offline start")

        TimerActionToasts.await(TimerActionToasts.offline) { running.notification.action(R.string.notification_pause).actionIntent.send() }

        assertEquals("the timer runs on", id, (runBlocking { app.timerControls.first() } as TimerControls.Running).entry.id)
        assertTrue("the notification still runs", RunningNotifications.shown()!!.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("only the Start is queued", 1, unsentCount())
    }

    @Test
    fun stopFromTheNotificationDoesNotWaitForANetworkThatNeverAnswers() {
        val description = "Stopped while the network hangs"
        // An existing long entry keeps the Stop itself as the only queued change.
        val serverId = account.createEntry(description, Instant.now().minusSeconds(600))
        HangingRelay(TokklTestServer.url).use { relay ->
            signIn(relay.url, account.apiToken)
            runBlocking { app.accountSettings.refresh().join() }
            runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it?.entry?.id == serverId } } }
            val shown = RunningNotifications.awaitShown("the existing entry") { it.title() == description }
            assertEquals("the fixture has no queued changes", 0, unsentCount())

            relay.hang()
            val refresh = app.accountSettings.refresh()
            try {
                runBlocking {
                    withTimeout(TIMEOUT_MS) {
                        while (relay.hungConnections == 0) delay(20)
                    }
                }
                assertTrue("a real accepted connection hangs before Stop", relay.hungConnections > 0)
                val stopped = TapToVisibleDeadline("notification Stop")
                shown.notification.stopAction().actionIntent.send()
                stopped.await("the cache stops") { runBlocking { app.cache.running.first() } == null }
                stopped.await("the notification disappears") { RunningNotifications.shown() == null }
                assertEquals("Stop stays queued unsent", 1, unsentCount())
                assertTrue("the connection still hangs after Stop", relay.hungConnections > 0)
            } finally {
                relay.release()
                runBlocking { refresh.join() }
            }

            runBlocking { app.sender.sendAndWait() }
            assertEquals("Stop eventually reaches the server", 0, unsentCount())
            val sent = web.get(web.server.api("me", "time_entries", serverId.toString()), JsonObject.serializer())
                .orFail("GET the notification's stopped entry")
            assertEquals(serverId, sent.getValue("id").jsonPrimitive.long)
            assertEquals(description, sent.getValue("description").jsonPrimitive.content)
            assertTrue("the captured Stop reached the server", sent["stop"] !is JsonNull)
        }
    }

    @Test
    fun aDismissedNotificationStaysGoneWhenTheAppOpensAndTheNextEditOrStartPostsItAgain() {
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Dismissed")) }
        val start = runBlocking { app.cache.running.first() }!!.entry.start
        RunningNotifications.awaitShown("the start")
        RunningNotifications.dismiss()

        ActivityScenario.launch(MainActivity::class.java).use {
            RunningNotifications.assertStaysGone("the app opened")

            assertTrue(runBlocking { app.changes.update(id, EntryFields(description = "Edited"), start, stop = null) })
            RunningNotifications.awaitShown("the edit") { it.title() == "Edited" }

            RunningNotifications.dismiss()
            runBlocking { app.changes.start(workspace, EntryFields(description = "Started again")) }
            RunningNotifications.awaitShown("the next start") { it.title() == "Started again" }
        }
    }

    @Test
    fun twoStartsWithinOneSecondLeaveTheStopOnTheNewerEntry() {
        AirplaneMode.set(true, web)
        val fields = EntryFields(description = "Started twice", projectId = project)
        // Just as a second begins, so both starts fall in it.
        Thread.sleep(1_000 - System.currentTimeMillis() % 1_000)
        val first = runBlocking { app.changes.start(workspace, fields) }
        val firstShown = RunningNotifications.awaitShown("the first start")
        // Read before the second start stops the first entry and puts its start on the grid.
        val firstStart = runBlocking { app.cache.running.first() }!!.entry.start
        val second = runBlocking { app.changes.start(workspace, fields) }
        val secondStart = runBlocking { app.cache.running.first() }!!.entry.start
        assertEquals("both starts fall in one second", firstStart.epochSecond, secondStart.epochSecond)

        val shown = RunningNotifications.awaitShown("the second start") { it.postTime != firstShown.postTime }
        shown.notification.stopAction().actionIntent.send()

        runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it == null } } }
        val undo = runBlocking { app.changes.shortStopUndo.first() }
        assertNotNull("the newer entry has a short Stop Undo", undo)
        val shortStop = checkNotNull(undo)
        assertEquals("Undo targets the newer start, not the first", second, shortStop.entryId)
        assertEquals("the newer entry stopped at zero duration", shortStop.entry.start, shortStop.entry.stop)
        assertTrue("the short Stop removed the newer entry from history", runBlocking { app.cache.timeEntries.first() }.none { it.id == second })
        assertTrue("the older entry is not running", runBlocking { app.cache.timeEntries.first() }.none { it.id == first && it.stop == null })
        RunningNotifications.awaitGone("the notification after its Stop")

        AirplaneMode.set(false, web)
        runBlocking { app.sender.sendAndWait() }
        assertEquals("both starts and Stops reached the server", 0, unsentCount())
        val firstServerId = runBlocking { app.changes.currentId(first) }
        val secondServerId = runBlocking { app.changes.currentId(second) }
        assertTrue("the starts have distinct server ids", firstServerId > 0 && secondServerId > 0 && firstServerId != secondServerId)
        val trashed = TrashServer(web).list().orFail("GET /api/app/trash")
        assertTrue("the first start was also short", trashed.any { it.id == firstServerId })
        val secondTrash = trashed.single { it.id == secondServerId }
        assertEquals("the notification stopped the newer entry", shortStop.entry.start.epochSecond, java.time.OffsetDateTime.parse(secondTrash.start).toEpochSecond())
        assertEquals(secondTrash.start, secondTrash.stop)
        assertEquals(0L, secondTrash.duration)

        AirplaneMode.set(true, web)
        assertTrue("Undo restores the newer stopped entry offline", runBlocking { app.changes.undoShortStop(second) })
        val restored = runBlocking { app.cache.timeEntries.first() }.single { it.id == secondServerId }
        assertEquals(shortStop.entry.start, restored.start)
        assertEquals(shortStop.entry.stop, restored.stop)
        assertNull("Undo must not restart the notification", runBlocking { app.cache.running.first() })
        AirplaneMode.set(false, web)
        runBlocking { app.sender.sendAndWait() }
        assertEquals("Undo reached the server", 0, unsentCount())
        val remainingTrash = TrashServer(web).list().orFail("GET /api/app/trash")
        assertTrue("Undo left the older entry in Trash", remainingTrash.any { it.id == firstServerId })
        assertTrue("Undo restored only the newer entry", remainingTrash.none { it.id == secondServerId })
        val restoredOnServer = web.get(web.server.api("me", "time_entries", secondServerId.toString()), JsonObject.serializer())
            .orFail("GET the restored newer entry")
        assertEquals(restoredOnServer.getValue("start"), restoredOnServer.getValue("stop"))
    }

    @Test
    fun aDismissedNotificationStaysGoneWhenTheServerAnswersAnOfflineStart() {
        AirplaneMode.set(true, web)
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Answered later", projectId = project)) }
        RunningNotifications.awaitShown("the offline start")
        RunningNotifications.dismiss()

        AirplaneMode.set(false, web)
        runBlocking { app.sender.sendAndWait() }
        assertEquals("the start reached the server", 0, unsentCount())
        val serverId = runBlocking { app.changes.currentId(id) }
        assertTrue("the server gave the entry its id", serverId > 0)
        runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it?.entry?.id == serverId } } }

        RunningNotifications.assertStaysGone("the server's answer to the start")
    }

    private fun android.service.notification.StatusBarNotification.title(): String? =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }



    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    @Serializable
    private class Created(val id: Long)

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
