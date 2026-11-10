package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext

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

data class ShortStopOffer(val entryId: EntryId, val capturedAt: Instant)

/**
 * Starts, stops, creates, edits and deletes time entries on the phone (docs/PRD.md O1). Each call
 * changes the cached entry at once and queues the change, in one transaction, then asks [onQueued]
 * to send the queue while it holds a change. No call waits for the network. The cache marks the
 * entry unsent until the server has every queued change to it, and keeps showing the change over
 * refreshed rows until then.
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
 * Pause, Resume and Pomodoro sessions belong to the server's timer state and never pass through
 * the queue (docs/PRD.md T1). A queued Stop of the server's resumed clone or of its open pomodoro
 * is an ordinary Stop, and the server ends the chain or the session when it gets it.
 *
 * A change the server refused waits for the user in the "needs attention" list ([held],
 * docs/PRD.md O6), and later changes to its entry wait behind it. A change made to that entry
 * meanwhile, such as the user's fix in the entry form, folds into the held change and lets it go
 * out again: every change carries all of the entry's fields, so the newest one says everything the
 * server needs.
 *
 * The decisions each call makes live in [QueueEdits], over the [QueueTables] that [storage] hands
 * it inside one transaction, so they hold no Room code and run in JVM tests over tables in memory.
 *
 * @param spans the spans the server stores for a Stop: [ServerSpans.capturedStop] for a Stop now,
 *   and [ServerSpans.stopped] for a short Stop's snapped zero point.
 */
class ChangeQueue(
    private val storage: QueueStorage,
    private val io: CoroutineContext,
    private val onQueued: () -> Unit,
    private val clock: Clock,
    private val spans: ServerSpans,
) : EntryChanges,
    RunningEntryChanges,
    HeldChanges {

    /** How many changes the server has not received (docs/PRD.md O5), held ones included. */
    val unsentCount: Flow<Int> = storage.unsentCount

    suspend fun unsentNow(): Int = unsentCount.first()

    override val held: Flow<List<HeldChange>> = storage.heldChanges.map { changes -> changes.map { it.toHeld() } }

    val heldCount: Flow<Int> = storage.heldCount

    /** Latest short Stop, retained across process death until Undo or dismissal. */
    val shortStopUndo: Flow<ShortStopOffer?> = storage.latestShortStop.map { undo ->
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
        return queue { start(workspaceId, fields, at) }
    }

    /**
     * Stops the running entry [entryId] at the tap time, or at its start if the tap came earlier,
     * on the grid as Tokkl's captured Stop stores it. A short span enters the trash.
     * With [fields], the stop also sets them, in the same change, such as a description still
     * being typed when Stop was tapped. Returns false when the entry is not cached or not running.
     */
    override suspend fun stop(entryId: EntryId, fields: EntryFields?): Boolean {
        val at = clock.instant()
        return queue { stop(entryId, fields, at) }
    }

    suspend fun undoShortStop(entryId: EntryId): Boolean = queue { undoShortStop(entryId) }

    /** Dismisses only the offer; the queued Stop and server trash remain intact. */
    suspend fun dismissShortStop(entryId: EntryId) = withContext(io) {
        storage.transaction { QueueEdits(this, spans).dismissShortStop(entryId) }
    }

    /**
     * Creates a stopped entry in [workspaceId], as a manual entry does, or a no-time entry (see
     * [EntryChanges]). Returns its local id. Pass the account's
     * `AccountSettings.defaultWorkspaceId`, as for [start].
     */
    override suspend fun create(workspaceId: WorkspaceId, fields: EntryFields, timing: EntryTiming.Ended): EntryId {
        requireForward(timing)
        return queue { create(workspaceId, fields, timing) }
    }

    /**
     * Sets every field of the cached entry [entryId]. A [EntryTiming.Running] timing makes it run,
     * and a stopped entry made to run stops the running one at its start first, as a Start would.
     * An [EntryTiming.AllDay] makes it a no-time entry (see [EntryChanges]); any other timing makes
     * it timed, so it turns a no-time entry's times on. Returns false when the entry is not cached.
     */
    override suspend fun update(entryId: EntryId, fields: EntryFields, timing: EntryTiming): Boolean {
        requireForward(timing)
        return queue { update(entryId, fields, timing) }
    }

    /**
     * Splits the cached entry [entryId] in one transaction, so a failed write, a cancellation or
     * the end of the process leaves either both halves or neither: the create of [second] first,
     * as the web app posts it first, then the edit that ends the original at [first].
     */
    override suspend fun split(
        entryId: EntryId,
        fields: EntryFields,
        first: EntryTiming.Stopped,
        second: EntryTiming.Stopped,
    ): Boolean {
        requireForward(first)
        requireForward(second)
        return queue { split(entryId, fields, first, second) }
    }

    override suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry? = withContext(io) {
        storage.transaction { QueueEdits(this, spans).activeEntry(identity) }?.toModel()
    }

    override suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean =
        queue { updateRunning(identity, fields, start) }

    /**
     * Sets every field of the stopped entry [entryId] in [workspaceId], which the cache need not
     * hold, such as an entry from the older days the history pages from the server. The cache holds
     * the entry from now on, marked unsent until the server has it; a refresh drops it again when
     * it starts before the cache's window. The workspace of a cached entry wins over [workspaceId].
     */
    override suspend fun updateOlder(
        entryId: EntryId,
        workspaceId: WorkspaceId,
        fields: EntryFields,
        timing: EntryTiming.Ended,
    ) {
        requireForward(timing)
        queue { updateOlder(entryId, workspaceId, fields, timing) }
    }

    private fun requireForward(timing: EntryTiming) {
        require((timing as? EntryTiming.Ended)?.stop?.isBefore(timing.start) != true) {
            "The stop comes before the start."
        }
    }

    /**
     * The id the cache holds the entry [entryId] under: the server's once the server has answered
     * the create of an entry made on the phone, or [entryId] as it is.
     */
    override suspend fun currentId(entryId: EntryId): EntryId = withContext(io) {
        EntryId(storage.transaction { resolve(entryId) })
    }

    /** Returns false when [entryId] is not cached or a delete is already queued. */
    override suspend fun delete(entryId: EntryId): Boolean = queue { delete(entryId) }

    /**
     * Resolves identity, prefers the current cache row and guards a duplicate in the same
     * transaction as DELETE. A missing local create is never recreated from a stale snapshot.
     * Returns the exact deleted fields and identity for reconciliation and Undo.
     */
    override suspend fun delete(entry: TimeEntry): TimeEntry? = queue { delete(entry) }

    override suspend fun sendAgain(sequence: Long): Boolean = queue { sendAgain(sequence) }

    /**
     * The later changes to the entry go too, since each builds on the held one. An entry the phone
     * created leaves the cache with them; any other keeps its row until the next refresh.
     */
    override suspend fun discard(sequence: Long): Boolean = queue { discard(sequence) }

    /** A call that queued nothing, such as a stop of an entry that does not run, leaves an empty queue alone. */
    private suspend fun <T> queue(change: QueueEdits.() -> T): T {
        // A Stop at process start, as from the notification, snaps to the account's grid.
        spans.ready()
        val (result, holdsChanges) = withContext(io) {
            storage.transaction {
                val edits = QueueEdits(this, spans)
                edits.change() to edits.holdsChanges()
            }
        }
        if (holdsChanges) onQueued()
        return result
    }
}
