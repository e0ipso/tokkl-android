package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.session.AccountSettings
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression source for page identity and subscriptions. Execution belongs to the wave gate. */
class PomodoroPagePhaseActionsTest {
    private val fixtures = mutableListOf<Fixture>()
    private fun fixture() = Fixture().also { fixtures += it }

    @After
    fun stopModelsAndObservers() = runBlocking {
        fixtures.forEach { it.parent.cancelAndJoin() }
    }

    @Test
    fun notificationNextRefreshesTheStillResumedPageWithoutReload() = runBlocking {
        val f = fixture()
        f.page.editFields(EntryFields(description = "Focused work"))
        f.page.start()!!.join()
        val observer = f.observe()
        val work = f.page.state.value.session!!
        f.now = f.now.plusSeconds(40 * 60 + 13)
        f.store.onPhaseAlarm(work.phaseKey())
        f.await { it.session == work }
        assertEquals(PomodoroClock(true, 13), f.page.state.value.session!!.clockAt(f.now))
        f.store.nextPhaseIfCurrent(work.phaseKey())
        f.await { it.session?.phaseKind == "shortRest" }
        assertEquals(f.store.current(), f.page.state.value.session)
        assertEquals("Focused work", f.page.state.value.fields.description)
        assertTrue(f.database.creates.isEmpty())
        assertEquals(1, f.collectors)
        observer.cancelAndJoin()
        assertEquals(0, f.collectors)
    }

    @Test
    fun staleNextAndEndRefreshAnUnobservedPageWithoutWritingFieldsOrSavingAPhantomEntry() = runBlocking {
        for (end in listOf(false, true)) {
            val f = fixture()
            f.page.editFields(EntryFields(description = "Old page fields"))
            f.page.start()!!.join()
            val displayed = f.page.state.value.session!!.phaseKey()
            f.now = f.now.plusSeconds(60)
            f.store.nextPhaseIfCurrent(displayed)
            f.store.updateFields(EntryFields(description = "External edit", tags = listOf("new")))
            val current = f.store.current()
            assertEquals(displayed, f.page.state.value.session!!.phaseKey())
            if (end) f.page.end(displayed)!!.join() else f.page.next(displayed)!!.join()
            assertEquals(current, f.store.current())
            assertEquals(current, f.page.state.value.session)
            assertEquals(current!!.fields, f.page.state.value.fields)
            assertTrue(f.database.creates.isEmpty())
            assertNull(f.page.state.value.notice)
            assertFalse(f.page.state.value.busy)
        }
    }

    @Test
    fun callbacksCapturedBeforeRenderRefreshCannotTargetTheNewlyDisplayedPhase() = runBlocking {
        val f = fixture()
        f.page.start()!!.join()
        f.observe()
        val displayed = f.page.state.value.session!!.phaseKey()
        val capturedNext = { f.page.next(displayed) }
        val capturedEnd = { f.page.end(displayed) }
        f.now = f.now.plusSeconds(60)
        f.store.nextPhaseIfCurrent(displayed)
        f.await { it.session?.phaseKind == "shortRest" }
        val rest = f.store.current()
        capturedNext()!!.join()
        capturedEnd()!!.join()
        assertEquals(rest, f.store.current())
        assertEquals(rest, f.page.state.value.session)
        assertTrue(f.database.creates.isEmpty())
        assertNull(f.page.state.value.notice)
    }

    @Test
    fun staleControlsAndFocusFlushCannotChangeAReplacementSession() = runBlocking {
        val f = fixture()
        f.page.editFields(EntryFields(description = "First session"))
        f.page.start()!!.join()
        f.observe()
        val displayed = f.page.state.value.session!!.phaseKey()
        f.now = f.now.plusSeconds(60)
        f.store.endIfCurrent(displayed)
        f.now = f.now.plusSeconds(1)
        val replacement = f.store.start(
            PomodoroLengths.from(PomodoroDefaults.DEFAULT).config()!!, EntryFields(description = "Replacement", tags = listOf("keep")), 22,
        )
        f.await { it.session == replacement }
        val frozen = f.database.creates.toList()
        f.page.editFields(EntryFields(description = "Stale focused draft"), displayed)!!.join()
        f.page.next(displayed)!!.join()
        f.page.end(displayed)!!.join()
        assertEquals(replacement, f.store.current())
        assertEquals(replacement.fields, f.page.state.value.fields)
        assertEquals(frozen, f.database.creates)
        assertEquals(1, frozen.size)
        assertNull(f.page.state.value.notice)
    }

