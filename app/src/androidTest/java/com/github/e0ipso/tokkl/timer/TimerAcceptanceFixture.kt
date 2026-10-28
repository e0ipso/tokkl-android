package com.github.e0ipso.tokkl.timer

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.cache.*
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

/** Disposable account and real Room/queue shared by the normal timer acceptance tests. */
internal class TimerAcceptanceFixture {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val app get() = context.applicationContext as TokklApplication
    lateinit var account: TestAccounts.Account
    fun prepare() {
        account = TestAccounts.create(timezone = "Asia/Tokyo")
        Session(context).save(TokklTestServer.url, account.apiToken)
        // Read while the stored server is the test server: the account's client sends nowhere
        // else, and a test may store a relay before it starts a timer.
        account.workspaceId
        refresh()
        // Sign in as the sign-in screen stores it, with the settings that refresh read. That
        // gives the account a new, empty timer state. The state an earlier test's account left
        // on the phone would otherwise hold back every state of this account with a lower
        // revision. The refresh after it seeds the new state.
        app.storeSignIn(TokklTestServer.url, account.apiToken, app.accountSettings.state.value!!)
        refresh()
    }
    fun cleanup() {
        try { AirplaneMode.set(false, account.client) }
        finally {
            try { TestAccounts.delete(account) }
            finally { SessionRule.signOut() }
        }
    }
    fun refresh() = runBlocking { app.accountSettings.refresh().join() }
    fun rows(): List<CachedTimeEntry> = runBlocking { app.cache.timeEntries.first() }

    /** What every timer surface shows: the cache's running entry and the server's pause chain. */
    fun shown(): TimerControls = runBlocking { app.timerControls.first() }

    /**
     * Starts [description] on the server, [seconds] ago, and waits for a refresh that read the
     * server after it. Nothing goes through the queue, so no send, and no refresh a send starts
     * without waiting, is still on its way when this returns.
     */
    fun startOnServer(description: String, seconds: Long = 600): TimerControls.Running {
        account.createEntry(description, Instant.now().minusSeconds(seconds))
        runBlocking { app.accountSettings.refreshAfterChange().join() }
        return shown() as TimerControls.Running
    }
    fun start(description: String = "Focused work"): TimerControls.Running {
        runBlocking { app.changes.start(account.workspaceId, EntryFields(description)) }
        return shown() as TimerControls.Running
    }
    fun ageRunning(seconds: Long = 600): TimerControls.Running {
        val active = shown() as TimerControls.Running
        val row = active.entry
        assertTrue(runBlocking { app.changes.updateRunning(
            RunningEntryIdentity(row.id, row.workspaceId, row.start),
            EntryFields(row.description, row.projectId, row.taskId, row.tags, row.billable),
            Instant.now().minusSeconds(seconds),
        ) })
        return shown() as TimerControls.Running
    }

    /** Stops the running entry through the queue, or ends a paused chain on the server. */
    fun stop() {
        when (val active = shown()) {
            is TimerControls.Running -> assertTrue(runBlocking { app.changes.stop(active.entry.id) })
            is TimerControls.Paused -> assertEquals(TimerActionResult.Done, runBlocking { app.timerActions.discard(active.target.revision) })
            TimerControls.Idle -> Unit
        }
    }
    fun drain() {
        runBlocking { app.sender.sendAndWait() }
        assertEquals("all local mutations sent", 0, runBlocking { app.changes.unsentCount.first() })
        // A send ends with a refresh of its own that nobody waits for. Join that one, so a later
        // request count does not catch it.
        runBlocking { app.accountSettings.refreshAfterChange().join() }
    }
}
