package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The queue's rows and the cached entries and tags, as one change to the queue reads and writes
 * them inside a transaction. [ChangeDao] keeps them in Room; the queue's JVM tests keep them in
 * memory. Every call blocks on disk I/O.
 */
@Suppress("TooManyFunctions") // One function per query, as the Room DAO that implements it has.
interface QueueTables {

    /** The oldest change that can go out, or null when none can: held ones wait (docs/PRD.md O6). */
    fun first(): PendingChange?

    fun heldFor(entryId: Long): PendingChange?

    fun change(sequence: Long): PendingChange?

    /** Lets a held change go out again. Returns how many it let go: none when it is not held. */
    fun release(sequence: Long): Int

    /** Deletes the queued changes to the entry [entryId] from [sequence] on. Returns how many. */
    fun deleteFrom(entryId: Long, sequence: Long): Int

    /** The queued changes to the entry [entryId], oldest first. */
    fun changesFor(entryId: Long): List<PendingChange>

    /** Queues [change] and returns its sequence. */
    fun insert(change: PendingChange): Long

    fun update(change: PendingChange)

    fun delete(sequence: Long)

    fun queuedDelete(entryId: Long): PendingChange?

    /** The server's id for the entry the phone created under [localId], once the server answered. */
    fun serverId(localId: Long): Long?

    fun entry(id: Long): CachedTimeEntry?

    /** The ids of the cached entries from the server, not made on the phone, that start from [from] to [to]. */
    fun serverEntryIds(from: Instant, to: Instant): List<Long>

    fun runningEntries(): List<CachedTimeEntry>

    fun upsertEntry(entry: CachedTimeEntry)

    fun deleteEntry(id: Long)

    fun tags(workspaceId: Long): List<CachedTag>

    fun shortStop(entryId: Long): ShortStopUndo?

    fun upsertShortStop(stop: ShortStopUndo)

    fun deleteShortStop(entryId: Long)
}

/**
 * Where [ChangeQueue] keeps its changes: [transaction] applies one change's edits to the
 * [QueueTables] atomically, and the flows say what the queue holds. [RoomChangeStore] keeps them in
 * the cache's database.
 */
interface QueueStorage {

    /** How many changes the server has not received, held ones included. */
    val unsentCount: Flow<Int>

    /** The changes the server refused that wait for the user, oldest first. */
    val heldChanges: Flow<List<PendingChange>>

    val heldCount: Flow<Int>

    /** The latest short Stop that Undo can take back, or null. */
    val latestShortStop: Flow<ShortStopUndo?>

    /** Runs [edit] over the tables in one transaction. Blocks on disk I/O. */
    fun <T> transaction(edit: QueueTables.() -> T): T
}

/** The id the cache holds [entryId] under: the server's once it answered the local create, or [entryId]. */
internal fun QueueTables.resolve(entryId: EntryId): Long = entryId.local?.let { serverId(it.value) } ?: entryId.value

/**
 * What one change does to the queue and the cached rows, as [ChangeQueue] describes each one: the
 * queue's decisions, written against [tables] and free of Room, so the caller decides how they
 * commit. [ChangeQueue] runs each inside one transaction.
 */
internal class QueueEdits(private val tables: QueueTables, private val spans: ServerSpans) {

    /** Whether the queue holds a change that can go out. */
    fun holdsChanges(): Boolean = tables.first() != null

    /** Stops every cached running entry at [at] and queues a start in [workspaceId]. Returns its local id. */
    fun start(workspaceId: WorkspaceId, fields: EntryFields, at: Instant): EntryId {
        tables.runningEntries().forEach { running -> stopAt(running, at) }
        return EntryId(queueCreate(workspaceId.value, fields, EntryTiming.Running(at)))
    }

    /**
     * Stops the running entry [entryId] at [at] with [fields]. With [fields], they go in a change
     * of their own before the captured Stop. Returns false when it is not cached or not running.
     */
    fun stop(entryId: EntryId, fields: EntryFields?, at: Instant): Boolean {
        val running = tables.entry(tables.resolve(entryId))?.takeIf { it.stop == null } ?: return false
        val stopped = if (fields == null) {
            running
        } else {
            entry(running.id, running.workspaceId, fields, EntryTiming.Running(running.start))
        }
        if (stopped != running) enqueue(ChangeKind.UPDATE, stopped)
        stopAt(stopped, at)
        return true
    }

    fun undoShortStop(entryId: EntryId): Boolean {
        val id = tables.resolve(entryId)
        val undo = tables.shortStop(id) ?: return false
        tables.insert(PendingChange.of(ChangeKind.RESTORE, undo.entry))
        tables.upsertEntry(undo.entry)
        tables.deleteShortStop(id)
        return true
    }