    @Test
    fun controlsCapturedBeforeExternalEndDoNotSaveAgainAndDefaultsReset() = runBlocking {
        val f = fixture()
        f.page.editLengths(PomodoroLengths("1", "2", "3", "2"))
        f.page.start()!!.join()
        f.observe()
        val displayed = f.page.state.value.session!!.phaseKey()
        f.defaults.value = PomodoroDefaults(60, 12, 25, 5)
        f.now = f.now.plusSeconds(60)
        f.store.endIfCurrent(displayed)
        f.await { it.session == null }
        f.page.next(displayed)!!.join()
        f.page.end(displayed)!!.join()
        assertNull(f.store.current())
        assertEquals(1, f.database.creates.size)
        assertEquals(PomodoroLengths("60", "12", "25", "5"), f.page.state.value.lengths)
        f.defaults.value = PomodoroDefaults(45, 9, 20, 3)
        assertEquals("45", f.page.state.value.lengths.work)
        assertNull(f.page.state.value.notice)
    }

    @Test
    fun duplicateActionsWhileBusyAndOldCallbacksAfterCompletionDoNotAdvanceAgain() = runBlocking {
        val f = fixture()
        f.page.start()!!.join()
        val displayed = f.page.state.value.session!!.phaseKey()
        f.now = f.now.plusSeconds(60)
        val gate = CompletableDeferred<Unit>()
        f.stopGate = gate
        val first = f.page.next(displayed)!!
        assertTrue(f.page.state.value.busy)
        assertNull(f.page.next(displayed))
        assertNull(f.page.end(displayed))
        f.stopGate = null
        gate.complete(Unit)
        first.join()
        val rest = f.store.current()
        f.page.next(displayed)!!.join()
        f.page.end(displayed)!!.join()
        assertEquals(rest, f.store.current())
        assertEquals("shortRest", rest!!.phaseKind)
        assertTrue(f.database.creates.isEmpty())
        assertFalse(f.page.state.value.busy)
    }

    @Test
    fun validDisplayedControlsStillAdvanceSaveOnceAndEndWithCurrentFields() = runBlocking {
        val f = fixture()
        f.observe()
        f.page.start()!!.join()
        f.now = f.now.plusSeconds(60)
        f.page.next(f.page.state.value.session!!.phaseKey())!!.join()
        assertEquals("shortRest", f.page.state.value.session!!.phaseKind)
        f.page.editFields(EntryFields(description = "Rest edit", billable = true))!!.join()
        f.now = f.now.plusSeconds(30)
        val rest = f.page.state.value.session!!.phaseKey()
        f.page.next(rest)!!.join()
        assertEquals(1, f.database.creates.size)
        assertEquals("Rest edit", f.database.creates.single().description)
        assertEquals(PomodoroNotice.SAVED_ON_PHONE, f.page.state.value.notice)
        val work = f.page.state.value.session!!.phaseKey()
        f.page.next(rest)!!.join()
        assertEquals(work, f.store.current()!!.phaseKey())
        assertEquals(1, f.database.creates.size)
        f.now = f.now.plusSeconds(60)
        f.page.end(work)!!.join()
        assertNull(f.store.current())
        assertEquals(2, f.database.creates.size)
        assertEquals(PomodoroNotice.SAVED_ON_PHONE, f.page.state.value.notice)
    }

    @Test
    fun observerRefreshPreservesFailedDraftAndRetryStillPrecedesEnd() = runBlocking {
        val f = fixture()
        f.page.start()!!.join()
        f.observe()
        val work = f.page.state.value.session!!.phaseKey()
        f.database.failSave = true
        f.page.editFields(EntryFields(description = "Keep draft"))!!.join()
        f.now = f.now.plusSeconds(40 * 60)
        f.store.onPhaseAlarm(work)
        f.await { it.fields.description == "Keep draft" }
        assertEquals(PomodoroAction.FIELDS, f.page.state.value.problem!!.action)
        f.page.end(work)!!.join()
        assertTrue(f.database.creates.isEmpty())
        assertEquals("Keep draft", f.page.state.value.fields.description)
        f.database.failSave = false
        f.page.end(work)!!.join()
        assertEquals("Keep draft", f.database.creates.single().description)
    }

