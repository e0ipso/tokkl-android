package com.github.e0ipso.tokkl.cache

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.io.File
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * The queue survives the death of the app process. The steps run in name order, and the test
 * orchestrator (app/build.gradle.kts) starts a new process for each, so step 2 sends what step 1
 * queued in another process. Step 1 queues with no route to the server, and step 2 fails if both
 * run in the same process. Step 1 makes its own account, and step 2 deletes it.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChangeQueueRestartTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication

    /** What step 1 left for step 2: its process and the test account, which the test deletes. */
    private val handOver = File(context.cacheDir, "ChangeQueueRestartTest.properties")

    @Test
    fun step1QueuesStartEditAndStopWithNoRouteToTheServer() {
        SessionRule.signOut()
        signIn(TokklTestServer.url, TokklTestServer.apiToken)
        val account = TestAccounts.create(timezone = "Europe/Madrid")
        signIn(TokklTestServer.url, account.apiToken)
        // A real sign-in caches the resolution before going offline, so a short Stop predicts
        // the server's Trash outcome before this process exits.
        runBlocking { app.accountSettings.refreshAfterChange().join() }
        val workspace = account.client.get(account.client.server.api("me"), JsonObject.serializer())
            .orFail("GET /api/v9/me").getValue("default_workspace_id").jsonPrimitive.long
        Properties().apply {
            setProperty("process", currentProcess())
            setProperty("accountId", account.id.toString())
            setProperty("email", account.email)
            setProperty("token", account.apiToken)
            handOver.outputStream().use { store(it, null) }
        }
        // Nothing listens on this port, so every request fails for want of a route.
        signIn(checkNotNull(ServerUrl.parse("http://127.0.0.1:9")), account.apiToken)

        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Started before the restart")) }
        val start = runBlocking { app.cache.running.first() }!!.entry.start
        assertTrue(runBlocking { app.changes.update(id, EntryFields(description = "Edited before the restart"), start, stop = null) })
        assertTrue(runBlocking { app.changes.stop(id) })
        runBlocking { app.sender.send().join() }

        assertEquals("the drain failed and kept every change", 3, runBlocking { app.changes.unsentCount.first() })
        assertTrue("the short timer is absent from offline history", runBlocking { app.cache.entries.first() }.isEmpty())
        assertEquals(id, runBlocking { app.changes.shortStopUndo.first() }?.entryId)
    }

    @Test
    fun step2SendsTheQueueInANewProcess() {
        assertTrue("step 1 did not run before step 2", handOver.exists())
        val saved = Properties().apply { handOver.inputStream().use { load(it) } }
        val account = TestAccounts.Account(saved.getProperty("accountId").toLong(), saved.getProperty("email"), saved.getProperty("token"))
        try {
            assertNotEquals("step 1 ran in this same process", saved.getProperty("process"), currentProcess())
            assertEquals("the queue survived", 3, runBlocking { app.changes.unsentCount.first() })
            assertTrue("Trash prediction survived", runBlocking { app.cache.entries.first() }.isEmpty())
            val localId = checkNotNull(runBlocking { app.changes.shortStopUndo.first() }).entryId
            assertTrue("Undo queues offline after process death", runBlocking { app.changes.undoShortStop(localId) })
            val row = runBlocking { app.cache.entries.first() }.single()
            assertTrue(row.unsent)
            assertEquals("Edited before the restart", row.entry.description)
            assertEquals(row.entry.start, row.entry.stop)
            assertEquals(4, runBlocking { app.changes.unsentCount.first() })

            signIn(TokklTestServer.url, account.apiToken)
            runBlocking { app.accountSettings.refresh().join() }
            while (true) {
                // The refresh and WorkManager can drain concurrently. Use the same
                // observation for the loop guard and its strict progress assertion.
                val before = runBlocking { app.changes.unsentCount.first() }
                if (before == 0) break
                runBlocking { app.sender.send().join() }
                assertTrue("the drain sent nothing", runBlocking { app.changes.unsentCount.first() } < before)
            }

            val serverId = runBlocking { app.changes.currentId(localId) }
            val sent = account.client.get(account.client.server.api("me", "time_entries", serverId.toString()), JsonObject.serializer())
                .orFail("GET the entry")
            assertEquals("Edited before the restart", sent.getValue("description").jsonPrimitive.content)
            assertEquals("the server restored a stopped zero-length entry", sent.getValue("start"), sent.getValue("stop"))
        } finally {
            try {
                signIn(TokklTestServer.url, TokklTestServer.apiToken)
                TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
                handOver.delete()
            }
        }
    }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    /** A PID can be reused, so the process start time goes with it. */
    private fun currentProcess(): String = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"
}
