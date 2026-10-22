package com.github.e0ipso.tokkl.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The queue's reads and writes. [ChangeQueue] and [RoomChangeStore] combine them in transactions,
 * so a change and its effect on the cached rows commit together. Every call but [count] blocks on
 * disk I/O.
 */
@Dao
abstract class ChangeDao {

    /** How many changes the server has not received, held ones included. */
    @Query("SELECT COUNT(*) FROM pending_changes")
    abstract fun count(): Flow<Int>

    /**
     * The oldest change that can go out, which goes out next: neither [PendingChange.held] nor to
     * an entry whose earlier change is held, since those wait behind it (docs/PRD.md O6).
     */
    @Query("SELECT * FROM pending_changes WHERE entryId NOT IN (SELECT entryId FROM pending_changes WHERE held) ORDER BY sequence LIMIT 1")
    abstract fun first(): PendingChange?

    /** How many changes the server refused wait for the user. */
    @Query("SELECT COUNT(*) FROM pending_changes WHERE held")
    abstract fun heldCount(): Flow<Int>

    /** The changes the server refused that wait for the user, oldest first. */
    @Query("SELECT * FROM pending_changes WHERE held ORDER BY sequence")
    abstract fun held(): Flow<List<PendingChange>>

    /** The held change to the entry [entryId], or null when it has none. */
    @Query("SELECT * FROM pending_changes WHERE entryId = :entryId AND held ORDER BY sequence LIMIT 1")
    abstract fun heldFor(entryId: Long): PendingChange?

    @Query("SELECT * FROM pending_changes WHERE sequence = :sequence")
    abstract fun change(sequence: Long): PendingChange?

    @Query("UPDATE pending_changes SET held = 1, refusal = :refusal WHERE sequence = :sequence")
    abstract fun hold(sequence: Long, refusal: String?)

    /** Lets a held change go out again. Returns how many it let go: none when it is not held. */
    @Query("UPDATE pending_changes SET held = 0, refusal = NULL WHERE sequence = :sequence AND held")
    abstract fun release(sequence: Long): Int

    /** Deletes the queued changes to the entry [entryId] from [sequence] on. Returns how many. */
    @Query("DELETE FROM pending_changes WHERE entryId = :entryId AND sequence >= :sequence")
    abstract fun deleteFrom(entryId: Long, sequence: Long): Int

    @Query("SELECT COUNT(*) FROM pending_changes WHERE entryId = :entryId")
    abstract fun countFor(entryId: Long): Int

    /** The queued changes to the entry [entryId], oldest first. */
    @Query("SELECT * FROM pending_changes WHERE entryId = :entryId ORDER BY sequence")
    abstract fun changesFor(entryId: Long): List<PendingChange>

    @Insert
    abstract fun insert(change: PendingChange): Long

    @Update
    abstract fun update(change: PendingChange)

    @Query("DELETE FROM pending_changes WHERE sequence = :sequence")
    abstract fun delete(sequence: Long)

    /** Gives every queued change to the entry [localId] the server's id. */
    @Query("UPDATE pending_changes SET entryId = :serverId WHERE entryId = :localId")
    abstract fun remap(localId: Long, serverId: Long)

    @Query("SELECT serverId FROM entry_ids WHERE localId = :localId")
    abstract fun serverId(localId: Long): Long?

    /** True when a create from the phone already maps to the server's entry [serverId]. */
    @Query("SELECT EXISTS(SELECT 1 FROM entry_ids WHERE serverId = :serverId)")
    abstract fun isMapped(serverId: Long): Boolean

    /** Returns how many changes it marked: none when the change has left the queue. */
    @Query("UPDATE pending_changes SET maybeSent = :maybeSent WHERE sequence = :sequence")
    abstract fun setMaybeSent(sequence: Long, maybeSent: Boolean): Int

    /** The queued delete of the entry [entryId], or null when the queue holds none. */
    @Query("SELECT * FROM pending_changes WHERE entryId = :entryId AND kind = 'DELETE' ORDER BY sequence DESC LIMIT 1")
    abstract fun queuedDelete(entryId: Long): PendingChange?

    @Query("SELECT * FROM short_stop_undo ORDER BY capturedAt DESC LIMIT 1")
    abstract fun latestShortStop(): Flow<ShortStopUndo?>

    @Query("SELECT * FROM short_stop_undo WHERE entryId = :entryId")
    abstract fun shortStop(entryId: Long): ShortStopUndo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertShortStop(stop: ShortStopUndo)

    @Query("DELETE FROM short_stop_undo WHERE entryId = :entryId")
    abstract fun deleteShortStop(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertMapping(mapping: EntryIdMapping)

    @Query("SELECT * FROM time_entries WHERE id = :id")
    abstract fun entry(id: Long): CachedTimeEntry?

    /** The ids of the cached entries from the server, not made on the phone, that start from [from] to [to]. */
    @Query("SELECT id FROM time_entries WHERE id > 0 AND start BETWEEN :from AND :to")
    abstract fun serverEntryIds(from: Instant, to: Instant): List<Long>

    @Query("SELECT * FROM time_entries WHERE stop IS NULL")
    abstract fun runningEntries(): List<CachedTimeEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertEntry(entry: CachedTimeEntry)

    @Query("DELETE FROM time_entries WHERE id = :id")
    abstract fun deleteEntry(id: Long)

    @Query("SELECT * FROM tags WHERE workspaceId = :workspaceId")
    abstract fun tags(workspaceId: Long): List<CachedTag>
}