    @Test
    fun signedOutStoreClearsTheRetainedPageAndStopsAllModelSubscriptions() = runBlocking {
        val f = fixture()
        f.page.editFields(EntryFields(description = "Private draft"))
        f.page.start()!!.join()
        val observer = f.observe()
        val displayed = f.page.state.value.session!!.phaseKey()
        f.store.forget()
        withTimeout(1_000) { observer.join() }
        assertEquals(0, f.collectors)
        assertEquals(0, f.defaults.subscriptionCount.value)
        assertEquals(0, f.settings.subscriptionCount.value)
        assertEquals(0, f.unsent.subscriptionCount.value)
        assertEquals(0, f.refused.subscriptionCount.value)
        assertTrue(f.parent.isActive)
        val cleared = f.page.state.value
        assertNull(cleared.session)
        assertNull(cleared.account)
        assertEquals(EntryFields(), cleared.fields)
        assertFalse(cleared.canStart)
        assertNull(f.page.next(displayed))
        assertNull(f.page.end(displayed))
        assertNull(f.page.start())
        f.defaults.value = PomodoroDefaults(50, 10, 30, 4)
        f.settings.value = AccountSettings("UTC", DayOfWeek.MONDAY, null, defaultWorkspaceId = 22)
        f.unsent.value = 99
        f.page.permissionsChanged(PomodoroPermissions(false, false, false))
        f.page.fullScreenPermissionChanged(false)
        assertEquals(cleared, f.page.state.value)
        assertTrue(f.database.creates.isEmpty())
    }

    @Test
    fun stoppingObservationRemovesTheCollectorAndResumeReadsChangesMadeWhileStopped() = runBlocking {
        val f = fixture()
        f.page.start()!!.join()
        val work = f.page.state.value.session!!
        val first = f.observe()
        first.cancelAndJoin()
        assertEquals(0, f.collectors)
        f.now = f.now.plusSeconds(60)
        f.store.nextPhaseIfCurrent(work.phaseKey())
        assertEquals(work, f.page.state.value.session)
        val resumed = f.observe()
        f.await { it.session?.phaseKind == "shortRest" }
        assertEquals(1, f.collectors)
        resumed.cancelAndJoin()
        assertEquals(0, f.collectors)
    }

    @Test
    fun cancellationWhileObserverWaitsForTheStoreCannotPublishALateRow() = runBlocking {
        val f = fixture()
        f.page.start()!!.join()
        val shown = f.page.state.value
        f.now = f.now.plusSeconds(60)
        val gate = CompletableDeferred<Unit>()
        f.stopGate = gate
        val external = f.scope.launch { f.store.nextPhaseIfCurrent(shown.session!!.phaseKey()) }
        val observer = f.observe()
        assertEquals(1, f.collectors)
        observer.cancelAndJoin()
        assertEquals(0, f.collectors)
        f.stopGate = null
        gate.complete(Unit)
        external.join()
        assertEquals(shown, f.page.state.value)
        f.observe()
        f.await { it.session?.phaseKind == "shortRest" }
    }

    @Test
    fun viewModelScopeCancellationReleasesObserversAndAccountCollectors() = runBlocking {
        val f = fixture()
        f.observe()
        f.parent.cancelAndJoin()
        assertEquals(0, f.collectors)
        assertEquals(0, f.defaults.subscriptionCount.value)
        assertEquals(0, f.settings.subscriptionCount.value)
        assertEquals(0, f.unsent.subscriptionCount.value)
        assertEquals(0, f.refused.subscriptionCount.value)
        val before = f.page.state.value
        f.now = f.now.plusSeconds(60)
        f.store.start(PomodoroLengths.from(PomodoroDefaults.DEFAULT).config()!!, EntryFields(description = "External"), 22)
        f.unsent.value = 12
        assertEquals(before, f.page.state.value)
    }

    @Test
    fun cancelledCommandReleasesBusyAndDoesNotReplayAnAlreadyCommittedPhase() = runBlocking {
        val f = fixture()
        f.page.start()!!.join()
        val displayed = f.page.state.value.session!!.phaseKey()
        f.now = f.now.plusSeconds(60)
        val gate = CompletableDeferred<Unit>()
        f.stopGate = gate
        val command = f.page.next(displayed)!!
        assertTrue(f.page.state.value.busy)
        command.cancelAndJoin()
        f.stopGate = null
        assertFalse(f.page.state.value.busy)
        assertNull(f.page.state.value.problem)
        assertNull(f.page.state.value.notice)
        f.page.next(displayed)!!.join()
        assertEquals("shortRest", f.page.state.value.session!!.phaseKind)
        assertTrue(f.database.creates.isEmpty())
    }

