package com.github.e0ipso.tokkl.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /** The ids of the entries the queue holds changes to, held ones included. */
    @Query("SELECT DISTINCT entryId FROM pending_changes")
    abstract fun pendingEntryIds(): List<Long>

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

    /** A running create whose server answer may still be in flight or have been lost. */
    @Query("SELECT * FROM pending_changes WHERE kind = 'CREATE' AND stop IS NULL AND maybeSent")
    abstract fun possiblySentStarts(): List<PendingChange>

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

    /** The running entry that started last, as [CacheDao.runningEntry] shows it. */
    @Query("SELECT time_entries.*, ${CacheDao.UNSENT} FROM time_entries WHERE stop IS NULL ORDER BY start DESC, id DESC LIMIT 1")
    abstract fun latestRunning(): EntryWithSendState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertEntry(entry: CachedTimeEntry)

    @Query("DELETE FROM time_entries WHERE id = :id")
    abstract fun deleteEntry(id: Long)

    @Query("SELECT * FROM tags WHERE workspaceId = :workspaceId")
    abstract fun tags(workspaceId: Long): List<CachedTag>

    @Query("SELECT * FROM pause_chain WHERE slot = 0")
    abstract fun pauseChain(): PauseChain?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPauseChain(chain: PauseChain)

    /**
     * Slot 0 is the visible chain. Positive slots are segment checkpoints for that chain,
     * using the same Room 8 columns without adding an entity or changing the schema.
     * Their revision stores segment order, epoch is 0 before Pause and 1 after it, and
     * stoppedSeconds is only that segment's contribution. Fields are the pre-start snapshot.
     * Keep answered segments too: a later discard must distinguish them from unrelated entries.
     */
    @Query("SELECT * FROM pause_chain WHERE slot > 0 AND chainId = :chainId ORDER BY revision")
    abstract fun pauseSegmentRows(chainId: Long): List<PauseChain>

    fun pauseSegments(chainId: Long): List<PauseSegmentCheckpoint> = pauseSegmentRows(chainId).map { row ->
        PauseSegmentCheckpoint(
            slot = row.slot,
            chainId = row.chainId,
            order = row.revision,
            entryId = requireNotNull(row.runningId),
            seconds = row.stoppedSeconds,
            paused = row.epoch != 0L,
            workspaceId = row.workspaceId,
            fields = row.fields,
        )
    }

    @Query("SELECT COALESCE(MAX(slot), 0) FROM pause_chain")
    abstract fun lastPauseSegmentSlot(): Int

    fun upsertPauseSegment(segment: PauseSegmentCheckpoint) = upsertPauseChain(
        PauseChain(
            slot = segment.slot,
            chainId = segment.chainId,
            revision = segment.order,
            epoch = if (segment.paused) 1 else 0,
            stoppedSeconds = segment.seconds,
            runningId = segment.entryId,
            workspaceId = segment.workspaceId,
            fields = segment.fields,
        ),
    )

    @Query("DELETE FROM pause_chain WHERE slot = :slot AND slot > 0")
    abstract fun deletePauseSegment(slot: Int)

    /** Ends the chain and deletes all of its segment checkpoints. Returns the number of rows. */
    @Query("DELETE FROM pause_chain")
    abstract fun deletePauseChain(): Int

    /** Marks a queue answer, so a refresh whose server read began earlier leaves the chain alone. */
    @Query("UPDATE pause_chain SET epoch = epoch + 1 WHERE slot = 0")
    abstract fun touchPauseChain()

    /** Gives the running clone and its checkpoint [localId] the server's id. */
    @Query("UPDATE pause_chain SET runningId = :serverId WHERE runningId = :localId")
    abstract fun remapPauseChain(localId: Long, serverId: Long)

    @Query("SELECT id FROM timer_session WHERE slot = 0")
    abstract fun timerSession(): Long?

    /** Keeps the current session, or starts [session] when there is none. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun ensureTimerSession(session: TimerSession)

    /**
     * Ends the pause chain when a server read that began at [mark] shows that its timer changed
     * elsewhere: something runs while it is paused, or another entry than its clone runs, or
     * nothing does. A chain that changed since [mark], or began after it, is newer than the read
     * and stays. Call it in the transaction that writes the read's rows.
     */
    @Transaction
    open fun reconcilePauseChain(mark: ChainMark?) {
        val chain = pauseChain() ?: return
        if (mark != ChainMark(chain.chainId, chain.epoch)) return
        val running = latestRunning()?.entry
        val keeps = if (chain.runningId == null) running == null else running?.id == chain.runningId
        if (!keeps) deletePauseChain()
    }
}

/** Where a server read begins for the pause chain, as [ChangeDao.reconcilePauseChain] takes it. */
fun ChangeDao.chainMark(): ChainMark? = pauseChain()?.let { ChainMark(it.chainId, it.epoch) }
