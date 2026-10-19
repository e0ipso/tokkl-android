package com.github.e0ipso.tokkl.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The queue's reads and writes. [ChangeQueue] and [RoomChangeStore] combine them in transactions,
 * so a change and its effect on the cached rows commit together. Every call but [count] blocks on
 * disk I/O.
 */
@Dao
abstract class ChangeDao {

    /** How many changes the server has not received. */
    @Query("SELECT COUNT(*) FROM pending_changes")
    abstract fun count(): Flow<Int>

    /** The oldest change, which goes out next. */
    @Query("SELECT * FROM pending_changes ORDER BY sequence LIMIT 1")
    abstract fun first(): PendingChange?

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

    @Query("UPDATE pending_changes SET maybeSent = :maybeSent WHERE sequence = :sequence")
    abstract fun setMaybeSent(sequence: Long, maybeSent: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertMapping(mapping: EntryIdMapping)

    @Query("SELECT * FROM time_entries WHERE id = :id")
    abstract fun entry(id: Long): CachedTimeEntry?

    @Query("SELECT * FROM time_entries WHERE stop IS NULL")
    abstract fun runningEntries(): List<CachedTimeEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertEntry(entry: CachedTimeEntry)

    @Query("DELETE FROM time_entries WHERE id = :id")
    abstract fun deleteEntry(id: Long)

    @Query("SELECT * FROM tags WHERE workspaceId = :workspaceId")
    abstract fun tags(workspaceId: Long): List<CachedTag>
}
