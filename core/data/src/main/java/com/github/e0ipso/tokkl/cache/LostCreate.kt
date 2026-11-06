package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.jsTrim
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.builtins.ListSerializer

/**
 * The entry an earlier post of [create] made, or null when the server has none: one that
 * [couldHaveMade] it, among the entries that start within [ChangeSender.LOOKUP_MARGIN] of its start
 * (for a no-time create, its stored local midnight), that the server did not hold already when the
 * create was queued ([PendingChange.existingIds]), such as a duplicate's source, and that no
 * other create from the phone maps to already in [store]. [spans] says where the server snaps the
 * start.
 */
internal fun earlierCopy(
    api: TogglApiClient,
    create: PendingChange,
    spans: ServerSpans,
    store: ChangeStore,
): ApiResult<ServerTimeEntry?> {
    val start = create.start.truncatedTo(ChronoUnit.SECONDS)
    val url = api.server.api("me", "time_entries").newBuilder()
        .addQueryParameter("start_date", start.minus(ChangeSender.LOOKUP_MARGIN).toString())
        .addQueryParameter("end_date", start.plus(ChangeSender.LOOKUP_MARGIN).toString())
        .build()
    val entries = when (val result = api.get(url, ListSerializer(ServerTimeEntry.serializer()))) {
        is ApiResult.Success -> result.value
        is ApiResult.Failure -> return result
    }
    val starts = setOf(start, spans.start(start))
    return ApiResult.Success(
        entries.firstOrNull { create.couldHaveMade(it, starts) && it.id !in create.existingIds && !store.isMapped(it.id) },
    )
}

/**
 * True when [entry] could be the one this create made: same workspace, description, project, task
 * and tags, the tags as [tagKey] compares them, and a start among [starts], the create's own and
 * where the server snaps it. Tokkl leaves `created_with` out of its answers, so that cannot tell
 * entries apart. Billable and the stop do not count, so the entry still matches after the server
 * or another client changed them.
 *
 * A no-time create also matches a no-time entry whose start lies within
 * [ChangeSender.LOOKUP_MARGIN] of its own. Tokkl puts that entry at midnight of its day in the
 * account's timezone as the server has it, which need not be the midnight the phone stored. One
 * sent while no-time entries were off went with its times, so it still matches by its start.
 */
internal fun PendingChange.couldHaveMade(entry: ServerTimeEntry, starts: Set<Instant>): Boolean {
    val made = entry.toCached() ?: return false
    return made.workspaceId == workspaceId &&
        made.description == description &&
        made.projectId == projectId &&
        made.taskId == taskId &&
        made.tags.mapTo(HashSet(), ::tagKey) == tags.mapTo(HashSet(), ::tagKey) &&
        (made.start in starts || allDay && made.allDay && made.start.near(start))
}

/** True when this instant lies within [ChangeSender.LOOKUP_MARGIN] of [start], in whole seconds. */
private fun Instant.near(start: Instant): Boolean =
    Duration.between(start.truncatedTo(ChronoUnit.SECONDS), this).abs() <= ChangeSender.LOOKUP_MARGIN

/**
 * The tag [name] as Tokkl matches it to a tag it has: trimmed, with A to Z in lower case. Tokkl
 * 1.3.1 trims each name a create sends (`requireName` in packages/api/src/lib/values.ts) and looks
 * it up with SQLite's `COLLATE NOCASE` (`findByName` in packages/api/src/models/tags.ts), which
 * folds ASCII letters only. So an entry queued with "work" comes back tagged "Work" when the
 * server has that tag.
 */
private fun tagKey(name: String): String =
    buildString { name.jsTrim().forEach { append(if (it in 'A'..'Z') it.lowercaseChar() else it) } }
