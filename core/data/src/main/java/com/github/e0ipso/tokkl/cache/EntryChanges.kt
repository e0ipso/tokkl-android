package com.github.e0ipso.tokkl.cache

import java.time.Instant
import java.time.LocalDate

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
 * A running segment's durable identity, captured before opening its editor. Starts always get a
 * new entry id, even at the same instant. Keep the local id: EntryIdMapping survives process
 * restarts and resolves it after the server answers. The original start also rejects a competing
 * time edit. Pause ends this segment and Resume gives it a new identity.
 */
data class RunningEntryIdentity(val entryId: Long, val workspaceId: Long, val start: Instant)

/**
 * The changes screens make to time entries, as [ChangeQueue] makes them: on the phone at once,
 * then queued for the server. A screen's model takes this, so a JVM test can stand in for the
 * database. See [ChangeQueue] for what each call does.
 *
 * A non-null `noTimeDay` on [create], [update] and [updateOlder] makes a no-time entry on that
 * day. The caller passes the day's local midnight in the account's zone as the start
 * (`AccountTime.instantAt(day, 0)`) and the start plus the snapped duration as the stop. A null
 * one makes a timed entry, also when the entry was a no-time one.
 */
interface EntryChanges {
    suspend fun start(workspaceId: Long, fields: EntryFields): Long

    suspend fun stop(entryId: Long, fields: EntryFields? = null): Boolean

    suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant, noTimeDay: LocalDate? = null): Long

    suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?, noTimeDay: LocalDate? = null): Boolean

    /**
     * Sets every field of the stopped entry [entryId] in [workspaceId], whether the phone holds it
     * or not, such as an entry from the older days the history pages from the server. A stand-in
     * with no cache of its own treats it as [update].
     */
    suspend fun updateOlder(
        entryId: Long,
        workspaceId: Long,
        fields: EntryFields,
        start: Instant,
        stop: Instant,
        noTimeDay: LocalDate? = null,
    ) {
        update(entryId, fields, start, stop, noTimeDay)
    }

    /** Reads this exact active segment, resolving a phone-made id after its create was sent. */
    suspend fun runningEntry(identity: RunningEntryIdentity): CachedTimeEntry? = null

    /** Checks the identity and running state in the same transaction as the unsnapped update. */
    suspend fun updateRunning(identity: RunningEntryIdentity, fields: EntryFields, start: Instant): Boolean = false

    suspend fun delete(entryId: Long): Boolean

    /** The id the cache holds [entryId] under after a local create is answered, or [entryId]. */
    suspend fun currentId(entryId: Long): Long = entryId

    /**
     * Deletes the current cached copy, or [entry] when the cache does not hold that server entry.
     * Returns the exact deleted row, or null when nothing was queued.
     */
    suspend fun delete(entry: CachedTimeEntry): CachedTimeEntry? = if (delete(entry.id)) entry else null
}
