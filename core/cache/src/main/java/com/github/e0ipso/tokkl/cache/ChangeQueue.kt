package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/** The changes the server refused, which wait for the user, as [ChangeQueue] keeps them. */
interface HeldChanges {

    /** The held changes, oldest first. */
    val held: Flow<List<HeldChange>>

    /** Lets the held change [sequence] go out as it is. Returns false when it is not held. */
    suspend fun sendAgain(sequence: Long): Boolean

    /** Drops the held change [sequence] and the changes behind it. Returns false when it is not held. */
    suspend fun discard(sequence: Long): Boolean
}

/**
 * A change the server refused, as the "needs attention" list shows it: what it does, the entry as
 * the change leaves it, or as it was for a delete, and the server's reason, if it gave one.
 */
data class HeldChange(val sequence: Long, val kind: ChangeKind, val entry: TimeEntry, val refusal: String?)

/** The latest short Stop, which Undo can take back, of the entry [entryId] at [capturedAt]. */
data class ShortStopOffer(val entryId: EntryId, val capturedAt: Instant)

/**
 * Starts, stops, creates, edits and deletes time entries on the phone (docs/PRD.md O1). Each call
 * changes the cached entry at once and queues the change, in one transaction, then asks [onQueued]
 * to send the queue while it holds a change. No call waits for the network. The cache marks the
 * entry unsent until the server has every queued change to it, and keeps showing the change over
 * refreshed rows until then. The notification, widget and tile read the running entry from the
 * cache, so they follow at once.
 *
 * Start and Stop use the tap time, which each call reads from [clock] before anything else, so
 * call them from the tap's handler. A timer started offline keeps that start on the server. A
 * captured Stop predicts the span the server will store. A short Stop removes the entry from
 * local history and keeps an Undo offer on disk. Undo queues a restore after the Stop and shows
 * the server's zero-length stopped row at once, including offline.
 *
 * An entry created on the phone gets a negative local id. The calls take it, before and after the
 * server has answered the create with its own id.
 *
 * A no-time entry's change carries its flag and day ([TimeEntry.allDay] and
 * [TimeEntry.day]), and so does every cache row or queued change rebuilt from it. Running
 * entries are never no-time ones.
 *
 * Pause, Resume and Pomodoro sessions belong to the server's timer state and never pass through
 * the queue (docs/PRD.md T1). A queued Stop of the server's resumed clone or of its open pomodoro
 * is an ordinary Stop, and the server ends the chain or the session when it gets it.
 *
 * A change the server refused waits for the user in the "needs attention" list ([held],
 * docs/PRD.md O6), and later changes to its entry wait behind it. A change made to that entry
 * meanwhile, such as the user's fix in the entry form, folds into the held change and lets it go
 * out again: every change carries all of the entry's fields, so the newest one says everything the
 * server needs. [sendAgain] lets a held change go out as it is, and [discard] drops it.
 *
 * @param store the queue's tables, which [ChangeSender] reads through the same store.
 * @param io where the database writes run.
 * @param spans the spans the server stores for a Stop: [ServerSpans.capturedStop] for a Stop now,
 *   and [ServerSpans.stopped] for a short Stop's snapped zero point.
 */
