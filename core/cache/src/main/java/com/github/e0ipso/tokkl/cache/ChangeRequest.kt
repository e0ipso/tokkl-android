package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.HttpMethod
import com.github.e0ipso.tokkl.api.ServerUrl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import java.time.temporal.ChronoUnit

/** One change as an HTTP request. */
internal class ChangeRequest(val method: HttpMethod, val url: HttpUrl, val body: JsonObject?)

/** What the app names itself as in a created entry's `created_with`, which Toggl requires. */
const val CREATED_WITH = "Tokkl for Android"

/**
 * The Toggl API v9 request for this change, under [server]. A create is a POST, an update and a
 * legacy stop a PUT with every field; a new Stop sends its frozen tap instant as PATCH. A delete is
 * a DELETE and a short Stop's Undo is a POST restore. Times go in whole seconds, the precision
 * the server stores; a running entry sends `duration: -1`, and a stopped one the duration that
 * matches its start and stop. Tags go by name. With [allDay], the server stores no-time entries,
 * and a create or update says whether it is one, as [body] describes. Without it no request
 * carries `all_day`.
 */
internal fun PendingChange.request(server: ServerUrl, allDay: Boolean = false): ChangeRequest {
    val collection = server.api("workspaces", workspaceId.toString(), "time_entries")
    val url = if (kind == ChangeKind.CREATE) {
        collection
    } else {
        collection.newBuilder().addPathSegment(
            entryId.toString(),
        ).build()
    }
    return when (kind) {
        ChangeKind.CREATE -> ChangeRequest(HttpMethod.POST, url, body(allDay))

        ChangeKind.UPDATE -> ChangeRequest(HttpMethod.PUT, url, body(allDay))

        // A legacy stop ends a running entry, which is never a no-time one.
        ChangeKind.STOP -> if (capturedStop == null) {
            ChangeRequest(HttpMethod.PUT, url, body(allDay = false))
        } else {
            ChangeRequest(
                HttpMethod.PATCH,
                url.newBuilder().addPathSegment("stop").build(),
                buildJsonObject {
                    put("stop", capturedStop.truncatedTo(ChronoUnit.SECONDS).toString())
                },
            )
        }

        ChangeKind.RESTORE -> ChangeRequest(
            HttpMethod.POST,
            server.app("time_entries", entryId.toString(), "restore"),
            JsonObject(emptyMap()),
        )

        ChangeKind.DELETE -> ChangeRequest(HttpMethod.DELETE, url, body = null)
    }
}

/**
 * The entry's fields. With [allDay] on, a no-time change sends `all_day: true`, its day as
 * `start_date` and its duration, and neither `start` nor `stop`: Tokkl refuses a start that is not
 * local midnight and a stop that is not the one it anchors. Without a stored day it sends its
 * local midnight as `start`, whose date Tokkl reads in the account's timezone. Every other change
 * sends its times, and `all_day: false` when [allDay] is on, which turns a no-time entry back into
 * a timed one.
 */
private fun PendingChange.body(allDay: Boolean): JsonObject {
    val start = start.truncatedTo(ChronoUnit.SECONDS)
    val stop = stop?.truncatedTo(ChronoUnit.SECONDS)
    val noTime = allDay && this.allDay
    return buildJsonObject {
        put("workspace_id", workspaceId)
        if (kind == ChangeKind.CREATE) put("created_with", CREATED_WITH)
        put("description", description)
        put("project_id", projectId)
        put("task_id", taskId)
        put("tags", JsonArray(tags.map(::JsonPrimitive)))
        put("billable", billable)
        if (noTime && stop != null) {
            put("all_day", true)
            if (day != null) put("start_date", day.toString()) else put("start", start.toString())
            put("duration", stop.epochSecond - start.epochSecond)
        } else {
            put("start", start.toString())
            if (stop == null) {
                put("stop", JsonNull)
                put("duration", -1)
            } else {
                put("stop", stop.toString())
                put("duration", stop.epochSecond - start.epochSecond)
            }
            if (allDay) put("all_day", false)
        }
    }
}
