package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerClient
import com.github.e0ipso.tokkl.api.ServerFavorite
import com.github.e0ipso.tokkl.api.ServerPreferences
import com.github.e0ipso.tokkl.api.ServerProject
import com.github.e0ipso.tokkl.api.ServerTag
import com.github.e0ipso.tokkl.api.ServerTask
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.session.Me
import com.github.e0ipso.tokkl.session.MePreferences
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.builtins.ListSerializer

/**
 * Reads what a refresh puts in the cache. [me] is the refresh's `GET /api/v9/me` with related
 * data, which carries the clients, projects, tags and tasks. [preferences] is its
 * `GET /api/v9/me/preferences`, which carries the favorites, or null when that request failed. On
 * top of them comes one request, `GET /api/v9/me/time_entries` for the history window, counted in
 * the account's timezone. A failed request leaves its list null. Without [favorites], as on Toggl
 * Track, the favorites are an empty list.
 *
 * Blocks on the network, so call it off the main thread.
 */
fun readServerSnapshot(
    api: TogglApiClient,
    me: Me,
    preferences: MePreferences?,
    clock: Clock,
    favorites: Boolean,
): ServerSnapshot {
    val history = me.accountSettings()?.time(clock)?.let(HistoryWindow::endingToday)
    return ServerSnapshot(
        history = history,
        timeEntries = history?.let { readTimeEntries(api, it) },
        projects = me.projects?.map { it.toCached() },
        clients = me.clients?.map { it.toCached() },
        tags = me.tags?.map { it.toCached() },
        tasks = me.tasks?.map { it.toCached() },
        favorites = if (favorites) favoritesFrom(preferences) else emptyList(),
    )
}

/**
 * Entries that start within [history]. Sends both ends as instants, which Tokkl and Toggl read the
 * same way, so neither server's idea of the day matters.
 */
private fun readTimeEntries(api: TogglApiClient, history: HistoryWindow): List<CachedTimeEntry>? {
    val url = api.server.api("me", "time_entries").newBuilder()
        .addQueryParameter("start_date", history.start.toString())
        .addQueryParameter("end_date", history.end.toString())
        .build()
    val entries = api.get(url, ListSerializer(ServerTimeEntry.serializer()))
    return (entries as? ApiResult.Success)?.value?.mapNotNull { it.toCached() }
}

/**
 * Tokkl leaves `favorites` out until the user saves one, which means none. Null when the request
 * failed or the favorites do not decode.
 */
private fun favoritesFrom(preferences: MePreferences?): List<CachedFavorite>? {
    val body = preferences?.body ?: return null
    val decoded = try {
        ApiJson.decodeFromJsonElement(ServerPreferences.serializer(), body)
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization's SerializationException is an IllegalArgumentException.
        return null
    }
    return decoded.favorites.orEmpty().mapIndexed { position, favorite -> favorite.toCached(position) }
}

/** Null when [ServerTimeEntry.start] is not an RFC 3339 timestamp. */
private fun ServerTimeEntry.toCached(): CachedTimeEntry? {
    val start = instant(start) ?: return null
    return CachedTimeEntry(
        id = id,
        workspaceId = workspaceId,
        projectId = projectId,
        taskId = taskId,
        description = description.orEmpty(),
        start = start,
        stop = stop?.let(::instant),
        billable = billable ?: false,
        tags = tags.orEmpty(),
        tagIds = tagIds.orEmpty(),
    )
}

private fun ServerProject.toCached() = CachedProject(
    id = id,
    workspaceId = workspaceId,
    clientId = clientId,
    name = name.orEmpty(),
    color = color,
    active = active ?: true,
    billable = billable ?: false,
)

private fun ServerClient.toCached() = CachedClient(id, workspaceId, name.orEmpty(), archived ?: false)

private fun ServerTag.toCached() = CachedTag(id, workspaceId, name.orEmpty())

private fun ServerTask.toCached() = CachedTask(id, workspaceId, projectId, name.orEmpty(), active ?: true)

private fun ServerFavorite.toCached(position: Int) = CachedFavorite(
    position = position,
    description = description.orEmpty(),
    projectId = projectId,
    taskId = taskId,
    tags = tags.orEmpty(),
    billable = billable ?: false,
)

/** Tokkl sends `+00:00` and Toggl `Z`; both carry the offset, so no zone is assumed. */
private fun instant(text: String): Instant? =
    try {
        OffsetDateTime.parse(text).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }
