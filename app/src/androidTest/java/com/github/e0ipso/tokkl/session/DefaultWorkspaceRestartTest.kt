package com.github.e0ipso.tokkl.session

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.orFail
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
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
 * The account's default workspace survives a restart of the app process, so a timer started
 * offline has one. The steps run in name order, and the test orchestrator
 * (app/build.gradle.kts) starts a new process for each. They share an account of their own,
 * which the first step creates and the last deletes. A step fails if it runs in the same process
 * as the one before.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DefaultWorkspaceRestartTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication

    /** The process that ran the step before, the account's id and email, the workspace and the queued entry. */
    private val state = File(context.cacheDir, "DefaultWorkspaceRestartTest.state")

    private val client: TogglApiClient get() = checkNotNull(Session(context).client()) { "signed out" }

    @Test
    fun step1OneRefreshStoresTheDefaultWorkspace() {
        SessionRule.signOut()
        state.delete()
        // The admin creates the account through the app's client, which sends only to the stored server.
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
        val account = TestAccounts.create(timezone = "UTC")
        try {
            Session(context).save(TokklTestServer.url, account.apiToken)

            runBlocking { app.accountSettings.refresh().join() }

            assertEquals(account.workspaceId, app.accountSettings.state.value?.defaultWorkspaceId)
            assertEquals(account.workspaceId, AccountSettingsStore(context).load()?.defaultWorkspaceId)
            state.writeText(listOf(currentProcess(), account.id, account.email, account.workspaceId).joinToString("\n"))
        } catch (e: Throwable) {
            TestAccounts.delete(account)
            SessionRule.signOut()
            throw e
        }
    }

    /** In airplane mode after a restart, the stored workspace is there, and a timer starts in it on the phone. */
    @Test
    fun step2OfflineAfterARestartATimerStartsInTheStoredWorkspace() {
        val (previous, accountId, email, workspace) = state.readLines()
        assertNotEquals("the step before ran in this same process", previous, currentProcess())
        try {
            AirplaneMode.set(true, client)
            assertEquals("read from the phone", workspace.toLong(), app.accountSettings.state.value?.defaultWorkspaceId)
            runBlocking { app.accountSettings.refresh().join() }
            assertEquals("a failed refresh keeps it", workspace.toLong(), app.accountSettings.state.value?.defaultWorkspaceId)

            val stored = checkNotNull(app.accountSettings.state.value?.defaultWorkspaceId)
            val id = runBlocking { app.changes.start(stored, EntryFields(description = "Started offline")) }
            runBlocking { app.sender.send().join() }

            assertEquals("the start waits in the queue", 1, runBlocking { app.changes.unsentCount.first() })
            state.writeText(listOf(currentProcess(), accountId, email, workspace, id).joinToString("\n"))
        } finally {
            AirplaneMode.off()
        }
    }

    /** Once the queue reaches the server, the timer runs there in the account's default workspace. */
    @Test
    fun step3TheSentTimerRunsInTheDefaultWorkspace() {
        val lines = state.readLines()
        val (previous, accountId, email, workspace) = lines
        try {
            assertNotEquals("the step before ran in this same process", previous, currentProcess())
            assertTrue("the step before queued an entry", lines.size == 5)
            assertEquals("read after a second restart", workspace.toLong(), app.accountSettings.state.value?.defaultWorkspaceId)
            AirplaneMode.set(false, client)
            val deadline = System.nanoTime() + SEND_TIMEOUT_NANOS
            while (runBlocking { app.changes.unsentCount.first() } > 0) {
                check(System.nanoTime() < deadline) { "the queue did not empty" }
                runBlocking { app.sender.send().join() }
            }

            val serverId = runBlocking { app.changes.currentId(lines[4].toLong()) }
            val entry = client.get(client.server.api("me", "time_entries", serverId.toString()), JsonObject.serializer())
                .orFail("GET /api/v9/me/time_entries/<id>")
            assertEquals("Started offline", entry.getValue("description").jsonPrimitive.content)
            assertEquals("the default workspace", workspace.toLong(), entry.getValue("workspace_id").jsonPrimitive.long)
            assertTrue("it still runs", entry["stop"] == null || entry["stop"] is JsonNull)
        } finally {
            try {
                // Deleting needs only the account's id and email, and the admin's token.
                TestAccounts.delete(TestAccounts.Account(accountId.toLong(), email, apiToken = ""))
            } finally {
                SessionRule.signOut()
                state.delete()
            }
        }
    }

    /** A PID can be reused, so the process start time goes with it. */
    private fun currentProcess(): String = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"

    private companion object {
        const val SEND_TIMEOUT_NANOS = 60_000_000_000L
    }
}
