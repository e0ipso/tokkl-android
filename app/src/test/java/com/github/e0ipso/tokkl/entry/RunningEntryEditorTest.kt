package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.timerControls
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timer.TimerModel
import com.github.e0ipso.tokkl.timer.TimerState
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The running editor and its local-write boundary, without a device timezone or network. */
class RunningEntryEditorTest {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val rows = MutableStateFlow(listOf(EntryWithSendState(ENTRY, unsent = true)))
    private val settings = MutableStateFlow<AccountSettings?>(AccountSettings(
        timeZone = "Europe/Madrid",
        beginningOfWeek = DayOfWeek.MONDAY,
        timeResolutionMinutes = 15.0,
        defaultWorkspaceId = 10,
    ))
    private val projects = MutableStateFlow(emptyList<CachedProject>())
    private val tasks = MutableStateFlow(emptyList<CachedTask>())
    private val clock = Clock.fixed(Instant.parse("2026-10-22T22:10:00Z"), ZoneOffset.UTC)
    private val changes = Changes()
    private val form = EntryFormModel(rows, projects, tasks, settings, changes, scope, clock)
    private val timer = TimerModel(rows.map { entries -> timerControls(entries.firstOrNull { it.entry.stop == null }, chain = null, session = 0) }, projects, tasks, settings, changes, scope, clock)

    @Before
    fun collect() {
        scope.launch { form.state.collect {} }
        scope.launch { timer.state.collect {} }
        timerState { it is TimerState.Running }
    }

    @After
    fun close() {
        scope.cancel()
        dispatcher.close()
    }

    @Test
    fun opensRunningModeInAccountDateAndKeepsSecondsOnMetadataSave() {
        val opened = open()
        assertTrue(opened.editing)
        assertTrue(opened.running)
        assertEquals(LocalDate.parse("2026-10-23"), opened.date)
        assertEquals("00:03", opened.start)
        assertEquals("", opened.end)
        assertEquals("", opened.duration)
        onMain {
            form.setDescription("Correct the start")
            form.pick(Picked(projectId = 12, taskId = 15, tags = listOf("offline")))
            form.setBillable(true)
            form.save()
        }
        saved()
        val update = changes.updates.single()
        assertEquals(ENTRY.start, update.start)
        assertNull(update.stop)
        assertEquals(ENTRY.id, update.id)
        assertEquals(ENTRY.workspaceId, update.workspaceId)
        assertEquals("Correct the start", update.description)
        assertEquals(12L, update.projectId)
        assertEquals(15L, update.taskId)
        assertEquals(listOf("offline"), update.tags)
        assertTrue(update.billable)
    }

    @Test
    fun backdatesAcrossMidnightWithoutSnappingRunningStart() {
        open()
        onMain {
            form.setDate(LocalDate.parse("2026-10-22"))
            form.setStart("23:53")
            form.timesDone()
            form.save()
        }
        saved()
        assertEquals(Instant.parse("2026-10-22T21:53:00Z"), changes.updates.single().start)
        assertNull(changes.updates.single().stop)
        assertEquals(1, rows.value.count { it.entry.stop == null })
    }

    @Test
    fun parsesTwelveHourStartAgainstTheAccountClock() {
        onMain { settings.value = settings.value!!.copy(twelveHour = true) }
        open()
        onMain {
            form.setDate(LocalDate.parse("2026-10-22"))
            form.setStart("11:53pm")
            form.save()
        }
        saved()
        assertEquals(Instant.parse("2026-10-22T21:53:00Z"), changes.updates.single().start)
    }

    @Test
    fun ignoresHiddenEndAndDurationInRunningMode() {
        open()
        onMain {
            form.setEnd("invalid")
            form.setDuration("invalid")
            form.durationDone()
            form.save()
        }
        saved()
        assertEquals(ENTRY.start, changes.updates.single().start)
        assertNull(changes.updates.single().stop)
    }

