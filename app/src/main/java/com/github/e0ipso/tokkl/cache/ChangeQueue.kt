package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.time.SnappedSpan
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The fields of a time entry that the user picks, apart from its workspace and times. */
data class EntryFields(
    val description: String = "",
    val projectId: Long? = null,
    val taskId: Long? = null,
    /** Tag names. A name the cache does not know is kept, and the server creates the tag. */
    val tags: List<String> = emptyList(),
    val billable: Boolean = false,
)

/**
 * The changes screens make to time entries, as [ChangeQueue] makes them: on the phone at once,
 * then queued for the server. A screen's model takes this, so a JVM test can stand in for the
 * database. See [ChangeQueue] for what each call does.
 */
interface EntryChanges {
    suspend fun start(workspaceId: Long, fields: EntryFields): Long

    suspend fun stop(entryId: Long, fields: EntryFields? = null): Boolean

    suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long

    suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean

    /**
     * Sets every field of the stopped entry [entryId] in [workspaceId], whether the phone holds it
     * or not, such as an entry from the older days the history pages from the server. A stand-in
     * with no cache of its own treats it as [update].
     */
    suspend fun updateOlder(entryId: Long, workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant) {
        update(entryId, fields, start, stop)
    }

    suspend fun delete(entryId: Long): Boolean
}

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
 * stop puts the entry where [stopSpan] says the server stores a timer stopped at the tap, so the
 * phone shows that span at once and the server keeps it as sent.
 *
 * An entry created on the phone gets a negative local id. The calls take it, before and after the
 * server has answered the create with its own id.
 *
 * A change the server refused waits for the user in the "needs attention" list ([held],
 * docs/PRD.md O6), and later changes to its entry wait behind it. A change made to that entry
 * meanwhile, such as the user's fix in the entry form, folds into the held change and lets it go
 * out again: every change carries all of the entry's fields, so the newest one says everything the
 * server needs. [sendAgain] lets a held change go out as it is, and [discard] drops it.
 *
 * @param io where the database writes run.
 * @param stopSpan the span the server stores for a running entry from `start` stopped at `at`:
 *   with `oneStep` for a Stop, as Tokkl's stop endpoint does, and without it for a start that
 *   stops the running entry. Tokkl puts both ends on the grid of the account's time resolution
 *   (`AccountTime.snapStop`), and Toggl keeps the tap time.
 */
