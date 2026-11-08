package com.github.e0ipso.tokkl.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A time entry. [stop] is null while it runs.
 *
 * @property tags tag names. A name no cached tag has is kept, and the server creates the tag.
 * @property tagIds the cached ids of [tags].
 * @property allDay a no-time entry: a day and a duration with no start or end time. [start] is
 *   local midnight of its day and [stop] is [start] plus the duration.
 * @property day the no-time entry's day in the account's timezone, which [noTimeDay] reads.
 *   Changes made on the phone set it; entries from the server leave it null.
 */
data class TimeEntry(
    val id: EntryId,
    val workspaceId: WorkspaceId,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val description: String,
    val start: Instant,
    val stop: Instant?,
    val billable: Boolean,
    val tags: List<String>,
    val tagIds: List<TagId>,
    val allDay: Boolean = false,
    val day: LocalDate? = null,
)

/**
 * The day of a no-time entry. Entries from the server may leave [TimeEntry.day] null, because
 * the server keeps their start at local midnight of the account's timezone [zone], and moves it
 * when that timezone changes. Changes made on the phone always set the day, so a midnight instant
 * that a later timezone change made stale never moves them to another day.
 */
fun TimeEntry.noTimeDay(zone: ZoneId): LocalDate = day ?: start.atZone(zone).toLocalDate()

/** An entry, and whether the queue holds a change to it that the server has not received. */
data class EntryWithSendState(val entry: TimeEntry, val unsent: Boolean)

/**
 * A running segment's durable identity, captured before opening its editor. Starts always get a
 * new entry id, even at the same instant. Keep the local id: the queue resolves it after the
 * server answers, also after a process restart. The original start also rejects a competing time
 * edit. Pause ends this segment and Resume gives it a new identity.
 */
data class RunningEntryIdentity(val entryId: EntryId, val workspaceId: WorkspaceId, val start: Instant)
