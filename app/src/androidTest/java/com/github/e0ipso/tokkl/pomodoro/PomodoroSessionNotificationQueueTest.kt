package com.github.e0ipso.tokkl.pomodoro

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RoomChangeStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Room + released server. No sender runs until the offline-action assertions finish. */
class PomodoroSessionNotificationQueueTest {
    @get:Rule val session = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val filename = "session-notification-${UUID.randomUUID()}.db"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val now = AtomicReference(Instant.parse("2026-10-24T09:00:00Z"))
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private lateinit var account: TestAccounts.Account
    private lateinit var db: CacheDatabase
    private lateinit var queue: ChangeQueue
    private lateinit var store: PomodoroSessionStore
    private lateinit var sync: PomodoroSessionNotificationSync
    private var display: PomodoroNotificationDisplay? = null
    private var active: PomodoroSessionNotificationContent? = null
    private var workspace = 0L

    @Before fun prepare() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        workspace = checkNotNull(app.accountSettings.state.value).defaultWorkspaceId!!
        connect()
    }

    private fun connect() {
        db = Room.databaseBuilder(context, CacheDatabase::class.java, filename).build()
        queue = ChangeQueue(db, Dispatchers.IO, onQueued = {}, clock = clock)
        store = PomodoroSessionStore.open(
            db, queue, Dispatchers.IO, clock, accountTime = { app.accountSettings.state.value?.time(clock) },
            alarms = IdlePomodoroAlarms, phaseEnds = MemoryPomodoroPhaseEnds(),
        )
        sync = PomodoroSessionNotificationSync(
            store, store.notificationChanges.map { Unit }, { true }, { false }, { display }, { display = it },
            { content, _ -> active = content; true }, { active = null }, scope, Dispatchers.IO,
        )
    }

    @After fun cleanup() {
        scope.cancel()
        try {
            runBlocking { if (::sync.isInitialized) sync.forget() }
            if (::db.isInitialized) db.close()
            context.deleteDatabase(filename)
            assertFalse(context.getDatabasePath(filename).exists())
            assertNull(display)
            assertNull(active)
        } finally {
            try { if (::account.isInitialized) TestAccounts.delete(account) }
            finally { SessionRule.signOut() }
        }
    }

    @Test fun offlineNextAndEndKeepOrdinaryQueueOrderAcrossReopenAndSendEachFrozenEntryOnce() = runBlocking {
        val start = now.get()
        queue.create(workspace, EntryFields("Earlier manual save"), start.minusSeconds(3600), start.minusSeconds(1800))
        var phase = store.start(LENGTHS, EntryFields("First pomodoro"), workspace).phaseKey()
        sync.refresh()
        val identity = checkNotNull(display).account
        now.set(start.plusSeconds(1800))
        assertTrue(sync.act(identity, phase, end = false) is PomodoroStep.Running)
        phase = checkNotNull(store.current()).phaseKey()
        now.set(start.plusSeconds(3600))
        assertTrue(sync.act(identity, phase, end = false) is PomodoroStep.Saved)
        phase = checkNotNull(store.current()).phaseKey()
        store.updateFields(EntryFields("Second pomodoro"))
        now.set(start.plusSeconds(5400))
        assertTrue(sync.act(identity, phase, end = true) is PomodoroStep.Saved)
        assertEquals(PomodoroStep.Idle, sync.act(identity, phase, end = true))
        sync.refresh()
        assertNull(active)
        assertNull(store.current())
        assertEquals(3, queue.unsentCount.first())
        assertEquals(3, store.unsavedChangeCount())
        withContext(Dispatchers.IO) {
            val names = db.openHelper.readableDatabase.query("SELECT description FROM pending_changes ORDER BY sequence").use { rows ->
                buildList { while (rows.moveToNext()) add(rows.getString(0)) }
            }
            assertEquals(listOf("Earlier manual save", "First pomodoro", "Second pomodoro"), names)
        }
        db.close()
        connect()
        assertNull(store.current())
        assertEquals(3, queue.unsentCount.first())
        val model = PomodoroScreenModel(store, app.pomodoroDefaults.defaults, app.accountSettings.state, queue.unsentCount, queue.held, scope)
        val pending = kotlinx.coroutines.withTimeout(10_000) { model.state.first { !it.loading && it.unsentCount == 3 } }
        assertNull(pending.session)
        val sender = ChangeSender(RoomChangeStore(db), { account.client }, scope, Dispatchers.IO, clock = clock)
        sender.sendAndWait()
        assertEquals(0, queue.unsentCount.first())
        val entries = AccountCache(db).timeEntries.first()
        assertEquals(3, entries.size)
        assertEquals(setOf("Earlier manual save", "First pomodoro", "Second pomodoro"), entries.map { it.description }.toSet())
        for (entry in entries) {
            assertTrue(entry.id > 0)
            val remote = account.entry(entry.id)
            assertEquals(entry.description, remote.getValue("description").jsonPrimitive.content)
            assertTrue(remote.getValue("duration").jsonPrimitive.content.toLong() >= 0)
        }
        sender.sendAndWait()
        assertEquals(3, AccountCache(db).timeEntries.first().size)
    }

    @Test fun observerForgetErasesDisplayIdentityWithoutClearingQueuedSavesBeforeAccountCleanup() = runBlocking {
        val phase = store.start(LENGTHS, EntryFields("Pending on sign-out"), workspace).phaseKey()
        sync.refresh()
        val identity = checkNotNull(display).account
        now.set(now.get().plusSeconds(1800))
        sync.act(identity, phase, end = true)
        assertEquals(1, queue.unsentCount.first())
        sync.forget()
        assertNull(display)
        assertNull(active)
        assertEquals(1, queue.unsentCount.first())
        store.forget()
        withContext(Dispatchers.IO) { AccountCache(db).clear() }
        sync.refresh(force = true)
        assertEquals(0, queue.unsentCount.first())
        assertNull(active)
        assertEquals(PomodoroStep.Idle, sync.act(identity, phase, end = true))
        assertEquals(0, queue.unsentCount.first())
    }

    private companion object { val LENGTHS = PomodoroSessionConfig(25.0, 5.0, 15.0, 4.0) }
}