class ChangeQueue(
    private val database: CacheDatabase,
    private val io: CoroutineContext,
    private val onQueued: () -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val stopSpan: (start: Instant, at: Instant, oneStep: Boolean) -> SnappedSpan = { start, at, _ ->
        SnappedSpan(start, maxOf(at, start))
    },
) : EntryChanges {

    private val dao = database.changes()

    /** How many changes the server has not received (docs/PRD.md O5), held ones included. */
    val unsentCount: Flow<Int> = dao.count()

    /** The changes the server refused, which wait for the user, oldest first (docs/PRD.md O6). */
    val held: Flow<List<PendingChange>> = dao.held()

    /** How many changes the server refused wait for the user. */
    val heldCount: Flow<Int> = dao.heldCount()

    /**
     * Starts a timer in [workspaceId] with [fields], at the tap time. A cached running entry
     * stops at that time first, as the server does when a timer starts, so the two are stored
     * back to back. Returns the new entry's local id. Pass the account's
     * `AccountSettings.defaultWorkspaceId`, which the phone keeps, so it is there offline too.
     */
    override suspend fun start(workspaceId: Long, fields: EntryFields): Long {
        val at = clock.instant()
        return queue {
            dao.runningEntries().forEach { running -> stopAt(running, at, oneStep = false) }
            queueCreate(workspaceId, fields, at, stop = null)
        }
    }

    /**
     * Stops the running entry [entryId] at the tap time, or at its start if the tap came earlier,
     * on the grid as the web app's Stop is stored: at least one step long on Tokkl.
     * With [fields], the stop also sets them, in the same change, such as a description still
     * being typed when Stop was tapped. Returns false when the entry is not cached or not running.
     */
    override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean {
        val at = clock.instant()
        return queue {
            val running = dao.entry(resolve(entryId))?.takeIf { it.stop == null } ?: return@queue false
            val stopped = if (fields == null) running else entry(running.id, running.workspaceId, fields, running.start, stop = null)
            stopAt(stopped, at, oneStep = true)
            true
        }
    }

    /**
     * Creates a stopped entry in [workspaceId], as a manual entry does. Returns its local id. Pass
     * the account's `AccountSettings.defaultWorkspaceId`, as for [start].
     */
    override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
        require(!stop.isBefore(start)) { "The stop comes before the start." }
        return queue { queueCreate(workspaceId, fields, start, stop) }
    }

    /**
     * Sets every field of the cached entry [entryId]. A null [stop] makes it run. Returns false
     * when the entry is not cached.
     */
    override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean {
        require(stop == null || !stop.isBefore(start)) { "The stop comes before the start." }
        return queue {
            val current = dao.entry(resolve(entryId)) ?: return@queue false
            enqueue(ChangeKind.UPDATE, entry(current.id, current.workspaceId, fields, start, stop))
            true
        }
    }

    /**
     * Sets every field of the stopped entry [entryId] in [workspaceId], which the cache need not
     * hold, such as an entry from the older days the history pages from the server. The change
     * goes to the queue with every field, so the sender's PUT sends it as it is, and the cache
     * holds the entry from now on, marked unsent until the server has it. A refresh drops it again
     * when it starts before the cache's window. The workspace of a cached entry wins over
     * [workspaceId].
     */
    override suspend fun updateOlder(entryId: Long, workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant) {
        require(!stop.isBefore(start)) { "The stop comes before the start." }
        queue {
            val id = resolve(entryId)
            enqueue(ChangeKind.UPDATE, entry(id, dao.entry(id)?.workspaceId ?: workspaceId, fields, start, stop))
        }
    }

    /**
     * The id the cache holds the entry [entryId] under: the server's once the server has answered
     * the create of an entry made on the phone, or [entryId] as it is.
     */
    suspend fun currentId(entryId: Long): Long = withContext(io) { resolve(entryId) }

    /** Deletes the cached entry [entryId]. Returns false when the entry is not cached. */
    override suspend fun delete(entryId: Long): Boolean =
        queue {
            val current = dao.entry(resolve(entryId)) ?: return@queue false
            enqueue(ChangeKind.DELETE, current)
            true
        }

    /**
     * Lets the held change [sequence] go out again as it is, such as after the cause of its
     * refusal was fixed on the server. Returns false when it is not held.
     */
    suspend fun sendAgain(sequence: Long): Boolean = queue { dao.release(sequence) > 0 }

    /**
     * Drops the held change [sequence] and the later changes to its entry that wait behind it,
     * since each of them builds on it. An entry the phone created goes from the cache with them;
     * any other keeps its row until the next refresh brings the server's. Returns false when the
     * change is not held.
     */
    suspend fun discard(sequence: Long): Boolean =
        queue {
            val held = dao.change(sequence)?.takeIf { it.held } ?: return@queue false
            dao.deleteFrom(held.entryId, held.sequence)
            if (held.kind == ChangeKind.CREATE) dao.deleteEntry(held.entryId)
            true
        }

    /**
     * Runs [change] in one transaction on [io], then asks for the queue to be sent while it holds
     * a change that can go out. A call that queued nothing, such as a stop of an entry that does
     * not run, leaves an empty queue alone.
     */
    private suspend fun <T> queue(change: () -> T): T {
        val (result, holdsChanges) = withContext(io) {
            database.runInTransaction<Pair<T, Boolean>> { change() to (dao.first() != null) }
        }
        if (holdsChanges) onQueued()
        return result
    }

    private fun stopAt(running: CachedTimeEntry, at: Instant, oneStep: Boolean) {
        val span = stopSpan(running.start, at, oneStep)
        enqueue(ChangeKind.STOP, running.copy(start = span.start, stop = span.stop))
    }

    /**
     * Queues a create, with the server's entries the phone holds around its start, which a lost
     * post's lookup must not take for it (see [PendingChange.existingIds]).
     */
    private fun queueCreate(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant?): Long {
        val margin = ChangeSender.LOOKUP_MARGIN
        val existing = dao.serverEntryIds(start.minus(margin), start.plus(margin))
        return enqueue(ChangeKind.CREATE, entry(id = 0, workspaceId, fields, start, stop), existing)
    }

    /**
     * Queues a change that leaves [entry] as given and applies it to the cache. A create gets its
     * local id here. Returns the entry's id.
     */
    private fun enqueue(kind: ChangeKind, entry: CachedTimeEntry, existingIds: List<Long> = emptyList()): Long {
        dao.heldFor(entry.id)?.let { held -> return foldInto(held, kind, entry) }
        val change = PendingChange.of(kind, entry).copy(existingIds = existingIds)
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
    private fun foldInto(held: PendingChange, kind: ChangeKind, entry: CachedTimeEntry): Long {
        dao.deleteFrom(held.entryId, held.sequence + 1)
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
            .copy(sequence = held.sequence, entryId = held.entryId, maybeSent = held.maybeSent, existingIds = existingIds)
        dao.update(folded)
        val row = folded.cachedEntry()
        if (row == null) dao.deleteEntry(folded.entryId) else dao.upsertEntry(row)
        return folded.entryId
    }

    private fun entry(id: Long, workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant?): CachedTimeEntry {
        val tags = fields.tags.distinct()
        val known = dao.tags(workspaceId).associate { it.name to it.id }
        return CachedTimeEntry(
            id = id,
            workspaceId = workspaceId,
            projectId = fields.projectId,
            taskId = fields.taskId,
            description = fields.description,
            start = start,
            stop = stop,
            billable = fields.billable,
            tags = tags,
            tagIds = tags.mapNotNull(known::get),
        )
    }

    /** The server's id for a local id the server has answered, or [entryId] as it is. */
    private fun resolve(entryId: Long): Long =
        if (entryId < 0) dao.serverId(entryId) ?: entryId else entryId
}
