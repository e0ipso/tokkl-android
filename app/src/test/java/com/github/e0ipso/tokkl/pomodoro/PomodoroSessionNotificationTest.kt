package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroSessionNotificationTest {
    @Test fun contentUsesThePlannedEndForCountdownAndOvertimeWithoutAppTicks() = runBlocking {
        val f = Fixture()
        val work = f.start().notificationContent()
        assertEquals(START.plusSeconds(60), work.end)
        assertEquals(1, work.pomodoro)
        assertEquals("Focus", work.description)
        f.now = START.plusSeconds(3600)
        assertEquals(work, f.store.current()?.notificationContent())
        f.store.nextPhase()
        val rest = checkNotNull(f.store.current()).notificationContent()
        assertEquals("shortRest", rest.phase.kind)
        assertEquals(1, rest.pomodoro)
        assertEquals(f.now.plusSeconds(60), rest.end)
        f.now = f.now.plusSeconds(60)
        f.store.nextPhase()
        assertEquals(2, f.store.current()?.notificationContent()?.pomodoro)
    }

    @Test fun idleRemovesAndAnActiveSessionPostsOnlyOnceIncludingAfterObserverRecreation() = runBlocking {
        val f = Fixture()
        f.sync.refresh()
        assertEquals(1, f.removes)
        f.start()
        f.sync.refresh()
        val shown = f.saved
        f.sync.refresh()
        f.newSync().refresh()
        assertEquals(1, f.posts.size)
        assertEquals(shown, f.saved)
        assertFalse(checkNotNull(shown).shown!!.contains("Focus"))
    }

    @Test fun editingFieldsReplacesTheNotificationAndDenialCanBeRepostedOnGrant() = runBlocking {
        val f = Fixture()
        f.start()
        f.allowed = false
        f.sync.refresh()
        assertNull(f.saved)
        assertEquals(0, f.posts.size)
        f.allowed = true
        f.sync.refresh(force = true)
        f.store.updateFields(EntryFields("Edited"))
        f.sync.refresh()
        assertEquals(listOf("Focus", "Edited"), f.posts.map { it.description })
        assertNotEquals(f.posts.first().key, f.posts.last().key)
    }

    @Test fun aFailedDisplayWriteDoesNotRepostAndTheNextEmissionPersistsIt() = runBlocking {
        val f = Fixture()
        f.start()
        f.writeFailure = true
        f.sync.refresh()
        assertEquals(1, f.posts.size)
        assertNull(f.saved)
        f.writeFailure = false
        f.sync.refresh()
        assertEquals(1, f.posts.size)
        assertEquals(f.posts.single().key, f.saved?.shown)
        f.writeFailure = true
        assertTrue(runCatching { f.sync.forget() }.isFailure)
        f.sync.refresh(force = true)
        assertEquals(1, f.posts.size)
        assertTrue(f.removes > 0)
    }

    @Test fun ringingOwnsTheSharedIdUntilTheServiceHasFinishedStopping() = runBlocking {
        val f = Fixture()
        val phase = f.start().phaseKey()
        f.sync.refresh()
        val removals = f.removes
        f.now = START.plusSeconds(60)
        f.store.onPhaseAlarm(phase)
        f.sync.refresh()
        assertEquals(1, f.posts.size)
        assertEquals(removals, f.removes)
        assertNull(f.saved?.shown)
        f.serviceRunning = true
        f.store.nextPhase()
        f.sync.refresh()
        assertEquals(1, f.posts.size)
        f.serviceRunning = false
        f.sync.refresh()
        assertEquals(2, f.posts.size)
        assertEquals("shortRest", f.posts.last().phase.kind)
        assertEquals(3, PomodoroSessionNotification.ID)
    }

    @Test fun nextAndEndRejectWrongAccountStalePhaseAndDuplicateDeliveries() = runBlocking {
        val f = Fixture()
        val work = f.start().phaseKey()
        f.sync.refresh()
        val account = checkNotNull(f.saved).account
        assertEquals(PomodoroStep.Idle, f.sync.act("another account", work, end = false))
        assertFalse(f.sync.acceptsTap("another account", work))
        assertTrue(f.sync.acceptsTap(account, work))
        f.now = START.plusSeconds(30)
        assertTrue(f.sync.act(account, work, end = false) is PomodoroStep.Running)
        assertEquals(PomodoroStep.Idle, f.sync.act(account, work, end = false))
        assertEquals(PomodoroStep.Idle, f.sync.act(account, work, end = true))
        assertFalse(f.sync.acceptsTap(account, work))
        val rest = checkNotNull(f.store.current()).phaseKey()
        f.now = START.plusSeconds(90)
        assertTrue(f.sync.act(account, rest, end = true) is PomodoroStep.Saved)
        assertEquals(PomodoroStep.Idle, f.sync.act(account, rest, end = true))
        f.sync.refresh()
        assertNull(f.store.current())
        assertEquals(1, f.database.creates)
        assertEquals(listOf("create", "delete", "queued"), f.database.events.takeLast(3))
        assertEquals(1, f.store.unsavedChangeCount())
        assertNull(f.saved?.shown)
    }

    @Test fun noSignedInAccountCanPublishOrActEvenWithAPersistedSession() = runBlocking {
        val f = Fixture()
        val phase = f.start().phaseKey()
        f.sync.refresh()
        val account = checkNotNull(f.saved).account
        f.signedIn = false
        f.sync.refresh(force = true)
        assertEquals(1, f.posts.size)
        assertEquals(PomodoroStep.Idle, f.sync.act(account, phase, end = true))
        assertFalse(f.sync.acceptsTap(account, phase))
        assertEquals(phase, f.store.current()?.phaseKey())
    }

    @Test fun forgetCancelsACollectorWaitingForThePhaseLockBeforeErasingDisplayState() = runBlocking {
        val f = Fixture()
        val phase = f.start().phaseKey()
        f.sync.refresh()
        val account = checkNotNull(f.saved).account
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val locked = launch(start = CoroutineStart.UNDISPATCHED) {
            f.store.withNotificationState { _, _ -> entered.complete(Unit); release.await() }
        }
        entered.await()
        f.sync.start()
        f.sync.forget()
        assertNull(f.saved)
        release.complete(Unit)
        locked.join()
        f.store.forget()
        f.sync.refresh(force = true)
        assertEquals(1, f.posts.size)
        assertEquals(PomodoroStep.Idle, f.sync.act(account, phase, end = true))
        assertEquals(0, f.database.creates)
        assertNull(f.saved)
    }

    @Test fun forgetWaitsForAnAlreadyAcceptedActionAndNeverRepostsItsResult() = runBlocking {
        val f = Fixture()
        val phase = f.start().phaseKey()
        f.sync.refresh()
        val account = checkNotNull(f.saved).account
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.stopOutput = { entered.complete(Unit); release.await() }
        f.now = START.plusSeconds(30)
        val end = async(start = CoroutineStart.UNDISPATCHED) { f.sync.act(account, phase, end = true) }
        entered.await()
        val forget = async(start = CoroutineStart.UNDISPATCHED) { f.sync.forget() }
        assertFalse(forget.isCompleted)
        release.complete(Unit)
        assertTrue(end.await() is PomodoroStep.Saved)
        forget.await()
        f.sync.refresh()
        assertNull(f.saved)
        assertEquals(1, f.database.creates)
        assertEquals(1, f.posts.size)
    }

    @Test fun newAccountRejectsAnOldIdentityEvenWhenPhaseFieldsAreIdentical() = runBlocking {
        val f = Fixture()
        val phase = f.start().phaseKey()
        f.sync.refresh()
        val oldAccount = checkNotNull(f.saved).account
        f.sync.forget()
        f.store.forget()
        val next = Fixture()
        assertEquals(phase, next.start().phaseKey())
        next.sync.refresh()
        assertNotEquals(oldAccount, next.saved?.account)
        assertEquals(PomodoroStep.Idle, next.sync.act(oldAccount, phase, end = true))
        assertFalse(next.sync.acceptsTap(oldAccount, phase))
        assertEquals(0, next.database.creates)
    }

    private class Fixture {
        var now: Instant = START
        var saved: PomodoroNotificationDisplay? = null
        var writeFailure = false
        var allowed = true
        var signedIn = true
        var serviceRunning = false
        var stopOutput: suspend () -> Unit = {}
        var removes = 0
        val posts = mutableListOf<PomodoroSessionNotificationContent>()
        val database = MemoryDatabase()
        val store = PomodoroSessionStore(
            database, Dispatchers.Unconfined,
            object : Clock() {
                override fun instant(): Instant = now
                override fun getZone(): ZoneId = ZoneOffset.UTC
                override fun withZone(zone: ZoneId?): Clock = this
            },
            accountTime = { null }, onQueued = { database.events += "queued" },
            ringing = object : PomodoroRinging {
                override suspend fun start(ring: PomodoroRing) = Unit
                override suspend fun stop() = stopOutput()
            },
        )
        val sync = newSync()
        fun newSync() = PomodoroSessionNotificationSync(
            store, store.notificationChanges.map { Unit }, { signedIn }, { serviceRunning },
            { saved }, { if (writeFailure) throw IOException("fixture write failed"); saved = it },
            { content, _ -> if (allowed) posts += content; allowed }, { removes++ },
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Dispatchers.Unconfined,
        )
        suspend fun start() = store.start(PomodoroSessionConfig(1.0, 1.0, 1.0, 4.0), EntryFields("Focus"), 7)
    }

    private class MemoryDatabase : PomodoroSessionDatabase {
        var session: PomodoroSession? = null
        var creates = 0
        val events = mutableListOf<String>()
        override fun current() = session
        override fun save(session: PomodoroSession) { this.session = session }
        override fun delete() { session = null; events += "delete" }
        override fun queuedCount() = creates
        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            events += "create"
            creates++
            return -creates.toLong()
        }
        override fun <T> transaction(block: () -> T): T = block()
    }

    private companion object { val START: Instant = Instant.parse("2026-10-20T09:00:00Z") }
}
