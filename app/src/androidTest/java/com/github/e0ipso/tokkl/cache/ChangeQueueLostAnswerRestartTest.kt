package com.github.e0ipso.tokkl.cache

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
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
 * The app process dies while the server holds a create the phone sent, before the answer arrives.
 * The steps run in name order, each in a new process (see [ChangeQueueRestartTest]), so step 2
 * sends the queue that step 1's process left. Step 1 makes its own account, and step 2 deletes it.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChangeQueueLostAnswerRestartTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication

    /** What step 1 left for step 2: its process, the test account and the entry. */
    private val handOver = File(context.cacheDir, "ChangeQueueLostAnswerRestartTest.properties")

    @Test
    fun step1SendsACreateAndDiesBeforeTheAnswer() {
        SessionRule.signOut()
        signIn(TokklTestServer.url, TokklTestServer.apiToken)
        val account = TestAccounts.create(timezone = "Europe/Madrid")
        // Read while the app's guard still lets requests reach the server itself.
        val workspace = account.workspaceId
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        val relay = HangingRelay(TokklTestServer.url)
        signIn(relay.url, account.apiToken)
        relay.holdAnswerTo("POST /api/v9/workspaces/")

        val id = runBlocking {
            app.changes.create(workspace, EntryFields(description = "Sent before the process died"), start, start.plus(Duration.ofMinutes(30)))
        }
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (!relay.holdsAnswer) {
            check(System.nanoTime() < deadline) { "the server never answered the create" }
            Thread.sleep(50)
        }
        Properties().apply {
            setProperty("process", currentProcess())
            setProperty("accountId", account.id.toString())
            setProperty("email", account.email)
            setProperty("token", account.apiToken)
            setProperty("entry", id.toString())
            setProperty("start", start.toString())
            handOver.outputStream().use { store(it, null) }
        }
        assertEquals("the create waits for its answer, still queued", 1, runBlocking { app.changes.unsentCount.first() })
        // The process ends with the answer held, as when Android kills the app mid-request.
    }

    @Test
    fun step2SendsTheQueueWithoutASecondCopy() {
        assertTrue("step 1 did not run before step 2", handOver.exists())
        val saved = Properties().apply { handOver.inputStream().use { load(it) } }
        val account = TestAccounts.Account(saved.getProperty("accountId").toLong(), saved.getProperty("email"), saved.getProperty("token"))
        try {
            assertNotEquals("step 1 ran in this same process", saved.getProperty("process"), currentProcess())
            assertEquals("the create survived", 1, runBlocking { app.changes.unsentCount.first() })

            signIn(TokklTestServer.url, account.apiToken)
            runBlocking { app.accountSettings.refresh().join() }
            val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
            while (runBlocking { app.changes.unsentCount.first() } > 0) {
                check(System.nanoTime() < deadline) { "the queue was not sent" }
                runBlocking { app.sender.send().join() }
            }

            val start = Instant.parse(saved.getProperty("start"))
            val url = account.client.server.api("me", "time_entries").newBuilder()
                .addQueryParameter("start_date", start.minus(Duration.ofDays(1)).toString())
                .addQueryParameter("end_date", start.plus(Duration.ofDays(1)).toString())
                .build()
            val copies = account.client.get(url, ListSerializer(JsonObject.serializer())).orFail("GET /api/v9/me/time_entries")
            assertEquals("one copy on the server: $copies", 1, copies.size)
            assertEquals(
                "the phone's entry is that copy",
                runBlocking { app.changes.currentId(saved.getProperty("entry").toLong()) },
                copies.single().getValue("id").jsonPrimitive.long,
            )
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
