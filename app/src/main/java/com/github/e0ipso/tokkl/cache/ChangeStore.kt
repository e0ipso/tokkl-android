package com.github.e0ipso.tokkl.cache

import java.time.Instant

/** The queue as [ChangeSender] sees it: the next change, and the record of a sent one. */
interface ChangeStore {

    /** The oldest queued change, or null when the queue is empty. Blocks on disk I/O. */
    fun first(): PendingChange?

    /**
     * Takes [change] out of the queue once the server has it, in one transaction. A create maps
     * its local id to [serverId] in the cache and in every later change to the entry. [answer] is
     * the entry as the server stored it, or null for a delete or an answer the phone cannot read.
     * It replaces the cached row unless more changes to the entry wait in the queue. Blocks on
     * disk I/O.
     */
    fun sent(change: PendingChange, serverId: Long, answer: CachedTimeEntry?)

    /**
     * Ends the queued start [start] where [later], the timer the server runs, started, before the
     * sender sends it (docs/PRD.md O3), in one transaction. The start's stop becomes the later
     * timer's start, or the stop of a [ChangeKind.STOP] queued for the entry when that comes
     * earlier. Every later queued change to the entry that would leave it running, or stop it after
     * the later timer started, stops it there instead, so no change sent after the start stops the
     * later timer. The cache shows the entry as the queue now leaves it, and [later] running unless
     * the queue holds a change to it. Returns the start as it now stands, to send. Blocks on disk
     * I/O.
     */
    fun endBefore(start: PendingChange, later: CachedTimeEntry): PendingChange

    /**
     * Records [PendingChange.maybeSent] for the queued create or delete [change]. Returns false
     * when [change] has left the queue, as a delete does when Undo takes it back. Blocks on disk
     * I/O.
     */
    fun maybeSent(change: PendingChange, maybeSent: Boolean): Boolean

    /** True when a create from the phone already maps to the server's entry [serverId]. Blocks on disk I/O. */
    fun isMapped(serverId: Long): Boolean

    /**
     * Takes back the delete of the entry [entryId], the id the phone deleted it under, in one
     * transaction: a queued delete that the sender has not sent leaves the queue, and the entry is
     * back in the cache as the delete found it. Otherwise the server has the delete, or may have
     * it, and only the server can restore the entry. Blocks on disk I/O.
     */
    fun takeBackDelete(entryId: Long): TakenBack

    /**
     * Records that the server restored its entry [serverId], in one transaction: a delete of it
     * still queued leaves the queue, and [answer], the entry as the server sent it back, goes to
     * the cache unless more changes to the entry wait in the queue. [answer] is null when the
     * phone could not read it. Blocks on disk I/O.
     */
    fun restored(serverId: Long, answer: CachedTimeEntry?)
}

/** What [ChangeStore.takeBackDelete] found. */
sealed interface TakenBack {
    /** The delete had not gone out. It left the queue and the entry is back, with no request. */
    data object Unsent : TakenBack

    /** The delete reached the server, or may have, so the server has to restore [serverId]. */
    data class OnServer(val serverId: Long) : TakenBack

    /** Neither the queue nor the server can have a delete of the entry, so nothing comes back. */
    data object Unknown : TakenBack
}

/** The queue in [database]. */
class RoomChangeStore(private val database: CacheDatabase) : ChangeStore {

    private val dao = database.changes()

    override fun first(): PendingChange? = dao.first()

    override fun sent(change: PendingChange, serverId: Long, answer: CachedTimeEntry?) {
        database.runInTransaction {
            dao.delete(change.sequence)
            if (change.kind == ChangeKind.CREATE) {
                dao.insertMapping(EntryIdMapping(localId = change.entryId, serverId = serverId))
                dao.remap(localId = change.entryId, serverId = serverId)
                dao.entry(change.entryId)?.let { local ->
                    dao.deleteEntry(local.id)
                    dao.upsertEntry(local.copy(id = serverId))
                }
            }
            if (answer != null && dao.countFor(serverId) == 0) dao.upsertEntry(answer)
        }
    }

    override fun endBefore(start: PendingChange, later: CachedTimeEntry): PendingChange =
        database.runInTransaction<PendingChange> {
            val changes = dao.changesFor(start.entryId)
            val ended = start.endedAt(later.start, changes)
            dao.update(ended)
            changes.filter { it.sequence > start.sequence }.forEach { change -> change.stoppedBy(later.start)?.let(dao::update) }
            dao.changesFor(start.entryId).last().cachedEntry()?.let(dao::upsertEntry)
            if (dao.countFor(later.id) == 0) dao.upsertEntry(later)
            ended
        }

    override fun maybeSent(change: PendingChange, maybeSent: Boolean): Boolean = dao.setMaybeSent(change.sequence, maybeSent) > 0

    override fun isMapped(serverId: Long): Boolean = dao.isMapped(serverId)

    override fun takeBackDelete(entryId: Long): TakenBack =
        database.runInTransaction<TakenBack> {
            // The server's id once the server has answered the create of an entry made on the phone.
            val id = if (entryId < 0) dao.serverId(entryId) ?: entryId else entryId
            val delete = dao.queuedDelete(id)
            when {
                delete != null && !delete.maybeSent -> {
                    dao.delete(delete.sequence)
                    dao.upsertEntry(delete.entry())
                    TakenBack.Unsent
                }
                // A delete goes out after the create of its entry, so it carries the server's id.
                id > 0 -> TakenBack.OnServer(id)
                else -> TakenBack.Unknown
            }
        }

    override fun restored(serverId: Long, answer: CachedTimeEntry?) {
        database.runInTransaction {
            dao.queuedDelete(serverId)?.let { dao.delete(it.sequence) }
            if (answer != null && dao.countFor(serverId) == 0) dao.upsertEntry(answer)
        }
    }
}

/**
 * This queued start, stopped at [laterStart], or at the stop of the first [ChangeKind.STOP] among
 * [changes] to the entry when that comes earlier.
 */
internal fun PendingChange.endedAt(laterStart: Instant, changes: List<PendingChange>): PendingChange {
    val queuedStop = changes.firstOrNull { it.kind == ChangeKind.STOP && it.sequence > sequence }?.stop
    return copy(stop = if (queuedStop != null && queuedStop.isBefore(laterStart)) queuedStop else laterStart)
}

/**
 * This change, made to stop at [laterStart] when it would leave its entry running or stop it after
 * then, or null when it can stay as it is: a delete, a change that starts the entry at [laterStart]
 * or later, or one that stops it by then.
 */
internal fun PendingChange.stoppedBy(laterStart: Instant): PendingChange? =
    when {
        kind == ChangeKind.DELETE || !start.isBefore(laterStart) -> null
        stop == null || stop.isAfter(laterStart) -> copy(stop = laterStart)
        else -> null
    }
