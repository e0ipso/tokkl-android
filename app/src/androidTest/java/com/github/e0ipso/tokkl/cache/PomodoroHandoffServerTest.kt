package com.github.e0ipso.tokkl.cache

import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionStore
import com.github.e0ipso.tokkl.session.Me
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.widget.StartTimerAction
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Acceptance against the pinned real server. Fixtures are disposable and no sender runs until requested. */
class PomodoroHandoffServerTest {
    @get:Rule val sessionRule = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val filename = "pomodoro-handoff-server-${UUID.randomUUID()}.db"
    private val now = AtomicReference(T0)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var account: TestAccounts.Account
    private lateinit var db: CacheDatabase
    private lateinit var queue: ChangeQueue
    private lateinit var sessions: PomodoroSessionStore
    private lateinit var cache: AccountCache
    private lateinit var sender: ChangeSender
    private lateinit var me: Me
    private lateinit var time: AccountTime
    private val dao get() = db.changes()

    @Before fun prepare() {
        account = TestAccounts.create("UTC")
        Session(context).save(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        me = account.client.get(account.client.server.api("me"), Me.serializer()).orFail("read test account")
        time = checkNotNull(me.accountSettings()?.time(clock))
        db = Room.databaseBuilder(context, CacheDatabase::class.java, filename).build()
        queue = ChangeQueue(
            db, Dispatchers.IO, {}, clock,
            stopSpan = time::snapStop, capturedStopSpan = time::snapCapturedStop, pomodoro = { sessions },
        )
        sessions = PomodoroSessionStore.open(
            db, queue, Dispatchers.IO, clock, accountTime = { time },
        )
        cache = AccountCache(db, clock, pomodoro = { sessions })
        sender = ChangeSender(
            RoomChangeStore(db), { account.client }, scope, Dispatchers.IO, clock = clock,
            serverStart = time::snapInstant, switchSpan = { start, stop -> time.snapStop(start, stop, false) },
        )
    }

    @After fun removeFixtures() {
        scope.cancel()
        try {
            if (::db.isInitialized) db.close()
            if (context.getDatabasePath(filename).exists()) assertTrue(context.deleteDatabase(filename))
            assertFalse(context.getDatabasePath(filename).exists())
        } finally {
            try { if (::account.isInitialized) TestAccounts.delete(account) }
            finally { SessionRule.signOut() }
        }
    }

    @Test fun offlineTimerSessionTimerChangesDrainInOrderWithMeetingStoredBoundaries() = runBlocking {
        val firstId = queue.start(account.workspaceId, EntryFields("First timer"))
        now.set(T0.plusSeconds(900))
        sessions.start(LENGTHS, FOCUS, account.workspaceId)
        now.set(T0.plusSeconds(2700))
        val nextId = queue.start(account.workspaceId, EntryFields("Next timer"))
        val partialId = cache.timeEntries.first().single { it.description == FOCUS.description }.id
        val ordered = (dao.changesFor(firstId) + dao.changesFor(partialId) + dao.changesFor(nextId)).sortedBy { it.sequence }
        assertEquals(listOf(ChangeKind.CREATE, ChangeKind.STOP, ChangeKind.CREATE, ChangeKind.CREATE), ordered.map { it.kind })
        assertEquals(T0.plusSeconds(900), ordered[1].capturedStop)
        assertEquals(ordered[1].stop, ordered[2].start)
        assertEquals(ordered[2].stop, ordered[3].start)
        assertNull(sessions.current())
        assertEquals(4, queue.unsentCount.first())
        assertTrue(serverEntries().isEmpty())

        val outcome = sender.sendAndWait()
        assertTrue(outcome is ChangeSender.Outcome.Empty)
        assertEquals(4, outcome!!.sent)
        assertEquals(0, queue.unsentCount.first())
        val first = serverEntry(firstId)
        val partial = serverEntry(partialId)
        val next = serverEntry(nextId)
        assertEquals(first.stopInstant(), partial.startInstant())
        assertEquals(partial.stopInstant(), next.startInstant())
        assertNull(next.stop)
        assertEquals(3, serverEntries().size)
    }

    @Test fun fullRefreshUsesTheForeignStartEvenWhenSeenAfterTheRestBegan() = runBlocking {
        sessions.start(LENGTHS, FOCUS, account.workspaceId)
        now.set(T0.plusSeconds(1500))
        sessions.nextPhase()
        val foreignStart = T0.plusSeconds(900)
        val foreignId = account.createEntry("Other client", foreignStart)
        now.set(T0.plusSeconds(3600))
        refresh()
        assertNull(sessions.current())
        val partial = dao.first()!!
        assertEquals(time.snapUnsent(T0, foreignStart)!!.stop, partial.stop)
        assertEquals(foreignId, dao.runningEntries().single().id)
        repeat(3) { refresh() }
        assertEquals(1, queue.unsentCount.first())
        assertEquals(partial, dao.first())
        assertTrue(sender.sendAndWait() is ChangeSender.Outcome.Empty)
        refresh()
        assertEquals(1, serverEntries().count { it.description == FOCUS.description })
        assertNull(serverEntries().single { it.id == foreignId }.stop)
    }

    @Test fun remappedPhoneStartsAndUnansweredPostEchoesDoNotEndTheSessionAgain() = runBlocking {
        val localId = queue.start(account.workspaceId, EntryFields("Phone timer"))
        val create = dao.changesFor(localId).single()
        // The real POST landed, but the phone has not yet consumed its answer.
        dao.setMaybeSent(create.sequence, true)
        val echoedId = account.createEntry("Phone timer", T0)
        now.set(T0.plusSeconds(60))
        val original = sessions.start(LENGTHS, FOCUS, account.workspaceId)
        refresh()
        assertEquals(original, sessions.current())
        assertEquals(2, queue.unsentCount.first())
        // A lost-response lookup remaps the same id and sends the queued short Stop.
        assertTrue(sender.sendAndWait() is ChangeSender.Outcome.Empty)
        assertEquals(echoedId, queue.currentId(localId))
        refresh()
        refresh()
        assertEquals(original, sessions.current())
        assertEquals(0, queue.unsentCount.first())
        assertTrue(serverEntries().none { it.description == FOCUS.description })
    }

    @Test fun shortTimerSwitchIsTrashedAndUndoRestoresOnlyTheZeroLengthTimer() = runBlocking {
        val localId = queue.start(account.workspaceId, EntryFields("Short timer"))
        now.set(T0.plusSeconds(1))
        val original = sessions.start(LENGTHS, FOCUS, account.workspaceId)
        val stop = dao.changesFor(localId).last()
        assertTrue(stop.shortStop)
        assertNull(dao.entry(localId))
        assertTrue(sender.sendAndWait() is ChangeSender.Outcome.Empty)
        val serverId = queue.currentId(localId)
        val trash = account.client.get(account.client.server.app("trash"), ListSerializer(JsonObject.serializer()))
            .orFail("read short timer trash")
        assertEquals(serverId, trash.single().getValue("id").jsonPrimitive.long)
        assertEquals(0L, trash.single().getValue("duration").jsonPrimitive.long)
        assertTrue(queue.undoShortStop(localId))
        assertTrue(sender.sendAndWait() is ChangeSender.Outcome.Empty)
        val restored = serverEntry(localId)
        assertEquals(restored.startInstant(), restored.stopInstant())
        assertEquals(original, sessions.current())
        assertNull(queue.shortStopUndo.first())
        assertTrue(account.client.get(account.client.server.app("trash"), ListSerializer(JsonObject.serializer()))
            .orFail("read restored timer trash").isEmpty())
    }

    @Test fun widgetCallbackReachesTheApplicationQueueAndEndsAPersistedSession() = runBlocking {
        // A second handle represents the persisted session before the application store is opened.
        val shared = Room.databaseBuilder(context, CacheDatabase::class.java, CacheDatabase.FILE_NAME).build()
        try {
            val began = Instant.now().minusSeconds(1800)
            val persisted = PomodoroSessionStore.open(
                shared, app.changes, Dispatchers.IO, Clock.fixed(began, ZoneOffset.UTC),
                accountTime = { app.accountSettings.state.value?.time() },
            )
            persisted.start(LENGTHS, EntryFields("Widget partial"), account.workspaceId)
            StartTimerAction().onAction(
                context, object : GlanceId {},
                actionParametersOf(ActionParameters.Key<String>("description") to "Widget timer"),
            )
            assertNull(app.pomodoroSessions.current())
            val entries = app.cache.timeEntries.first()
            val partial = entries.single { it.description == "Widget partial" }
            val timer = entries.single { it.description == "Widget timer" }
            assertNotNull(partial.stop)
            assertNull(timer.stop)
            assertEquals(time.snapUnsent(began, timer.start)!!.stop, partial.stop)
        } finally { shared.close() }
    }

    private fun refresh() = cache.refresh(account.client, me, null, Capabilities(true, true, true, true, true))
    private fun serverEntries(): List<ServerTimeEntry> = account.client.get(
        account.client.server.api("me", "time_entries"), ListSerializer(ServerTimeEntry.serializer()),
    ).orFail("read handoff entries")
    private suspend fun serverEntry(localId: Long): ServerTimeEntry = account.client.get(
        account.client.server.api("me", "time_entries", queue.currentId(localId).toString()), ServerTimeEntry.serializer(),
    ).orFail("read handoff entry")
    private fun ServerTimeEntry.startInstant(): Instant = OffsetDateTime.parse(start).toInstant()
    private fun ServerTimeEntry.stopInstant(): Instant? = stop?.let { OffsetDateTime.parse(it).toInstant() }
    private companion object {
        val T0: Instant = Instant.now().truncatedTo(ChronoUnit.DAYS).minusSeconds(7200)
        val LENGTHS = PomodoroSessionConfig(25.0, 5.0, 15.0, 4.0)
        val FOCUS = EntryFields("Server partial Pomodoro")
    }
}