    @Test
    fun competingNotificationBetweenFieldWriteAndTransitionCannotBeAdvancedOrEndedAgain() = runBlocking {
        for (end in listOf(false, true)) {
            val disk = QueuedDispatcher()
            val f = Fixture(disk).also { fixtures += it }
            disk.runFirst() // Initial reload.
            val start = f.page.start()!!
            disk.runFirst()
            start.join()
            val displayed = f.page.state.value.session!!.phaseKey()
            f.now = f.now.plusSeconds(60)
            val command = if (end) f.page.end(displayed)!! else f.page.next(displayed)!!
            disk.runFirst() // Guarded draft write completes; the phase command is now queued.
            val notification = f.scope.launch { f.store.nextPhaseIfCurrent(displayed) }
            disk.runLast() // The competing guarded action commits before the page transition.
            notification.join()
            val fields = EntryFields(description = "New phase fields")
            val externalEdit = f.scope.launch { f.store.updateFields(fields) }
            disk.runLast()
            externalEdit.join()
            disk.runFirst() // The page's phase guard refuses the old identity.
            disk.runFirst() // Refresh the row after that refusal.
            command.join()
            assertEquals("shortRest", f.page.state.value.session!!.phaseKind)
            assertEquals(fields, f.page.state.value.fields)
            assertTrue(f.database.creates.isEmpty())
            assertNull(f.page.state.value.notice)
            assertFalse(f.page.state.value.busy)
        }
    }

    @Test
    fun clearingViewModelAlsoCancelsTheCollectorOwnedByAnIndependentPageLifecycle() = runBlocking {
        val f = fixture()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { f.page.observeSession() }
        try {
            assertEquals(1, f.collectors)
            f.parent.cancelAndJoin()
            withTimeout(1_000) { observer.join() }
            assertTrue(observer.isCancelled)
            assertEquals(0, f.collectors)
        } finally {
            observer.cancelAndJoin()
        }
    }

    /** Controls only disk dispatch boundaries, without a timer, thread or polling loop. */
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val calls = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { calls.addLast(block) }
        fun runFirst() { calls.removeFirst().run() }
        fun runLast() { calls.removeLast().run() }
    }

    private class Fixture(io: CoroutineContext = Dispatchers.Unconfined) {
        var now = Instant.parse("2026-10-24T09:00:00Z")
        private val clock = object : Clock() {
            override fun instant(): Instant = now
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
        }
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Unconfined)
        val defaults = MutableStateFlow(PomodoroDefaults(40, 8, 20, 3))
        val settings = MutableStateFlow<AccountSettings?>(
            AccountSettings("UTC", DayOfWeek.MONDAY, null, defaultWorkspaceId = 11),
        )
        val unsent = MutableStateFlow(0)
        val refused = MutableStateFlow<List<PendingChange>>(emptyList())
        val database = PageDatabase()
        var stopGate: CompletableDeferred<Unit>? = null
        val store = PomodoroSessionStore(
            database, io, clock, accountTime = { settings.value?.time(clock) },
            ringing = object : PomodoroRinging {
                override suspend fun start(ring: PomodoroRing) = Unit
                override suspend fun stop() { stopGate?.await() }
            },
        )
        var collectors = 0
        val page = PomodoroScreenModel(
            store, defaults, settings, unsent, refused, scope,
            sessionChanges = flow {
                collectors++
                try { emitAll(store.notificationChanges) } finally { collectors-- }
            },
        ).also { it.reload() }

        fun observe(): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) { page.observeSession() }
        suspend fun await(predicate: (PomodoroPageState) -> Boolean) {
            withTimeout(1_000) { page.state.first(predicate) }
        }
    }

    private class PageDatabase : PomodoroSessionDatabase {
        private var row: PomodoroSession? = null
        val creates = mutableListOf<EntryFields>()
        var failSave = false
        override fun current() = row
        override fun save(session: PomodoroSession) {
            if (failSave) throw IOException("session write refused")
            row = session
        }
        override fun delete() { row = null }
        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            creates += fields
            return -creates.size.toLong()
        }
        override fun <T> transaction(block: () -> T): T {
            val before = row
            val previous = creates.toList()
            return try { block() } catch (e: Exception) {
                row = before
                creates.clear()
                creates.addAll(previous)
                throw e
            }
        }
    }
}