    fun dismissShortStop(entryId: EntryId) = tables.deleteShortStop(tables.resolve(entryId))

    fun create(workspaceId: WorkspaceId, fields: EntryFields, timing: EntryTiming.Ended) =
        EntryId(queueCreate(workspaceId.value, fields, timing))

    fun update(entryId: EntryId, fields: EntryFields, timing: EntryTiming): Boolean {
        val current = tables.entry(tables.resolve(entryId)) ?: return false
        if (current.stop != null && timing is EntryTiming.Running) {
            tables.runningEntries().forEach { stopAt(it, timing.start) }
        }
        enqueue(ChangeKind.UPDATE, entry(current.id, current.workspaceId, fields, timing))
        return true
    }

    /** Queues [second] as a new entry in [entryId]'s workspace, then ends [entryId] at [first]. False when uncached. */
    fun split(
        entryId: EntryId,
        fields: EntryFields,
        first: EntryTiming.Stopped,
        second: EntryTiming.Stopped,
    ): Boolean {
        val current = tables.entry(tables.resolve(entryId)) ?: return false
        queueCreate(current.workspaceId, fields, second)
        return update(entryId, fields, first)
    }

    /** Sets the running entry's fields and start, while it is still the one [identity] names. */
    fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean {
        val current = activeEntry(identity) ?: return false
        enqueue(ChangeKind.UPDATE, entry(current.id, current.workspaceId, fields, EntryTiming.Running(start)))
        return true
    }

    /**
     * The cache's current running entry while it is the one [identity] names, or null. The latest
     * start wins, the same instant broken by id, as the cache orders its current entry.
     */
    fun activeEntry(identity: RunningEntryIdentity): CachedTimeEntry? {
        val id = tables.resolve(identity.entryId)
        val current = tables.runningEntries().maxWithOrNull(
            compareBy<CachedTimeEntry> { it.start }.thenBy { it.id },
        ) ?: return null
        if (current.id != id || current.workspaceId != identity.workspaceId.value) return null
        // Only a remapped local create may have lost fractional seconds in the server echo.
        val sameStart = current.start == identity.start ||
            (id != identity.entryId.value && current.start == identity.start.truncatedTo(ChronoUnit.SECONDS))
        return current.takeIf { sameStart }
    }

    fun updateOlder(entryId: EntryId, workspaceId: WorkspaceId, fields: EntryFields, timing: EntryTiming.Ended) {
        val id = tables.resolve(entryId)
        val workspace = tables.entry(id)?.workspaceId ?: workspaceId.value
        enqueue(ChangeKind.UPDATE, entry(id, workspace, fields, timing))
    }

    fun delete(entryId: EntryId): Boolean {
        val id = tables.resolve(entryId)
        if (tables.queuedDelete(id) != null) return false
        val current = tables.entry(id) ?: return false
        enqueue(ChangeKind.DELETE, current)
        return true
    }

    fun delete(entry: TimeEntry): TimeEntry? {
        val id = tables.resolve(entry.id)
        if (tables.queuedDelete(id) != null) return null
        val current = tables.entry(id) ?: entry.toCached().copy(id = id).takeIf { id > 0 } ?: return null
        enqueue(ChangeKind.DELETE, current)
        return current.toModel()
    }

    fun sendAgain(sequence: Long): Boolean = tables.release(sequence) > 0

    fun discard(sequence: Long): Boolean {
        val held = tables.change(sequence)?.takeIf { it.held } ?: return false
        tables.deleteFrom(held.entryId, held.sequence)
        if (held.kind == ChangeKind.CREATE) {
            tables.deleteEntry(held.entryId)
            tables.deleteShortStop(held.entryId)
        }
        return true
    }

    /** Queues the captured Stop of [running] at [at], with an Undo record for a short one. */
    private fun stopAt(running: CachedTimeEntry, at: Instant) {
        val span = spans.capturedStop(running.start, at)
        val stopped = if (span == null) {
            val point = spans.stopped(running.start, running.start, false).start
            running.copy(start = point, stop = point)
        } else {
            running.copy(start = span.start, stop = span.stop)
        }
        enqueue(ChangeKind.STOP, stopped, capturedAt = at, shortStop = span == null)
        if (span == null) tables.upsertShortStop(ShortStopUndo(stopped.id, at, stopped))
    }

    /**
     * Queues a create, with the server's entries the phone holds around its start, which a lost
     * post's lookup must not take for it (see [PendingChange.existingIds]).
     */
    private fun queueCreate(workspaceId: Long, fields: EntryFields, timing: EntryTiming): Long {
        val existing = serverEntriesAround(timing.start)
        return enqueue(ChangeKind.CREATE, entry(id = 0, workspaceId, fields, timing), existing)
    }

