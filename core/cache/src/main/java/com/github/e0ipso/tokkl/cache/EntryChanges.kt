package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import java.time.Instant
import java.time.LocalDate

/** Where the cache holds an entry now, such as [ChangeQueue.currentId]. */
fun interface CurrentEntryIds {

    /** The id the cache holds [entryId] under after a local create is answered, or [entryId]. */
    suspend fun currentId(entryId: EntryId): EntryId
}

/**
 * The timer's changes, as [ChangeQueue] makes them: Start, Stop and the edits of the running
 * segment, on the phone at once, then queued for the server. A timer surface's model takes this,
 * so a JVM test can stand in for the database. See [ChangeQueue] for what each call does.
 */
interface RunningEntryChanges : CurrentEntryIds {
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
 * A non-null `noTimeDay` on [create], [update] and [updateOlder] makes a no-time entry on that
 * day. The caller passes the day's local midnight in the account's zone as the start
 * (`AccountTime.instantAt(day, 0)`) and the start plus the snapped duration as the stop. A null
 * one makes a timed entry, also when the entry was a no-time one.
 */
interface EntryChanges : CurrentEntryIds {
    suspend fun create(
        workspaceId: WorkspaceId,
        fields: EntryFields,
        start: Instant,
        stop: Instant,
        noTimeDay: LocalDate? = null,
    ): EntryId

    suspend fun update(
        entryId: EntryId,
        fields: EntryFields,
        start: Instant,
        stop: Instant?,
        noTimeDay: LocalDate? = null,
    ): Boolean

    /**
     * Sets every field of the stopped entry [entryId] in [workspaceId], whether the phone holds it
     * or not, such as an entry from the older days the history pages from the server.
     */
    suspend fun updateOlder(
        entryId: EntryId,
        workspaceId: WorkspaceId,
        fields: EntryFields,
        start: Instant,
        stop: Instant,
        noTimeDay: LocalDate? = null,
    )

    suspend fun delete(entryId: EntryId): Boolean

    /**
     * Deletes the current cached copy, or [entry] when the cache does not hold that server entry.
     * Returns the exact deleted row, or null when nothing was queued.
     */
    suspend fun delete(entry: TimeEntry): TimeEntry?
}
