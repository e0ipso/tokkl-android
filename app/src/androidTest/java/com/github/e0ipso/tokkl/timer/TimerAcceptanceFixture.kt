package com.github.e0ipso.tokkl.timer

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.cache.*
import com.github.e0ipso.tokkl.session.Session
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
    fun controls(): TimerControls = runBlocking { app.changes.timerControls.first() }
    fun start(description: String = "Focused work"): TimerControls.Running {
        runBlocking { app.changes.start(account.workspaceId, EntryFields(description)) }
        return controls() as TimerControls.Running
    }
    fun ageRunning(seconds: Long = 600): TimerControls.Running {
        val active = controls() as TimerControls.Running
        val row = active.entry
        assertTrue(runBlocking { app.changes.updateRunning(
            RunningEntryIdentity(row.id, row.workspaceId, row.start),
            EntryFields(row.description, row.projectId, row.taskId, row.tags, row.billable),
            Instant.now().minusSeconds(seconds),
        ) })
        return controls() as TimerControls.Running
    }
    fun pause(): TimerControls.Paused {
        val active = controls() as TimerControls.Running
        assertTrue(runBlocking { app.changes.pause(active.target) })
        return controls() as TimerControls.Paused
    }
    fun resume(): TimerControls.Running {
        val paused = controls() as TimerControls.Paused
        assertNotNull(runBlocking { app.changes.resume(paused.target) })
        return controls() as TimerControls.Running
    }
    fun stop() {
        when (val active = controls()) {
            is TimerControls.Running -> assertTrue(runBlocking { app.changes.stop(active.target) })
            is TimerControls.Paused -> assertTrue(runBlocking { app.changes.stop(active.target) })
            TimerControls.Idle -> Unit
        }
    }
    fun drain() {
        runBlocking { app.sender.sendAndWait() }
        assertEquals("all local mutations sent", 0, runBlocking { app.changes.unsentCount.first() })
        refresh()
    }
}
