package com.github.e0ipso.tokkl.cache

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The running entry's check every 15 minutes (docs/PRD.md 6.3) against the real Tokkl container,
 * with the app closed: no activity starts. [SessionRule] puts a test WorkManager in place, whose
 * TestDriver stands in for the period passing and the network being there. Each test seeds its
 * own account and deletes it after.
 */
class RunningCheckServerTest {

    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val workManager get() = WorkManager.getInstance(context)
    private val driver: TestDriver get() = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)) { "no test WorkManager" }
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInToTheServer() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = web.get(web.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long
        signIn(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        assertNotNull("the refresh stored the settings", app.accountSettings.state.value)
        RunningNotifications.allow()
        RunningNotifications.awaitGone("no notification before a timer starts")
    }

    @After
    fun cleanUp() {
        try {
            signIn(TokklTestServer.url, TokklTestServer.apiToken)
            if (::account.isInitialized) TestAccounts.delete(account)
        } finally {
            SessionRule.signOut()
        }
    }

    @Test
    fun startingATimerSchedulesOneCheckAndStoppingItCancelsIt() {
        assertEquals("no check before a timer starts", emptyList<WorkInfo>(), checkWork())

        val first = runBlocking { app.changes.start(workspace, EntryFields(description = "First")) }
        runBlocking {
            app.changes.update(
                first,
                EntryFields(description = "First"),
                Instant.now().minus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
                stop = null,
            )
        }
        val check = awaitCheck("the start schedules a check") { it.state == WorkInfo.State.ENQUEUED }
        assertEquals("the work waits for a network", NetworkType.CONNECTED, check.constraints.requiredNetworkType)
        assertEquals("every 15 minutes", RunningCheck.INTERVAL.toMillis(), check.periodicityInfo?.repeatIntervalMillis)

        val second = runBlocking { app.changes.start(workspace, EntryFields(description = "Second")) }
        // Both timers need a normal, on-grid duration: replacing the first also queues a Stop.
        // This checks scheduling and cancellation, not short-Stop parity (TKA-0259).
        runBlocking {
            app.changes.update(
                second,
                EntryFields(description = "Second"),
                Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
                stop = null,
            )
        }
        val initialDrain = runBlocking { app.sender.sendAndWait() }
        assertEquals("a second timer keeps the one check", listOf(check.id), checkWork().map { it.id })
        assertEquals(WorkInfo.State.ENQUEUED, checkWork().single().state)

        assertTrue(runBlocking { app.changes.stop(second) })
        assertEquals(WorkInfo.State.CANCELLED, awaitCheck("the stop cancels the check") { it.state.isFinished }.state)
        val stoppedDrain = runBlocking { app.sender.sendAndWait() }
        assertEquals("long timers drain before Stop: $initialDrain; after Stop: $stoppedDrain", 0, unsentCount())
        assertTrue(
            "no work waits or runs with no timer running and the queue empty: ${allWork().map { it.tags to it.state }}",
            allWork().all { it.state.isFinished },
        )
    }

    @Test
    fun aTimerStoppedOnTheServerLeavesTheCacheAndTheNotificationAtTheNextCheck() {
        // Seed on the server, then await the one explicit setup refresh. A queued start would
        // launch an independent post-send refresh that could observe the remote stop first.
        val id = startOnTheWeb("Stopped on the web")
        runBlocking { app.accountSettings.refresh().join() }
        assertEquals(0, unsentCount())
        RunningNotifications.awaitShown("the start")
        val check = awaitCheck("the start schedules a check") { it.state == WorkInfo.State.ENQUEUED }

        web.sendNoContent("PATCH", web.server.api("workspaces", workspace.toString(), "time_entries", id.toString(), "stop"), buildJsonObject {})
            .orFail("PATCH the entry's stop")
        assertNotNull("nothing but the check tells the phone", runBlocking { app.cache.running.first() })
        RequestCounter(TokklTestServer.url, "/api/v9/me/time_entries/current HTTP/1.1").use { requests ->
            signIn(requests.url, account.apiToken)
            runCheck(check.id)

            awaitCondition("the check clears the running entry") { runBlocking { app.cache.running.first() } == null }
            RunningNotifications.awaitGone("the stopped timer's notification")
            assertEquals("the check finds nothing running and ends", WorkInfo.State.CANCELLED, awaitCheck("the cancel") { it.state.isFinished }.state)
            assertEquals("the check sends nothing", 0, unsentCount())
            assertEquals("one current-entry request", 1, requests.count)
            assertTrue("every connection was relayed: ${requests.failures}", requests.failures.isEmpty())
        }
    }

    @Test
    fun aTimerStartedOnTheServerTakesThePlaceOfTheOneItStopped() {
        startOnTheWeb("On the phone")
        runBlocking { app.accountSettings.refresh().join() }
        val check = awaitCheck("the start schedules a check") { it.state == WorkInfo.State.ENQUEUED }

        val elsewhere = startOnTheWeb("Started on the web")
        runCheck(check.id)

        awaitCondition("the check puts the web's timer in the cache") { runBlocking { app.cache.running.first() }?.entry?.id == elsewhere }
        assertEquals("one running entry", 1, runBlocking { app.cache.timeEntries.first() }.count { it.stop == null })
        RunningNotifications.awaitShown("the web's timer") { it.title() == "Started on the web" }
        val next = awaitCheck("the check waits for the next period") { it.ranSince(check) }
        assertEquals("still one check", check.id, next.id)
    }

    @Test
    fun theCheckLeavesAnEntryWithUnsentChangesAsItIs() {
        val id = startOnTheWeb("Sent")
        runBlocking { app.accountSettings.refresh().join() }
        val start = checkNotNull(runBlocking { app.cache.running.first() }).entry.start
        val check = awaitCheck("the start schedules a check") { it.state == WorkInfo.State.ENQUEUED }

        // The edit cannot reach the server, so it waits in the queue.
        signIn(NO_ROUTE, account.apiToken)
        assertTrue(runBlocking { app.changes.update(id, EntryFields(description = "Edited offline"), start, stop = null) })
        runBlocking { app.sender.sendAndWait() }
        assertEquals(1, unsentCount())
        signIn(TokklTestServer.url, account.apiToken)
        web.sendNoContent("PATCH", web.server.api("workspaces", workspace.toString(), "time_entries", id.toString(), "stop"), buildJsonObject {})
            .orFail("PATCH the entry's stop")

        runCheck(check.id)
        awaitCheck("the check ran and waits for the next period") { it.ranSince(check) }

        val running = checkNotNull(runBlocking { app.cache.running.first() }) { "the unsent entry still runs" }
        assertEquals(id, running.entry.id)
        assertEquals("Edited offline", running.entry.description)
        assertTrue(running.unsent)
        assertEquals("the check sends nothing", 1, unsentCount())
        assertEquals("the check stays while the entry runs on the phone", listOf(check.id), checkWork().filter { !it.state.isFinished }.map { it.id })
    }

    /** Lets the check's period pass with a network, so it runs once. */
    private fun runCheck(id: UUID) {
        driver.setInitialDelayMet(id)
        driver.setAllConstraintsMet(id)
    }

    /** Starts a running entry on the server, as the web app does, which stops the one running there. */
    private fun startOnTheWeb(description: String): Long =
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", description)
                put("start", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                put("duration", -1)
            },
            JsonObject.serializer(),
        ).orFail("POST a running entry").getValue("id").jsonPrimitive.long

    private fun android.service.notification.StatusBarNotification.title(): String? =
        notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()

    /**
     * True once the check has run a period since [earlier] and waits for the next one, which
     * WorkManager counts from that run, so later than [earlier]'s.
     */
    private fun WorkInfo.ranSince(earlier: WorkInfo): Boolean =
        state == WorkInfo.State.ENQUEUED && nextScheduleTimeMillis > earlier.nextScheduleTimeMillis

    private fun awaitCondition(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + WORK_TIMEOUT.toNanos()
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(POLL_MS)
        }
    }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    private fun allWork(): List<WorkInfo> = workManager.getWorkInfos(WorkQuery.fromStates(WorkInfo.State.entries)).get()

    private fun checkWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(RunningCheck.WORK_NAME).get()

    /** The one check once it matches [done], within the timeout. The app schedules it off the main thread. */
    private fun awaitCheck(what: String, done: (WorkInfo) -> Boolean): WorkInfo =
        runBlocking {
            withTimeoutOrNull(WORK_TIMEOUT.toMillis()) {
                workManager.getWorkInfosForUniqueWorkFlow(RunningCheck.WORK_NAME).filterNotNull().first { infos ->
                    infos.singleOrNull()?.let(done) == true
                }.single()
            } ?: throw AssertionError("timed out: $what: ${checkWork().map { it.state }}")
        }

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    private companion object {
        val WORK_TIMEOUT: Duration = Duration.ofSeconds(60)

        const val POLL_MS = 50L

        /** Nothing listens on this port, so every request fails for want of a route. */
        val NO_ROUTE: ServerUrl = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
