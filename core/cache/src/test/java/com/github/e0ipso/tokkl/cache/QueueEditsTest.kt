package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.time.SnappedSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The queue's decisions over tables in memory: what each change queues, what the cache then shows,
 * and how a change to an entry with a refused change folds into it. Room applies the same edits in
 * one transaction; the device tests check that side.
 */
class QueueEditsTest {

    private val tables = InMemoryQueueTables()

    /** The server's spans, which send a Stop under a minute after its start to the Trash. */
    private val spans = object : ServerSpans {
        override fun capturedStop(start: Instant, at: Instant): SnappedSpan? =
            if (Duration.between(start, at) < Duration.ofMinutes(1)) null else SnappedSpan(start, at)
    }

    private val edits = QueueEdits(tables, spans)

    @Test
    fun aStartStopsTheRunningEntryAtTheTapAndQueuesACreateUnderALocalId() {
        tables.upsertEntry(row(id = 10, start = T0, stop = null))
        tables.upsertTag(CachedTag(3, WORKSPACE, "focus"))

        val started = edits.start(WorkspaceId(WORKSPACE), EntryFields("Next", tags = listOf("focus", "new")), T1)

        val queued = tables.changesInOrder()
        assertEquals(listOf(ChangeKind.STOP, ChangeKind.CREATE), queued.map { it.kind })
        assertEquals("the Stop keeps the tap", T1, queued[0].capturedStop)
        assertEquals(T1, tables.entry(10)!!.stop)
        assertEquals("the create's id is minus its sequence", EntryId(-queued[1].sequence), started)
        val running = tables.entry(started.value)!!
        assertNull(running.stop)
        assertEquals("known tags carry their ids", listOf(3L), running.tagIds)
        assertTrue(edits.holdsChanges())
    }

    @Test
    fun aShortStopLeavesTheHistoryAndKeepsAnUndoThatShowsTheZeroLengthRow() {
        tables.upsertEntry(row(id = 10, start = T0, stop = null))

        assertTrue(edits.stop(EntryId(10), fields = null, at = T0.plusSeconds(20)))

        assertNull("the short entry leaves the history", tables.entry(10))
        val stop = tables.changesInOrder().single()
        assertTrue(stop.shortStop)
        assertEquals(T0, stop.stop)
        assertEquals(T0.plusSeconds(20), tables.shortStop(10)!!.capturedAt)

        assertTrue(edits.undoShortStop(EntryId(10)))

        assertEquals(listOf(ChangeKind.STOP, ChangeKind.RESTORE), tables.changesInOrder().map { it.kind })
        assertEquals("the zero-length row shows", T0, tables.entry(10)!!.stop)
        assertNull(tables.shortStop(10))
    }

    @Test
    fun aStopWithTypedFieldsQueuesTheEditBeforeTheStop() {
        tables.upsertEntry(row(id = 10, start = T0, stop = null))

        edits.stop(EntryId(10), EntryFields("Typed"), T1)

        val queued = tables.changesInOrder()
        assertEquals(listOf(ChangeKind.UPDATE, ChangeKind.STOP), queued.map { it.kind })
        assertEquals(listOf("Typed", "Typed"), queued.map { it.description })
        assertFalse("an entry that does not run stops no more", edits.stop(EntryId(10), null, T1))
    }

    @Test
    fun anEditOfAnEntryWithARefusedChangeFoldsIntoItAndLetsItGoAgain() {
        tables.upsertEntry(row(id = 20, start = T0, stop = T1))
        edits.update(EntryId(20), EntryFields("Refused"), EntryTiming.Stopped(T0, T1))
        edits.update(EntryId(20), EntryFields("Behind it"), EntryTiming.Stopped(T0, T1))
        val refused = tables.changesInOrder().first()
        tables.update(refused.copy(held = true, refusal = "Too long"))
        assertNull("the change behind the refused one waits", tables.first())

        edits.update(EntryId(20), EntryFields("Fixed"), EntryTiming.Stopped(T0, T1))

        val queued = tables.changesInOrder().single()
        assertEquals(refused.sequence, queued.sequence)
        assertFalse(queued.held)
        assertEquals("Fixed", queued.description)
        assertEquals("Fixed", tables.entry(20)!!.description)
        assertEquals(queued, tables.first())
    }

    @Test
    fun deletingAnEntryWhoseRefusedCreateNeverReachedTheServerLeavesNothingToSend() {
        val id = edits.create(WorkspaceId(WORKSPACE), EntryFields("Draft"), EntryTiming.Stopped(T0, T1))
        tables.update(tables.changesInOrder().single().copy(held = true))

        assertTrue(edits.delete(id))

        assertEquals(emptyList<PendingChange>(), tables.changesInOrder())
        assertNull(tables.entry(id.value))
        assertFalse(edits.holdsChanges())
    }

    @Test
    fun aSecondDeleteOfTheSameEntryQueuesNothing() {
        tables.upsertEntry(row(id = 30, start = T0, stop = T1))

        assertTrue(edits.delete(EntryId(30)))
        assertFalse(edits.delete(EntryId(30)))
        assertNull(edits.delete(row(id = 30, start = T0, stop = T1).toModel()))

        assertEquals(listOf(ChangeKind.DELETE), tables.changesInOrder().map { it.kind })
    }

