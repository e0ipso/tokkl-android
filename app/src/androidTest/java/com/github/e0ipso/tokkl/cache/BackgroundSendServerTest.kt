package com.github.e0ipso.tokkl.cache

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.AirplaneMode
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
        val work = allWork().single()
        assertEquals("one work for both changes", listOf(work.id), queueWork().map { it.id })
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
     * The worker's one run ends in a retry that waits out the backoff. The test stops there: the
     * TestDriver could run the retry only once WorkManager has handed it to the test scheduler
     * again, which happens on the worker's thread after the state this test can watch.
     */
    @Test
    fun aTransientFailureRetriesAfterABackoff() {
        // Nothing listens on this port, so every request fails for want of a route.
        signIn(checkNotNull(ServerUrl.parse("http://127.0.0.1:9")), account.apiToken)
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

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    /** Every work the app has enqueued, in any state. */
    private fun allWork(): List<WorkInfo> = workManager.getWorkInfos(WorkQuery.fromStates(WorkInfo.State.entries)).get()

    private fun queueWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(BackgroundSend.WORK_NAME).get()

    /** The work [id] once it matches [done]. The worker sends on the app's own threads. */
    private fun awaitWork(id: UUID, done: (WorkInfo) -> Boolean): WorkInfo =
        runBlocking {
            withTimeout(WORK_TIMEOUT.toMillis()) { workManager.getWorkInfoByIdFlow(id).filterNotNull().first(done) }
        }

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    private fun serverId(localId: Long): Long =
        runBlocking { app.changes.currentId(localId) }.also { check(it > 0) { "no server id for $localId" } }

    private fun serverEntry(id: Long): JsonObject =
        web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer()).orFail("GET a time entry")

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        val WORK_TIMEOUT: Duration = Duration.ofSeconds(60)

        /** An id the cache holds no entry under. */
        const val NOT_CACHED = 987_654_321L
    }
}
