package com.github.e0ipso.tokkl.cache

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The queue's background send against the real Tokkl container, with the app closed: no activity
 * starts, so when the network returns nothing in the app sends the queue, only the work
 * [BackgroundSend] scheduled. [SessionRule] puts a test WorkManager in place, whose TestDriver
 * stands in for Android telling WorkManager that the network is back. Each test seeds its own
 * account and deletes it after.
 */
class BackgroundSendServerTest {

    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val workManager get() = WorkManager.getInstance(context)
    private val driver: TestDriver get() = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)) { "no test WorkManager" }
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private var relay: HangingRelay? = null

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
    }

    @After
    fun cleanUp() {
        try {
            relay?.close()
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
    fun restoringTheNetworkSendsTheQueueWithTheAppClosed() {
        assertEquals("no work before anything is queued", emptyList<WorkInfo>(), allWork())

        AirplaneMode.set(true, web)
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Queued offline")) }
        assertTrue(runBlocking { app.changes.stop(id) })
        // The app's own send of both changes, which fails for want of a network.
        runBlocking { app.sender.send().join() }
        assertEquals(2, unsentCount())
        val work = queueWork().single()
        // The start also scheduled the running entry's check, which the stop cancelled.
        assertEquals("one work for both changes", listOf(work.id), allWork().filter { RunningCheckWorker::class.java.name !in it.tags }.map { it.id })
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals("the work waits for a network", NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertNull("one-time work", work.periodicityInfo)

        AirplaneMode.set(false, web)
        assertEquals("nothing in the app sends the queue when the network returns", 2, unsentCount())
        driver.setAllConstraintsMet(work.id)

        assertEquals(WorkInfo.State.SUCCEEDED, awaitWork(work.id) { it.state.isFinished }.state)
        assertEquals(0, unsentCount())
        val sent = serverEntry(serverId(id))
        assertEquals("Queued offline", sent.string("description"))
        assertTrue("the stop reached the server", sent["stop"] !is JsonNull)
        assertTrue("no work waits or runs once the queue is empty", allWork().all { it.state.isFinished })
    }

    @Test
    fun callsThatQueueNothingScheduleNoWork() {
        val fields = EntryFields(description = "Not cached")
        val now = Instant.now()

        assertFalse(runBlocking { app.changes.stop(NOT_CACHED) })
        assertFalse(runBlocking { app.changes.update(NOT_CACHED, fields, now.minusSeconds(60), now) })
        assertFalse(runBlocking { app.changes.delete(NOT_CACHED) })

        assertEquals(0, unsentCount())
        assertEquals("no work while the queue is empty", emptyList<WorkInfo>(), allWork())
    }

    /**
     * The worker's one run ends in a retry that waits out the backoff. The test stops there: with
     * the TestDriver's own delays, it could run the retry only once WorkManager has handed it to
     * the test scheduler again, which happens on the worker's thread after the state this test
     * can watch. [theRetryAfterTheBackoffSendsTheQueue] runs it in real time.
     */
    @Test
    fun aTransientFailureRetriesAfterABackoff() {
        signIn(NO_ROUTE, account.apiToken)
        val stop = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        runBlocking { app.changes.create(workspace, EntryFields(description = "Not sent"), stop.minusSeconds(600), stop) }
        runBlocking { app.sender.send().join() }
        val work = queueWork().single()

        val ran = System.currentTimeMillis()
        driver.setAllConstraintsMet(work.id)
        val retried = awaitWork(work.id) { it.runAttemptCount == 1 && it.state == WorkInfo.State.ENQUEUED }

        assertEquals("the change stays queued", 1, unsentCount())
        assertTrue(
            "the retry waits out the backoff: ${retried.nextScheduleTimeMillis - ran} ms",
            retried.nextScheduleTimeMillis >= ran + BackgroundSend.BACKOFF.toMillis(),
        )
        assertEquals("still one work", listOf(work.id), allWork().map { it.id })
    }

    /**
     * The retry itself. The first run fails for want of a server and asks for a retry. The server
     * answers again before the backoff ends, and once the backoff is over the retry sends the
     * queue. Here the test WorkManager keeps real time, so WorkManager waits out the backoff and
     * the TestDriver only reports the network. The first run used that report up, so the test
     * gives it again. In real time the retry runs whichever comes first, that report or
     * WorkManager handing the retry back to the test scheduler. The TestDriver's own delays
     * needed the report to come second, and the test cannot see when that is.
     */
    @Test
    fun theRetryAfterTheBackoffSendsTheQueue() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, WorkManagerTestInitHelper.ExecutorsMode.USE_TIME_BASED_SCHEDULING)
        signIn(NO_ROUTE, account.apiToken)
        val stop = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val id = runBlocking { app.changes.create(workspace, EntryFields(description = "Sent by the retry"), stop.minusSeconds(600), stop) }
        runBlocking { app.sender.send().join() }
        val work = queueWork().single()

        val ran = System.currentTimeMillis()
        driver.setAllConstraintsMet(work.id)
        awaitWork(work.id) { it.runAttemptCount == 1 && it.state == WorkInfo.State.ENQUEUED }
        signIn(TokklTestServer.url, account.apiToken)
        assertEquals("nothing but the retry sends the queue", 1, unsentCount())
        driver.setAllConstraintsMet(work.id)

        val done = awaitWork(work.id, BackgroundSend.BACKOFF.plus(WORK_TIMEOUT)) { it.state.isFinished }
        val finished = System.currentTimeMillis()

        assertEquals(WorkInfo.State.SUCCEEDED, done.state)
        assertEquals("two runs: the one that failed and the retry", 2, done.runAttemptCount)
        assertTrue("the retry waited out the backoff: ${finished - ran} ms", finished - ran >= BackgroundSend.BACKOFF.toMillis())
        assertEquals(0, unsentCount())
        assertEquals("Sent by the retry", serverEntry(serverId(id)).string("description"))
        assertEquals("still one work", listOf(work.id), allWork().map { it.id })
    }

    /**
     * The worker's run fails for want of a server and waits out its backoff. Then the app sends
     * the queue itself, after a change or a refresh, and that drain cancels the waiting retry, so
     * it never wakes the phone to find the queue empty.
     */
    @Test
    fun anAppDrainThatEmptiesTheQueueCancelsTheWaitingRetry() {
        signIn(NO_ROUTE, account.apiToken)
        val stop = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val id = runBlocking { app.changes.create(workspace, EntryFields(description = "Sent by the app"), stop.minusSeconds(600), stop) }
        runBlocking { app.sender.send().join() }
        val work = queueWork().single()
        driver.setAllConstraintsMet(work.id)
        awaitWork(work.id) { it.runAttemptCount == 1 && it.state == WorkInfo.State.ENQUEUED }

        signIn(TokklTestServer.url, account.apiToken)
        runBlocking { app.sender.send().join() }

        assertEquals(0, unsentCount())
        assertEquals("Sent by the app", serverEntry(serverId(id)).string("description"))
        assertEquals("no work waits once the app has sent the queue", WorkInfo.State.CANCELLED, queueWork().single().state)
    }

    /**
     * A worker that starts while the app's drain is on its way joins that drain. The drain empties
     * the queue, and the worker ends as succeeded, not cancelled.
     */
    @Test
    fun aRunningWorkerThatJoinedTheAppsDrainSucceeds() {
        val relay = HangingRelay(TokklTestServer.url).also { this.relay = it }
        signIn(relay.url, account.apiToken)
        relay.holdAnswerTo(CREATE)
        val stop = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        runBlocking { app.changes.create(workspace, EntryFields(description = "Joined"), stop.minusSeconds(600), stop) }
        awaitCondition("the server stored the entry and its answer is held") { relay.holdsAnswer }
        val work = queueWork().single()

        driver.setAllConstraintsMet(work.id)
        awaitWork(work.id) { it.state == WorkInfo.State.RUNNING }
        // The worker joins the drain as soon as it runs; the held answer keeps the drain open.
        Thread.sleep(JOIN_MS)
        relay.releaseAnswer()

        assertEquals(WorkInfo.State.SUCCEEDED, awaitWork(work.id) { it.state.isFinished }.state)
        assertEquals(0, unsentCount())
        assertEquals("one run", 1, queueWork().single().runAttemptCount)
    }

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

    /** Every work the app has enqueued, in any state. */
    private fun allWork(): List<WorkInfo> = workManager.getWorkInfos(WorkQuery.fromStates(WorkInfo.State.entries)).get()

    private fun queueWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(BackgroundSend.WORK_NAME).get()

    /** The work [id] once it matches [done], within [timeout]. The worker sends on the app's own threads. */
    private fun awaitWork(id: UUID, timeout: Duration = WORK_TIMEOUT, done: (WorkInfo) -> Boolean): WorkInfo =
        runBlocking {
            withTimeout(timeout.toMillis()) { workManager.getWorkInfoByIdFlow(id).filterNotNull().first(done) }
        }

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    private fun serverId(localId: Long): Long =
        runBlocking { app.changes.currentId(localId) }.also { check(it > 0) { "no server id for $localId" } }

    private fun serverEntry(id: Long): JsonObject =
        web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer()).orFail("GET a time entry")

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        val WORK_TIMEOUT: Duration = Duration.ofSeconds(60)

        /** Long enough for a worker that has started to call the sender. */
        const val JOIN_MS = 1_000L

        const val POLL_MS = 50L

        /** The start of a create's request. */
        const val CREATE = "POST /api/v9/workspaces/"

        /** An id the cache holds no entry under. */
        const val NOT_CACHED = 987_654_321L

        /** Nothing listens on this port, so every request fails for want of a route. */
        val NO_ROUTE: ServerUrl = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