    /**
     * Queues a change that leaves [entry] as given and applies it to the cache, or folds it into
     * the held change to the same entry. A create gets its local id here. Returns the entry's id.
     */
    private fun enqueue(
        kind: ChangeKind,
        entry: CachedTimeEntry,
        existingIds: List<Long> = emptyList(),
        capturedAt: Instant? = null,
        shortStop: Boolean = false,
    ): Long {
        if (kind != ChangeKind.STOP && kind != ChangeKind.RESTORE) {
            tables.heldFor(entry.id)?.let { held -> return foldInto(held, kind, entry, capturedAt, shortStop) }
        }
        val change = PendingChange.of(kind, entry)
            .copy(existingIds = existingIds, capturedStop = capturedAt, shortStop = shortStop)
        val sequence = tables.insert(change)
        val queued = if (kind == ChangeKind.CREATE) {
            change.copy(sequence = sequence, entryId = -sequence).also(tables::update)
        } else {
            change.copy(sequence = sequence)
        }
        showAsQueued(queued.entryId, queued.cachedEntry())
        return queued.entryId
    }

    /**
     * Folds a change of [kind] that leaves [entry] as given into [held], the refused change to the
     * same entry, and lets it go out again. The changes queued behind [held] go, since this one
     * carries every field after them, unless a captured Stop follows it. A held create stays a
     * create, as the server has no entry yet, and a delete of it leaves nothing to send. Returns the
     * entry's id.
     */
    private fun foldInto(
        held: PendingChange,
        kind: ChangeKind,
        entry: CachedTimeEntry,
        capturedAt: Instant?,
        shortStop: Boolean,
    ): Long {
        // A captured Stop is a separate PATCH and cannot be replaced by the full fields of an
        // edit. Keep it and the later queue order if the user fixes an earlier held change.
        val laterCapturedStop = tables.changesFor(held.entryId).any {
            it.sequence > held.sequence && it.kind == ChangeKind.STOP && it.capturedStop != null
        }
        if (!laterCapturedStop) tables.deleteFrom(held.entryId, held.sequence + 1)
        if (held.kind == ChangeKind.CREATE && kind == ChangeKind.DELETE) {
            tables.delete(held.sequence)
            tables.deleteEntry(held.entryId)
            return held.entryId
        }
        // An edit can move a create to another day. Its lost-post lookup must also exclude
        // the server entries already known around the new start.
        val existingIds = if (held.kind == ChangeKind.CREATE) {
            (held.existingIds + serverEntriesAround(entry.start)).distinct()
        } else {
            held.existingIds
        }
        val folded = PendingChange.of(if (held.kind == ChangeKind.CREATE) ChangeKind.CREATE else kind, entry)
            .copy(
                sequence = held.sequence,
                entryId = held.entryId,
                maybeSent = held.maybeSent,
                existingIds = existingIds,
                capturedStop = capturedAt,
                shortStop = shortStop,
            )
        tables.update(folded)
        showAsQueued(folded.entryId, tables.changesFor(held.entryId).last().cachedEntry())
        return folded.entryId
    }

    /** The cache shows the entry [entryId] as [row], the latest queued change leaves it, or not at all. */
    private fun showAsQueued(entryId: Long, row: CachedTimeEntry?) {
        if (row == null) tables.deleteEntry(entryId) else tables.upsertEntry(row)
    }

    /** The server's entries the cache holds within [ChangeSender.LOOKUP_MARGIN] of [start]. */
    private fun serverEntriesAround(start: Instant): List<Long> {
        val margin = ChangeSender.LOOKUP_MARGIN
        return tables.serverEntryIds(start.minus(margin), start.plus(margin))
    }

    /** The cache's row for the entry with [fields] and [timing], whose flat columns keep the timing. */
    private fun entry(id: Long, workspaceId: Long, fields: EntryFields, timing: EntryTiming): CachedTimeEntry {
        val tags = fields.tags.distinct()
        val known = tables.tags(workspaceId).associate { it.name to it.id }
        return CachedTimeEntry(
            id = id,
            workspaceId = workspaceId,
            projectId = fields.projectId?.value,
            taskId = fields.taskId?.value,
            description = fields.description,
            start = timing.start,
            stop = (timing as? EntryTiming.Ended)?.stop,
            billable = fields.billable,
            tags = tags,
            tagIds = tags.mapNotNull(known::get),
            allDay = timing is EntryTiming.AllDay,
            day = (timing as? EntryTiming.AllDay)?.day,
        )
    }
}
