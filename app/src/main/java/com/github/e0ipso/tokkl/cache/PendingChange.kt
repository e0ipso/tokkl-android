package com.github.e0ipso.tokkl.cache

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// The queue of changes the server has not received yet (docs/PRD.md O1). It lives in the cache's
// database, so a change and its effect on the cached rows commit together, and both survive the
// process. Only time entries go in the queue.

/** What a [PendingChange] does to its entry. */
enum class ChangeKind {
    /** Creates the entry: a timer started on the phone, or a stopped entry made by hand. */
    CREATE,

    /** Sets every field of the entry. */
    UPDATE,

    /** Stops a running entry at its captured tap time. */
    STOP,

    /** Restores a short stop after its captured PATCH has reached the server. */
    RESTORE,

    DELETE,
}

/**
 * One change to a time entry, waiting in the queue. The queue sends changes in [sequence] order.
 * Each one carries the entry fields as the change left them. Creates and updates send full values;
 * captured Stops send their immutable tap instant. A [ChangeKind.DELETE] carries the old fields.
 *
 * @property entryId the entry's server id, or a negative local id while the server has not
 *   answered the entry's [ChangeKind.CREATE]. A created entry's local id is minus its create's
 *   [sequence]. When the server answers, every queued change to that entry takes the server's id.
 * @property start the start the user gave, or the tap time of a timer started on the phone.
 * @property stop the stop the user gave or the tap time of Stop, or null while the entry runs.
 * @property tags tag names. The server creates any it does not know.
 * @property tagIds the cached ids of [tags], for the cache row only. The server gets the names.
 * @property maybeSent the sender sent this create, captured Stop or delete and the server may have stored it,
 *   because no answer came back that rules it out: the connection dropped, the read timed out, the
 *   app died mid-request, the server answered 5xx or the phone could not read its 2xx. Before
 *   posting a create again the sender looks for the entry that post made. Undo of a marked delete
 *   asks the server to restore the entry, since the server may have deleted it. Only a create and
 *   a delete set it.
 * @property existingIds for a create, the ids of the server's entries the phone held within
 *   [ChangeSender.LOOKUP_MARGIN] of its start when it was queued. None of them can be the entry a
 *   post of this create made, so the sender never takes one for it, as it would take the source
 *   of a duplicate, which has every field and time of the copy.
 * @property held the server refused this change with a 400 or another 4xx that only the user can
 *   settle (docs/PRD.md O6), so it waits in the "needs attention" list until the user sends it
 *   again, edits the entry or discards it. Later changes to the same entry wait behind it, and the
 *   rest of the queue goes on.
 * @property refusal the server's reason for refusing a [held] change, such as "Time entries can
 *   not be longer than 999 hours", or null when its answer gave none.
 */
@Entity(tableName = "pending_changes", indices = [Index("entryId")])
data class PendingChange(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val kind: ChangeKind,
    val entryId: Long,
    val workspaceId: Long,
    val projectId: Long?,
    val taskId: Long?,
    val description: String,
    val start: Instant,
    val stop: Instant?,
    val billable: Boolean,
    val tags: List<String>,
    val tagIds: List<Long>,
    @ColumnInfo(defaultValue = "0") val maybeSent: Boolean = false,
    @ColumnInfo(defaultValue = "[]") val existingIds: List<Long> = emptyList(),
    @ColumnInfo(defaultValue = "0") val held: Boolean = false,
    val refusal: String? = null,
    /** The immutable tap instant for a captured PATCH Stop. Null on pre-migration queued stops. */
    val capturedStop: Instant? = null,
    @ColumnInfo(defaultValue = "0") val shortStop: Boolean = false,
) {

    /** The cache row this change leaves, or null for a delete or short Stop. */
    fun cachedEntry(): CachedTimeEntry? = if (kind == ChangeKind.DELETE || (kind == ChangeKind.STOP && shortStop)) null else entry()

    /** The entry as this change has it: for a delete, as it was when the delete was queued. */
    fun entry(): CachedTimeEntry =
        CachedTimeEntry(entryId, workspaceId, projectId, taskId, description, start, stop, billable, tags, tagIds)

    companion object {
        /** A change of [kind] that leaves [entry] as it is given. */
        fun of(kind: ChangeKind, entry: CachedTimeEntry) = PendingChange(
            kind = kind,
            entryId = entry.id,
            workspaceId = entry.workspaceId,
            projectId = entry.projectId,
            taskId = entry.taskId,
            description = entry.description,
            start = entry.start,
            stop = entry.stop,
            billable = entry.billable,
            tags = entry.tags,
            tagIds = entry.tagIds,
        )
    }
}

/** A short Stop that can be undone after its queue item has been sent. */
@Entity(tableName = "short_stop_undo")
data class ShortStopUndo(
    @PrimaryKey val entryId: Long,
    val capturedAt: Instant,
    @Embedded(prefix = "stopped_") val entry: CachedTimeEntry,
)

/** The server's id for an entry the phone created under [localId]. */
@Entity(tableName = "entry_ids")
data class EntryIdMapping(
    @PrimaryKey val localId: Long,
    val serverId: Long,
)

/** A cached entry, and whether the queue holds a change to it that the server has not received. */
data class EntryWithSendState(
    @Embedded val entry: CachedTimeEntry,
    val unsent: Boolean,
)

/**
 * These entries with each of the [pending] changes applied in order, so a refresh keeps showing
 * what the user did until the server has it. A change sets every field of its entry, or removes it.
 */
fun List<CachedTimeEntry>.withPending(pending: List<PendingChange>): List<CachedTimeEntry> {
    val byId = LinkedHashMap<Long, CachedTimeEntry>()
    forEach { byId[it.id] = it }
    pending.sortedBy { it.sequence }.forEach { change ->
        val row = change.cachedEntry()
        if (row == null) byId.remove(change.entryId) else byId[change.entryId] = row
    }
    return byId.values.toList()
}
