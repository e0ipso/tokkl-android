package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import java.time.Instant

/**
 * The timer's changes, as [ChangeQueue] makes them: Start, Stop and the edits of the running
 * segment, on the phone at once, then queued for the server. A timer surface's model takes this,
 * so a JVM test can stand in for the database. See [ChangeQueue] for what each call does.
 */
interface RunningEntryChanges {
    /** The id the cache holds [entryId] under after a local create is answered, or [entryId]. */
    suspend fun currentId(entryId: EntryId): EntryId

    suspend fun start(workspaceId: WorkspaceId, fields: EntryFields): EntryId

    suspend fun stop(entryId: EntryId, fields: EntryFields? = null): Boolean

    /** Reads this exact active segment, resolving a phone-made id after its create was sent. */
    suspend fun runningEntry(identity: RunningEntryIdentity): TimeEntry?

    /** Checks the identity and running state in the same transaction as the unsnapped update. */
    suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean
}

/**
 * The changes screens make to the entries of the history, as [ChangeQueue] makes them: a new past
 * entry, an edit of every field, a delete, on the phone at once, then queued for the server. A
 * screen's model takes this, so a JVM test can stand in for the database. See [ChangeQueue] for
 * what each call does.
 *
 * Each call takes the entry's [EntryTiming]: an [EntryTiming.AllDay] makes a no-time entry on its
 * day, with the day's local midnight in the account's zone as its start and the snapped duration,
 * as core:entryfields' `AccountTime.noTimeEntry` makes one. Any other timing makes a timed entry,
 * also when the entry was a no-time one.
 */
interface EntryChanges {
    /** The id the cache holds [entryId] under after a local create is answered, or [entryId]. */
    suspend fun currentId(entryId: EntryId): EntryId

    suspend fun create(workspaceId: WorkspaceId, fields: EntryFields, timing: EntryTiming.Ended): EntryId

    suspend fun update(entryId: EntryId, fields: EntryFields, timing: EntryTiming): Boolean

    /**
     * Splits the cached entry [entryId] in one write: a new entry with [fields] in its workspace
     * runs [second], and [entryId] keeps [fields] and runs [first]. Returns false, changing nothing,
     * when the entry is not cached.
     */
    suspend fun split(
        entryId: EntryId,
        fields: EntryFields,
        first: EntryTiming.Stopped,
        second: EntryTiming.Stopped,
    ): Boolean

    /**
     * Sets every field of the ended entry [entryId] in [workspaceId], whether the phone holds it
     * or not, such as an entry from the older days the history pages from the server.
     */
    suspend fun updateOlder(entryId: EntryId, workspaceId: WorkspaceId, fields: EntryFields, timing: EntryTiming.Ended)

    suspend fun delete(entryId: EntryId): Boolean

    /**
     * Deletes the current cached copy, or [entry] when the cache does not hold that server entry.
     * Returns the exact deleted row, or null when nothing was queued.
     */
    suspend fun delete(entry: TimeEntry): TimeEntry?
}

/** Deleted IDs remain suppressed across stale requests; restored snapshots bridge cache pruning. */
fun reconcileEntryRows(
    cached: List<EntryWithSendState>,
    loaded: List<TimeEntry>,
    overrides: Map<EntryId, TimeEntry?>,
): Pair<List<EntryWithSendState>, List<TimeEntry>> {
    val deleted = overrides.filterValues { it == null }.keys
    val current = cached.filter { it.entry.id !in deleted }
    val currentIds = current.mapTo(HashSet()) { it.entry.id }
    val restored = overrides.values.filterNotNull()
    val replaced = overrides.keys + restored.map { it.id }
    val server = loaded.filter { it.id !in replaced } + restored.filter { it.id !in currentIds }
    return current to server
}

/** Remaps both the tombstone and its restored fallback after a local create reaches the server. */
suspend fun resolveEntryOverrides(overrides: Map<EntryId, TimeEntry?>, ids: EntryChanges): Map<EntryId, TimeEntry?> =
    overrides.map { (id, entry) -> ids.currentId(id) to entry?.let { it.copy(id = ids.currentId(it.id)) } }.toMap()