class ChangeQueue(
    private val store: RoomChangeStore,
    private val io: CoroutineContext,
    private val onQueued: QueuedListener,
    private val clock: Clock,
    private val spans: ServerSpans,
) : EntryChanges,
    RunningEntryChanges,
    HeldChanges,
    UnsentChanges {

    private val dao = store.dao

    /** How many changes the server has not received (docs/PRD.md O5), held ones included. */
    val unsentCount: Flow<Int> = dao.count()

    /** How many changes the server has not received, held ones included: [unsentCount] now. */
    override suspend fun unsentNow(): Int = unsentCount.first()

    /** The changes the server refused, which wait for the user, oldest first (docs/PRD.md O6). */
    override val held: Flow<List<HeldChange>> = dao.held().map { changes -> changes.map { it.toHeld() } }

    /** How many changes the server refused wait for the user. */
    val heldCount: Flow<Int> = dao.heldCount()

    /** Latest short Stop, retained across process death until Undo or dismissal. */
    val shortStopUndo: Flow<ShortStopOffer?> = dao.latestShortStop().map { undo ->
        undo?.let { ShortStopOffer(EntryId(it.entryId), it.capturedAt) }
    }

    /**
     * Starts a timer in [workspaceId] with [fields], at the tap time. A cached running entry
     * stops at that time first, as the server does when a timer starts, so the two are stored
     * back to back. Returns the new entry's local id. Pass the account's
     * `AccountSettings.defaultWorkspaceId`, which the phone keeps, so it is there offline too.
     */
    override suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId {
        val at = clock.instant()
        return queue {
            dao.runningEntries().forEach { running -> stopAt(running, at) }
            EntryId(queueCreate(workspaceId.value, fields, at, stop = null))
        }
    }

    /**
     * Stops the running entry [entryId] at the tap time, or at its start if the tap came earlier,
     * on the grid as Tokkl's captured Stop stores it. A short span enters the trash.
     * With [fields], the stop also sets them, in the same change, such as a description still
     * being typed when Stop was tapped. Returns false when the entry is not cached or not running.
     */
    override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean {
        val at = clock.instant()
        return queue { stopRunning(entryId, fields, at) }
    }

    /** Queues a restore behind the short Stop and shows its zero-length row immediately offline. */
    suspend fun undoShortStop(entryId: EntryId): Boolean = queue {
        val id = resolve(entryId)
        val undo = dao.shortStop(id) ?: return@queue false
        val change = PendingChange.of(ChangeKind.RESTORE, undo.entry)
        dao.insert(change)
        dao.upsertEntry(undo.entry)
        dao.deleteShortStop(id)
        true
    }

    /** Dismisses only the offer; the queued Stop and server trash remain intact. */
    suspend fun dismissShortStop(entryId: EntryId) = withContext(io) { dao.deleteShortStop(resolve(entryId)) }

    /**
     * Creates a stopped entry in [workspaceId], as a manual entry does, or a no-time entry on
     * [noTimeDay] (see [EntryChanges]). Returns its local id. Pass the account's
     * `AccountSettings.defaultWorkspaceId`, as for [start].
     */
    override suspend fun create(
        workspaceId: WorkspaceId,
        fields: EntryFields,
        start: Instant,
        stop: Instant,
        noTimeDay: LocalDate?,
    ): EntryId {
        require(!stop.isBefore(start)) { "The stop comes before the start." }
        return queue { EntryId(queueCreate(workspaceId.value, fields, start, stop, noTimeDay)) }
    }

    /**
     * Sets every field of the cached entry [entryId]. A null [stop] makes it run, and a stopped
     * entry made to run stops the running one at its start first, as a Start would. With
     * [noTimeDay] it is a no-time entry on that day (see [EntryChanges]), which never runs.
     * Without it the entry is timed, so a null [noTimeDay] turns a no-time entry's times on, with
     * the caller's real [start] and [stop]. Returns false when the entry is not cached.
     */
    override suspend fun update(
        entryId: EntryId,
        fields: EntryFields,
        start: Instant,
        stop: Instant?,
        noTimeDay: LocalDate?,
    ): Boolean {
        require(stop == null || !stop.isBefore(start)) { "The stop comes before the start." }
        require(noTimeDay == null || stop != null) { "A no-time entry never runs." }
        return queue {
            val current = dao.entry(resolve(entryId)) ?: return@queue false
            if (current.stop != null && stop == null) dao.runningEntries().forEach { stopAt(it, start) }
            enqueue(ChangeKind.UPDATE, entry(current.id, current.workspaceId, fields, start, stop, noTimeDay))
            true
        }
    }

    override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? = withContext(io) {
        store.transaction { activeEntry(identity) }?.toModel()
    }

    override suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean =
        queue {
            val current = activeEntry(identity) ?: return@queue false
            enqueue(ChangeKind.UPDATE, entry(current.id, current.workspaceId, fields, start, stop = null))
            true
        }

    private fun activeEntry(identity: RunningEntryIdentity): CachedTimeEntry? {
        val id = resolve(identity.entryId)
        // Match the cache's current-entry ordering, including a same-instant replacement.
        val current = dao.runningEntries().maxWithOrNull(
            compareBy<CachedTimeEntry> { it.start }.thenBy { it.id },
        ) ?: return null
        if (current.id != id || current.workspaceId != identity.workspaceId.value) return null
        // Only a remapped local create may have lost fractional seconds in the server echo.
        val sameStart = current.start == identity.start ||
            (
                id != identity.entryId.value && current.start == identity.start.truncatedTo(
                    java.time.temporal.ChronoUnit.SECONDS,
                )
                )
        return current.takeIf { sameStart }
    }

    /**
     * Sets every field of the stopped entry [entryId] in [workspaceId], which the cache need not
     * hold, such as an entry from the older days the history pages from the server. The change
     * goes to the queue with every field, so the sender's PUT sends it as it is, and the cache
     * holds the entry from now on, marked unsent until the server has it. A refresh drops it again
     * when it starts before the cache's window. The workspace of a cached entry wins over
     * [workspaceId]. [noTimeDay] is as for [update].
     */
    override suspend fun updateOlder(
        entryId: EntryId,
        workspaceId: WorkspaceId,
        fields: EntryFields,
        start: Instant,
        stop: Instant,
        noTimeDay: LocalDate?,
    ) {
        require(!stop.isBefore(start)) { "The stop comes before the start." }
        queue {
            val id = resolve(entryId)
            enqueue(
                ChangeKind.UPDATE,
                entry(id, dao.entry(id)?.workspaceId ?: workspaceId.value, fields, start, stop, noTimeDay),
            )
        }
    }

    /**
     * The id the cache holds the entry [entryId] under: the server's once the server has answered
     * the create of an entry made on the phone, or [entryId] as it is.
     */
    override suspend fun currentId(entryId: EntryId): EntryId = withContext(io) { EntryId(resolve(entryId)) }

    /**
     * Deletes the cached entry [entryId]. Returns false when it is missing or a delete is already
     * queued.
     */
    override suspend fun delete(entryId: EntryId): Boolean = queue {
        val id = resolve(entryId)
        if (dao.queuedDelete(id) != null) return@queue false
        val current = dao.entry(id) ?: return@queue false
        enqueue(ChangeKind.DELETE, current)
        true
    }

    /**
     * Resolves identity, prefers the current cache row and guards a duplicate in the same
     * transaction as DELETE. A missing local create is never recreated from a stale snapshot.
     * Returns the exact deleted fields and identity for reconciliation and Undo.
     */
    override suspend fun delete(entry: TimeEntry): TimeEntry? = queue {
        val id = resolve(entry.id)
        if (dao.queuedDelete(id) != null) return@queue null
        val current = dao.entry(id) ?: entry.toCached().copy(id = id).takeIf { id > 0 } ?: return@queue null
        enqueue(ChangeKind.DELETE, current)
        current.toModel()
    }

    /**
     * Lets the held change [sequence] go out again as it is, such as after the cause of its
     * refusal was fixed on the server. Returns false when it is not held.
     */
    override suspend fun sendAgain(sequence: Long): Boolean = queue { dao.release(sequence) > 0 }

    /**
     * Drops the held change [sequence] and the later changes to its entry that wait behind it,
     * since each of them builds on it. An entry the phone created goes from the cache with them;
     * any other keeps its row until the next refresh brings the server's. Returns false when the
     * change is not held.
     */
    override suspend fun discard(sequence: Long): Boolean = queue {
        val held = dao.change(sequence)?.takeIf { it.held } ?: return@queue false
        dao.deleteFrom(held.entryId, held.sequence)
        if (held.kind == ChangeKind.CREATE) {
            dao.deleteEntry(held.entryId)
            dao.deleteShortStop(held.entryId)
        }
        true
    }

    /**
     * Runs [change] in one transaction on [io], then asks for the queue to be sent while it holds
     * a change that can go out. A call that queued nothing, such as a stop of an entry that does
     * not run, leaves an empty queue alone.
     */
    private suspend fun <T> queue(change: () -> T): T {
        // A Stop at process start, as from the notification, snaps to the account's grid.
        spans.ready()
        val (result, holdsChanges) = withContext(io) {
            store.transaction { change() to (dao.first() != null) }
        }
        if (holdsChanges) onQueued.queued()
        return result
    }

    /**
     * Stops the running entry [entryId] at [at] with [fields], as [stop] describes. With
     * [fields], they go in a change of their own before the captured Stop.
     */
    private fun stopRunning(entryId: EntryId, fields: EntryFields?, at: Instant): Boolean {
        val running = dao.entry(resolve(entryId))?.takeIf { it.stop == null } ?: return false
        val stopped = if (fields == null) {
            running
        } else {
            entry(
                running.id,
                running.workspaceId,
                fields,
                running.start,
                stop = null,
            )
        }
        if (stopped != running) enqueue(ChangeKind.UPDATE, stopped)
        stopAt(stopped, at)
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
        if (span == null) dao.upsertShortStop(ShortStopUndo(stopped.id, at, stopped))
    }

    /**
     * Queues a create, with the server's entries the phone holds around its start, which a lost
     * post's lookup must not take for it (see [PendingChange.existingIds]). A no-time entry's
     * start is local midnight of [noTimeDay].
     */
    private fun queueCreate(
        workspaceId: Long,
        fields: EntryFields,
        start: Instant,
        stop: Instant?,
        noTimeDay: LocalDate? = null,
    ): Long {
        val margin = ChangeSender.LOOKUP_MARGIN
        val existing = dao.serverEntryIds(start.minus(margin), start.plus(margin))
        return enqueue(ChangeKind.CREATE, entry(id = 0, workspaceId, fields, start, stop, noTimeDay), existing)
    }

    /**
     * Queues a change that leaves [entry] as given and applies it to the cache. A create gets its
     * local id here. Returns the entry's id.
     */
    private fun enqueue(
        kind: ChangeKind,
        entry: CachedTimeEntry,
        existingIds: List<Long> = emptyList(),
        capturedAt: Instant? = null,
        shortStop: Boolean = false,
    ): Long {
        if (kind != ChangeKind.STOP && kind != ChangeKind.RESTORE) {
            dao.heldFor(entry.id)?.let { held -> return foldInto(held, kind, entry, capturedAt, shortStop) }
        }
        val change = PendingChange.of(
            kind,
            entry,
        ).copy(existingIds = existingIds, capturedStop = capturedAt, shortStop = shortStop)
        val sequence = dao.insert(change)
        val queued = if (kind == ChangeKind.CREATE) {
            change.copy(sequence = sequence, entryId = -sequence).also(dao::update)
        } else {
            change.copy(sequence = sequence)
        }
        val row = queued.cachedEntry()
        if (row == null) dao.deleteEntry(queued.entryId) else dao.upsertEntry(row)
        return queued.entryId
    }

    /**
     * Folds a change of [kind] that leaves [entry] as given into [held], the refused change to the
     * same entry, and lets it go out again. The changes queued behind [held] go, since this one
     * carries every field after them. A held create stays a create, as the server has no entry
     * yet, and a delete of it leaves nothing to send. Returns the entry's id.
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
        val laterCapturedStop = dao.changesFor(held.entryId).any {
            it.sequence > held.sequence && it.kind == ChangeKind.STOP && it.capturedStop != null
        }
        if (!laterCapturedStop) dao.deleteFrom(held.entryId, held.sequence + 1)
        if (held.kind == ChangeKind.CREATE && kind == ChangeKind.DELETE) {
            dao.delete(held.sequence)
            dao.deleteEntry(held.entryId)
            return held.entryId
        }
        // An edit can move a create to another day. Its lost-post lookup must also exclude
        // the server entries already known around the new start.
        val existingIds = if (held.kind == ChangeKind.CREATE) {
            val margin = ChangeSender.LOOKUP_MARGIN
            (held.existingIds + dao.serverEntryIds(entry.start.minus(margin), entry.start.plus(margin))).distinct()
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
        dao.update(folded)
        val row = dao.changesFor(held.entryId).last().cachedEntry()
        if (row == null) dao.deleteEntry(folded.entryId) else dao.upsertEntry(row)
        return folded.entryId
    }

    /** The entry with [fields], [start] and [stop]. It is a no-time one on [noTimeDay] when given, else timed. */
    private fun entry(
        id: Long,
        workspaceId: Long,
        fields: EntryFields,
        start: Instant,
        stop: Instant?,
        noTimeDay: LocalDate? = null,
    ): CachedTimeEntry {
        val tags = fields.tags.distinct()
        val known = dao.tags(workspaceId).associate { it.name to it.id }
        return CachedTimeEntry(
            id = id,
            workspaceId = workspaceId,
            projectId = fields.projectId?.value,
            taskId = fields.taskId?.value,
            description = fields.description,
            start = start,
            stop = stop,
            billable = fields.billable,
            tags = tags,
            tagIds = tags.mapNotNull(known::get),
            allDay = noTimeDay != null,
            day = noTimeDay,
        )
    }

    /** The server's id for a local id the server has answered, or [entryId] as it is. */
    private fun resolve(entryId: EntryId): Long = store.resolve(entryId)
}

/** Hears that a change went into the queue. [SendOnQueue] starts the sends; the queue's device tests hear nothing. */
fun interface QueuedListener {
    fun queued()
}

/** How many changes the server has not received, held ones included, as [ChangeQueue] counts them. */
fun interface UnsentChanges {
    suspend fun unsentNow(): Int
}
