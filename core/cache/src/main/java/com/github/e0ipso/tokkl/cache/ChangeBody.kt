package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.NoTimeEntryBody
import com.github.e0ipso.tokkl.api.TimeEntryBody
import com.github.e0ipso.tokkl.api.TimedEntryBody
import java.time.temporal.ChronoUnit

/** What the app names itself as in a created entry's `created_with`, which Toggl requires. */
const val CREATED_WITH = "Tokkl for Android"

/**
 * The entry's fields as a create, an update or a legacy stop sends them; a create names the app
 * in `created_with`. Times go in whole seconds, the precision the server stores; a running entry
 * sends `duration: -1`, and a stopped one the duration that matches its start and stop. Tags go
 * by name.
 *
 * With [allDay], the server stores no-time entries, and a no-time change sends `all_day: true`,
 * its day as `start_date` and its duration, and neither `start` nor `stop`: Tokkl refuses a start
 * that is not local midnight and a stop that is not the one it anchors. Without a stored day it
 * sends its local midnight as `start`, whose date Tokkl reads in the account's timezone. Every
 * other change sends its times, and `all_day: false` when [allDay] is on, which turns a no-time
 * entry back into a timed one. Without [allDay] no body carries `all_day`.
 */
internal fun PendingChange.body(allDay: Boolean): TimeEntryBody {
    val start = start.truncatedTo(ChronoUnit.SECONDS)
    val stop = stop?.truncatedTo(ChronoUnit.SECONDS)
    val createdWith = CREATED_WITH.takeIf { kind == ChangeKind.CREATE }
    return if (allDay && this.allDay && stop != null) {
        NoTimeEntryBody(
            workspaceId = workspaceId,
            createdWith = createdWith,
            description = description,
            projectId = projectId,
            taskId = taskId,
            tags = tags,
            billable = billable,
            allDay = true,
            startDate = day?.toString(),
            start = if (day == null) start.toString() else null,
            duration = stop.epochSecond - start.epochSecond,
        )
    } else {
        TimedEntryBody(
            workspaceId = workspaceId,
            createdWith = createdWith,
            description = description,
            projectId = projectId,
            taskId = taskId,
            tags = tags,
            billable = billable,
            start = start.toString(),
            stop = stop?.toString(),
            duration = if (stop == null) RUNNING else stop.epochSecond - start.epochSecond,
            allDay = if (allDay) false else null,
        )
    }
}

/** The duration a running entry sends. */
private const val RUNNING = -1L
