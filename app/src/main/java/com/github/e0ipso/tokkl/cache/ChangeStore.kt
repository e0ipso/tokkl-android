package com.github.e0ipso.tokkl.cache

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
}