    @Test
    fun invalidStartDoesNotFlushMetadataOrQueueAnything() {
        open()
        onMain {
            form.setDescription("Unsaved")
            form.setStart("invalid")
            form.save()
        }
        val invalid = state { it is EntryFormState.Open && it.mistakes.isNotEmpty() } as EntryFormState.Open
        assertEquals(mapOf(TimeField.START to TimeMistake.TIMES), invalid.mistakes)
        assertTrue(changes.updates.isEmpty())
        assertEquals(ENTRY, rows.value.single().entry)
    }

    @Test
    fun cancelDiscardsUnsavedDateStartAndMetadata() {
        open()
        onMain {
            form.setDate(LocalDate.parse("2026-10-21"))
            form.setStart("23:53")
            form.setDescription("Discard")
            form.close()
        }
        state { it == EntryFormState.Closed }
        assertEquals(ENTRY.description, open().fields.description)
        assertTrue(changes.updates.isEmpty())
    }

    @Test
    fun stoppedSegmentCannotBeRevivedByStaleSave() {
        open()
        onMain {
            rows.value = listOf(EntryWithSendState(ENTRY.copy(stop = clock.instant()), unsent = true))
            form.save()
        }
        state { it == EntryFormState.Missing }
        assertTrue(changes.updates.isEmpty())
        assertNotNull(rows.value.single().entry.stop)
    }

    @Test
    fun openingWaitsForInitialStartAndUsesItsAssignedId() {
        onMain { rows.value = emptyList() }
        timerState { it is TimerState.Idle }
        val gate = CompletableDeferred<Unit>()
        val opening = onMain {
            changes.startGate = gate
            timer.start()
            scope.async(start = CoroutineStart.UNDISPATCHED) { timer.runningForEdit() }
        }
        assertFalse(opening.isCompleted)
        gate.complete(Unit)
        val identity = runBlocking { withTimeout(TIMEOUT) { opening.await() } }!!
        assertEquals(-2L, identity.entryId)
        assertEquals(10L, identity.workspaceId)
        assertEquals(clock.instant(), identity.start)
    }

    @Test
    fun openingWaitsForPendingDescriptionBeforeReadingTheForm() {
        val gate = CompletableDeferred<Unit>()
        val opening = onMain {
            changes.updateGate = gate
            timer.editDescription("Latest typing")
            scope.async(start = CoroutineStart.UNDISPATCHED) { timer.runningForEdit() }
        }
        runBlocking { withTimeout(TIMEOUT) { changes.updateEntered.await() } }
        assertFalse(opening.isCompleted)
        gate.complete(Unit)
        val identity = runBlocking { withTimeout(TIMEOUT) { opening.await() } }!!
        onMain { form.editRunning(identity) }
        val opened = state { it is EntryFormState.Open } as EntryFormState.Open
        assertEquals("Latest typing", opened.fields.description)
        assertEquals(1, changes.updates.size)
    }

    @Test
    fun pendingDescriptionSurvivesServerIdAndWholeSecondRemapping() {
        onMain {
            timer.editDescription("Typed before reconnect")
            changes.remap[ENTRY.id] = 201
            rows.value = listOf(EntryWithSendState(ENTRY.copy(id = 201, start = ENTRY.start.truncatedTo(ChronoUnit.SECONDS)), unsent = false))
        }
        val shown = timerState { it is TimerState.Running && it.entryId == 201L } as TimerState.Running
        assertEquals("Typed before reconnect", shown.fields.description)
        val identity = onMain { timer.runningForEdit() }!!
        onMain { form.editRunning(identity) }
        val opened = state { it is EntryFormState.Open } as EntryFormState.Open
        assertEquals("Typed before reconnect", opened.fields.description)
        assertEquals(201L, changes.updates.single().id)
        assertEquals(ENTRY.start.truncatedTo(ChronoUnit.SECONDS), changes.updates.single().start)
    }

