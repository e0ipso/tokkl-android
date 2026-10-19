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

    /** Sets every field of a running entry, its stop being the tap time. */
    STOP,

    DELETE,
}

/**
 * One change to a time entry, waiting in the queue. The queue sends changes in [sequence] order.
 * Each one carries every field of the entry as the change left it, so the server gets full values
 * and the change that reaches it last wins (docs/PRD.md O3). A [ChangeKind.DELETE] carries the
 * fields the entry had.
 *
 * @property entryId the entry's server id, or a negative local id while the server has not
 *   answered the entry's [ChangeKind.CREATE]. A created entry's local id is minus its create's
 *   [sequence]. When the server answers, every queued change to that entry takes the server's id.
 * @property start the start the user gave, or the tap time of a timer started on the phone.
 * @property stop the stop the user gave or the tap time of Stop, or null while the entry runs.
 * @property tags tag names. The server creates any it does not know.
 * @property tagIds the cached ids of [tags], for the cache row only. The server gets the names.
 * @property maybeSent the sender posted this create and the server may have stored it, because
 *   no answer came back that rules it out: the connection dropped, the read timed out, the app
 *   died mid-request, the server answered 5xx or the phone could not read its 2xx. Before posting
 *   it again the sender looks for the entry that post made. Only a create sets it.
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
) {

    /** The cache row this change leaves, or null for a delete. */
    fun cachedEntry(): CachedTimeEntry? =
        if (kind == ChangeKind.DELETE) {
            null
        } else {
            CachedTimeEntry(entryId, workspaceId, projectId, taskId, description, start, stop, billable, tags, tagIds)
        }

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