    @Test
    fun aRunningEditForAnEntryThatNoLongerRunsChangesNothing() {
        tables.upsertEntry(row(id = 10, start = T0, stop = null))
        val shown = RunningEntryIdentity(EntryId(10), WorkspaceId(WORKSPACE), T0)
        edits.start(WorkspaceId(WORKSPACE), EntryFields("Next"), T1)
        val before = tables.changesInOrder()

        assertFalse(edits.updateRunning(shown, EntryFields("Late"), T0))

        assertEquals(before, tables.changesInOrder())
    }

    @Test
    fun aLocalIdTheServerAnsweredReachesTheServersEntry() {
        tables.upsertEntry(row(id = 55, start = T0, stop = T1))
        tables.map(localId = -1, serverId = 55)

        assertTrue(edits.update(EntryId(-1), EntryFields("Renamed"), EntryTiming.Stopped(T0, T1)))

        assertEquals(55L, tables.changesInOrder().single().entryId)
        assertEquals("Renamed", tables.entry(55)!!.description)
    }

    @Test
    fun discardingARefusedCreateDropsTheChangesBehindItAndItsEntry() {
        tables.upsertEntry(row(id = 10, start = T0, stop = null))
        val id = edits.start(WorkspaceId(WORKSPACE), EntryFields("Next"), T1)
        val create = tables.changesInOrder().last()
        edits.update(id, EntryFields("Renamed"), EntryTiming.Running(T1))
        tables.update(tables.change(create.sequence)!!.copy(held = true))

        assertTrue(edits.discard(create.sequence))

        assertEquals(listOf(ChangeKind.STOP), tables.changesInOrder().map { it.kind })
        assertNull(tables.entry(id.value))
        assertFalse("only a held change can be discarded", edits.discard(tables.changesInOrder().single().sequence))
    }

    private fun row(id: Long, start: Instant, stop: Instant?) = CachedTimeEntry(
        id = id,
        workspaceId = WORKSPACE,
        projectId = null,
        taskId = null,
        description = "Entry $id",
        start = start,
        stop = stop,
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private companion object {
        const val WORKSPACE = 7L
        val T0: Instant = Instant.parse("2026-11-09T08:00:00Z")
        val T1: Instant = T0.plusSeconds(3_600)
    }
}

/** The queue's and the cache's rows in memory, with the semantics of [ChangeDao]'s queries. */
private class InMemoryQueueTables : QueueTables {
    private val changes = sortedMapOf<Long, PendingChange>()
    private val entries = mutableMapOf<Long, CachedTimeEntry>()
    private val shortStops = mutableMapOf<Long, ShortStopUndo>()
    private val mappings = mutableMapOf<Long, Long>()
    private val tags = mutableListOf<CachedTag>()
    private var lastSequence = 0L

    fun changesInOrder(): List<PendingChange> = changes.values.toList()

    fun map(localId: Long, serverId: Long) {
        mappings[localId] = serverId
    }

    fun upsertTag(tag: CachedTag) {
        tags += tag
    }

    override fun first(): PendingChange? {
        val waiting = changes.values.filter { it.held }.map { it.entryId }.toSet()
        return changes.values.firstOrNull { it.entryId !in waiting }
    }

    override fun heldFor(entryId: Long) = changes.values.firstOrNull { it.entryId == entryId && it.held }

    override fun change(sequence: Long) = changes[sequence]

    override fun release(sequence: Long): Int {
        val held = changes[sequence]?.takeIf { it.held } ?: return 0
        changes[sequence] = held.copy(held = false, refusal = null)
        return 1
    }

    override fun deleteFrom(entryId: Long, sequence: Long): Int {
        val gone = changes.values.filter { it.entryId == entryId && it.sequence >= sequence }
        gone.forEach { changes.remove(it.sequence) }
        return gone.size
    }

    override fun changesFor(entryId: Long) = changes.values.filter { it.entryId == entryId }

    override fun insert(change: PendingChange): Long {
        val sequence = ++lastSequence
        changes[sequence] = change.copy(sequence = sequence)
        return sequence
    }

    override fun update(change: PendingChange) {
        changes[change.sequence] = change
    }

    override fun delete(sequence: Long) {
        changes.remove(sequence)
    }

    override fun serverId(localId: Long) = mappings[localId]

    override fun queuedDelete(entryId: Long) =
        changes.values.lastOrNull { it.entryId == entryId && it.kind == ChangeKind.DELETE }

    override fun shortStop(entryId: Long) = shortStops[entryId]

    override fun upsertShortStop(stop: ShortStopUndo) {
        shortStops[stop.entryId] = stop
    }

    override fun deleteShortStop(entryId: Long) {
        shortStops.remove(entryId)
    }

    override fun entry(id: Long) = entries[id]

    override fun serverEntryIds(from: Instant, to: Instant) =
        entries.values.filter { it.id > 0 && it.start in from..to }.map { it.id }

    override fun runningEntries() = entries.values.filter { it.stop == null }

    override fun upsertEntry(entry: CachedTimeEntry) {
        entries[entry.id] = entry
    }

    override fun deleteEntry(id: Long) {
        entries.remove(id)
    }

    override fun tags(workspaceId: Long) = tags.filter { it.workspaceId == workspaceId }
}