    @Test
    fun sameInstantReplacementDoesNotInheritPendingTimerText() {
        onMain {
            timer.editDescription("Text for original")
            rows.value = listOf(EntryWithSendState(ENTRY.copy(id = 202, description = "Replacement"), unsent = false))
        }
        val shown = timerState { it is TimerState.Running && it.entryId == 202L } as TimerState.Running
        assertEquals("Replacement", shown.fields.description)
        val identity = onMain { timer.runningForEdit() }!!
        onMain { form.editRunning(identity) }
        val opened = state { it is EntryFormState.Open } as EntryFormState.Open
        assertEquals("Replacement", opened.fields.description)
        assertTrue(changes.updates.isEmpty())
    }

    @Test
    fun stopDuringDescriptionSettlementDoesNotReviveOrOpenTheEntry() {
        val gate = CompletableDeferred<Unit>()
        val opening = onMain {
            changes.updateGate = gate
            timer.editDescription("Latest typing")
            scope.async(start = CoroutineStart.UNDISPATCHED) { timer.runningForEdit() }
        }
        runBlocking { withTimeout(TIMEOUT) { changes.updateEntered.await() } }
        onMain { rows.value = listOf(EntryWithSendState(ENTRY.copy(stop = clock.instant()), unsent = true)) }
        timerState { it is TimerState.Idle }
        gate.complete(Unit)
        assertNull(runBlocking { withTimeout(TIMEOUT) { opening.await() } })
        assertTrue(changes.updates.isEmpty())
        assertNotNull(rows.value.single().entry.stop)
    }

    private fun open(): EntryFormState.Open {
        onMain { form.editRunning(RunningEntryIdentity(ENTRY.id, ENTRY.workspaceId, ENTRY.start)) }
        return state { it is EntryFormState.Open } as EntryFormState.Open
    }

    private fun saved() = state { it == EntryFormState.Saved }

    private fun state(predicate: (EntryFormState) -> Boolean): EntryFormState = runBlocking {
        withTimeout(TIMEOUT) { form.state.first(predicate) }
    }

    private fun timerState(predicate: (TimerState) -> Boolean): TimerState = runBlocking {
        withTimeout(TIMEOUT) { timer.state.first(predicate) }
    }

    private fun <T> onMain(block: suspend () -> T): T = runBlocking(dispatcher) { block() }

    private inner class Changes : EntryChanges {
        val remap = mutableMapOf<Long, Long>()
        val updates = mutableListOf<CachedTimeEntry>()
        var startGate = CompletableDeferred(Unit)
        var updateGate = CompletableDeferred(Unit)
        val updateEntered = CompletableDeferred<Unit>()

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long {
            startGate.await()
            rows.value = listOf(EntryWithSendState(ENTRY.copy(id = -2, workspaceId = workspaceId, start = clock.instant()), unsent = true))
            return -2
        }

        override suspend fun currentId(entryId: Long): Long = remap[entryId] ?: entryId

        override suspend fun runningEntry(identity: RunningEntryIdentity): CachedTimeEntry? {
            val id = remap[identity.entryId] ?: identity.entryId
            val current = rows.value.singleOrNull()?.entry ?: return null
            val sameStart = current.start == identity.start ||
                (id != identity.entryId && current.start == identity.start.truncatedTo(ChronoUnit.SECONDS))
            return current.takeIf { it.id == id && it.workspaceId == identity.workspaceId && it.stop == null && sameStart }
        }

        override suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean {
            updateEntered.complete(Unit)
            updateGate.await()
            val current = runningEntry(identity) ?: return false
            val updated = current.copy(description = fields.description, projectId = fields.projectId, taskId = fields.taskId,
                tags = fields.tags, billable = fields.billable, start = start, stop = null)
            updates += updated
            rows.value = listOf(EntryWithSendState(updated, unsent = true))
            return true
        }

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("A running editor must use the guarded update")
        override suspend fun updateOlder(entryId: Long, workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant) =
            throw AssertionError("A running editor cannot fall back to an older-entry PUT")
        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("A running editor cannot create an entry")
        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = throw AssertionError("Unexpected stop")
        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("Unexpected delete")
    }

    private companion object {
        const val TIMEOUT = 5_000L
        val ENTRY = CachedTimeEntry(-1, 20, null, null, "Running", Instant.parse("2026-10-22T22:03:41.123Z"), null, false, emptyList(), emptyList())
    }
}
