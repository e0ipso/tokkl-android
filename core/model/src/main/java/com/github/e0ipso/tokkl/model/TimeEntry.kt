package com.github.e0ipso.tokkl.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When an entry happened. It runs from its [start], it ran from its start to a stop, or it is a
 * no-time entry: a day and a duration, with no times. Each kind holds only what it has, so a
 * running no-time entry or a timed entry with a day cannot be made. The cache's rows and the
 * server's answers keep a start, a stop, a no-time flag and a day, and their mappings read them
 * into one of these.
 */
sealed interface EntryTiming {

    /** Where the entry sits on the timeline: a no-time entry's is local midnight of its day. */
    val start: Instant

    /** The timer runs from [start]. */
    data class Running(override val start: Instant) : EntryTiming

    /** An entry that has ended: a [Stopped] one or an [AllDay] one. */
    sealed interface Ended : EntryTiming {

        /** Where the entry ends on the timeline: a no-time entry's start plus its duration. */
        val stop: Instant
    }

    /** The entry ran from [start] to [stop]. */
    data class Stopped(override val start: Instant, override val stop: Instant) : Ended

    /**
     * A no-time entry: [duration] on a day, with no start or end time.
     *
     * @property day the day in the account's timezone. Changes made on the phone set it, so a
     *   midnight that a later timezone change made stale never moves them to another day. Entries
     *   from the server leave it null: the server keeps their [start] at local midnight of the
     *   account's timezone, and moves it when that timezone changes.
     * @property start local midnight of the day in the account's timezone when the entry was stored.
     */
    data class AllDay(val day: LocalDate?, override val start: Instant, val duration: Duration) : Ended {

        override val stop: Instant get() = start + duration

        /** The day: [day], or the date of [start] in the account's timezone [zone]. */
        fun dayIn(zone: ZoneId): LocalDate = day ?: start.atZone(zone).toLocalDate()
    }

    companion object {

        /**
         * The timing a flat [start], [stop], no-time flag [allDay] and [day] describe, as the
         * cache's rows, the queue's changes and the saved state keep them. Without a stop the
         * entry runs, whatever the flag says, as the cache's running entry does. A timed entry
         * keeps no day.
         */
        fun of(start: Instant, stop: Instant?, allDay: Boolean, day: LocalDate?): EntryTiming = when {
            stop == null -> Running(start)
            allDay -> AllDay(day, start, Duration.between(start, stop))
            else -> Stopped(start, stop)
        }
    }
}

/**
 * A time entry.
 *
 * @property timing when it happened: running, stopped, or a no-time entry.
 * @property tags tag names. A name no cached tag has is kept, and the server creates the tag.
 * @property tagIds the cached ids of [tags].
 */
data class TimeEntry(
    val id: EntryId,
    val workspaceId: WorkspaceId,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val description: String,
    val timing: EntryTiming,
    val billable: Boolean,
    val tags: List<String>,
    val tagIds: List<TagId>,
) {
    /** [EntryTiming.start]. */
    val start: Instant get() = timing.start

    /** Where the entry ended, as [EntryTiming.Ended.stop] says, or null while it runs. */
    val stop: Instant? get() = (timing as? EntryTiming.Ended)?.stop
}

/** An entry, and whether the queue holds a change to it that the server has not received. */
data class EntryWithSendState(val entry: TimeEntry, val unsent: Boolean)

/**
 * A running segment's durable identity, captured before opening its editor. Starts always get a
 * new entry id, even at the same instant. Keep the local id: the queue resolves it after the
 * server answers, also after a process restart. The original start also rejects a competing time
 * edit. Pause ends this segment and Resume gives it a new identity.
 */
data class RunningEntryIdentity(val entryId: EntryId, val workspaceId: WorkspaceId, val start: Instant)

/** The fields of a time entry that the user picks, apart from its workspace and times. */
data class EntryFields(
    val description: String = "",
    val projectId: ProjectId? = null,
    val taskId: TaskId? = null,
    /** Tag names. A name no cached tag has is kept, and the server creates the tag. */
    val tags: List<String> = emptyList(),
    val billable: Boolean = false,
)

/**
 * One of Tokkl's favorites, which the app calls timer templates, at [position] in the user's
 * list. Favorites have no server id.
 */
data class Favorite(
    val position: Int,
    val description: String,
    val projectId: ProjectId?,
    val taskId: TaskId?,
    val tags: List<String>,
    val billable: Boolean,
)

/** The fields of [this] that the user picks. */
fun TimeEntry.fields(): EntryFields =
    EntryFields(description = description, projectId = projectId, taskId = taskId, tags = tags, billable = billable)

/** What a tap on this favorite starts. */
fun Favorite.fields(): EntryFields =
    EntryFields(description = description, projectId = projectId, taskId = taskId, tags = tags, billable = billable)
