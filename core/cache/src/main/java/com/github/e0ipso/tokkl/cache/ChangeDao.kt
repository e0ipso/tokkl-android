package com.github.e0ipso.tokkl.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The queue's reads and writes. [RoomChangeStore] combines them in transactions, for itself and,
 * as the [QueueTables] of [QueueEdits], for [ChangeQueue], so a change and its effect on the cached
 * rows commit together. Every call but the flows blocks on disk I/O.
 */
@Dao
abstract class ChangeDao : QueueTables {

    /** How many changes the server has not received, held ones included. */
    @Query("SELECT COUNT(*) FROM pending_changes")
    abstract fun count(): Flow<Int>

    /**
     * The oldest change that can go out, which goes out next: neither [PendingChange.held] nor to
     * an entry whose earlier change is held, since those wait behind it (docs/PRD.md O6).
     */
    @Query(
        "SELECT * FROM pending_changes WHERE entryId NOT IN (SELECT entryId FROM pending_changes WHERE held) ORDER " +
            "BY sequence LIMIT 1",
    )
    abstract override fun first(): PendingChange?

    /** How many changes the server refused wait for the user. */
    @Query("SELECT COUNT(*) FROM pending_changes WHERE held")
    abstract fun heldCount(): Flow<Int>

    /** The changes the server refused that wait for the user, oldest first. */
    @Query("SELECT * FROM pending_changes WHERE held ORDER BY sequence")
    abstract fun held(): Flow<List<PendingChange>>

    /** The held change to the entry [entryId], or null when it has none. */
    @Query("SELECT * FROM pending_changes WHERE entryId = :entryId AND held ORDER BY sequence LIMIT 1")
    abstract override fun heldFor(entryId: Long): PendingChange?

    @Query("SELECT * FROM pending_changes WHERE sequence = :sequence")
    abstract override fun change(sequence: Long): PendingChange?

    @Query("UPDATE pending_changes SET held = 1, refusal = :refusal WHERE sequence = :sequence")
    abstract fun hold(sequence: Long, refusal: String?)

    /** Lets a held change go out again. Returns how many it let go: none when it is not held. */
    @Query("UPDATE pending_changes SET held = 0, refusal = NULL WHERE sequence = :sequence AND held")
    abstract override fun release(sequence: Long): Int

    /** Deletes the queued changes to the entry [entryId] from [sequence] on. Returns how many. */
    @Query("DELETE FROM pending_changes WHERE entryId = :entryId AND sequence >= :sequence")
    abstract override fun deleteFrom(entryId: Long, sequence: Long): Int

    @Query("SELECT COUNT(*) FROM pending_changes WHERE entryId = :entryId")
    abstract fun countFor(entryId: Long): Int

    /** The ids of the entries the queue holds changes to, held ones included. */
    @Query("SELECT DISTINCT entryId FROM pending_changes")
    abstract fun pendingEntryIds(): List<Long>

    /** The queued changes to the entry [entryId], oldest first. */
    @Query("SELECT * FROM pending_changes WHERE entryId = :entryId ORDER BY sequence")
    abstract override fun changesFor(entryId: Long): List<PendingChange>

    @Insert
    abstract override fun insert(change: PendingChange): Long

    @Update
    abstract override fun update(change: PendingChange)

    @Query("DELETE FROM pending_changes WHERE sequence = :sequence")
    abstract override fun delete(sequence: Long)

    /** Gives every queued change to the entry [localId] the server's id. */
    @Query("UPDATE pending_changes SET entryId = :serverId WHERE entryId = :localId")
    abstract fun remap(localId: Long, serverId: Long)

    @Query("SELECT serverId FROM entry_ids WHERE localId = :localId")
    abstract override fun serverId(localId: Long): Long?

    /** True when a create from the phone already maps to the server's entry [serverId]. */
    @Query("SELECT EXISTS(SELECT 1 FROM entry_ids WHERE serverId = :serverId)")
    abstract fun isMapped(serverId: Long): Boolean

    /** Returns how many changes it marked: none when the change has left the queue. */
    @Query("UPDATE pending_changes SET maybeSent = :maybeSent WHERE sequence = :sequence")
    abstract fun setMaybeSent(sequence: Long, maybeSent: Boolean): Int

    /** The queued delete of the entry [entryId], or null when the queue holds none. */
    @Query("SELECT * FROM pending_changes WHERE entryId = :entryId AND kind = 'DELETE' ORDER BY sequence DESC LIMIT 1")
    abstract override fun queuedDelete(entryId: Long): PendingChange?

    @Query("SELECT * FROM short_stop_undo ORDER BY capturedAt DESC LIMIT 1")
    abstract fun latestShortStop(): Flow<ShortStopUndo?>

    @Query("SELECT * FROM short_stop_undo WHERE entryId = :entryId")
    abstract override fun shortStop(entryId: Long): ShortStopUndo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract override fun upsertShortStop(stop: ShortStopUndo)

    @Query("DELETE FROM short_stop_undo WHERE entryId = :entryId")
    abstract override fun deleteShortStop(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertMapping(mapping: EntryIdMapping)

    @Query("SELECT * FROM time_entries WHERE id = :id")
    abstract override fun entry(id: Long): CachedTimeEntry?

    /** The ids of the cached entries from the server, not made on the phone, that start from [from] to [to]. */
    @Query("SELECT id FROM time_entries WHERE id > 0 AND start BETWEEN :from AND :to")
    abstract override fun serverEntryIds(from: Instant, to: Instant): List<Long>

    @Query("SELECT * FROM time_entries WHERE stop IS NULL")
    abstract override fun runningEntries(): List<CachedTimeEntry>

    /** The running entry that started last, as [CacheDao.runningEntry] shows it. */
    @Query(
        "SELECT time_entries.*, ${CacheDao.UNSENT} FROM time_entries WHERE stop IS NULL ORDER BY start DESC, id DESC " +
            "LIMIT 1",
    )
    abstract fun latestRunning(): CachedEntryWithSendState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract override fun upsertEntry(entry: CachedTimeEntry)

    @Query("DELETE FROM time_entries WHERE id = :id")
    abstract override fun deleteEntry(id: Long)

    @Query("SELECT * FROM tags WHERE workspaceId = :workspaceId")
    abstract override fun tags(workspaceId: Long): List<CachedTag>
}
