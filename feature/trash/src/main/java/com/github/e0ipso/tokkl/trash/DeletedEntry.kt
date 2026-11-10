package com.github.e0ipso.tokkl.trash

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.ProjectId
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * A deleted time entry, as the trash page shows it.
 *
 * @property description null or blank for an entry without one.
 * @property start when it started, or null when the server's timestamp does not read as one: the
 *   page then leaves the start out of the row and shows the rest.
 * @property duration how long it lasted, or null while it runs.
 * @property deletedAt when it went to the trash, or null when the server left it out or it does
 *   not read as a timestamp, which the row leaves out too.
 */
@Immutable
data class DeletedEntry(
    val id: EntryId,
    val description: String? = null,
    val projectId: ProjectId? = null,
    val projectName: String? = null,
    val start: Instant? = null,
    val duration: Duration? = null,
    val deletedAt: Instant? = null,
)

/**
 * The server's row as the page shows it. A row keeps its place when a timestamp in it does not
 * read, so the trash still lists it and it can still be restored or deleted. A row without a stop,
 * or with a negative duration as API v9 gives a running entry, runs.
 */
internal fun TrashEntry.deleted() = DeletedEntry(
    id = id,
    description = description,
    projectId = projectId?.let(::ProjectId),
    projectName = projectName,
    start = instantOf(start),
    duration = Duration.ofSeconds(duration).takeIf { stop != null && duration >= 0 },
    deletedAt = deletedAt?.let(::instantOf),
)

/** Tokkl writes `+00:00` and Toggl `Z`; both read here. Null for anything else. */
private fun instantOf(text: String): Instant? = try {
    OffsetDateTime.parse(text).toInstant()
} catch (_: DateTimeParseException) {
    null
}
