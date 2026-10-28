package com.github.e0ipso.tokkl.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The Toggl API v9 shapes the cache reads, as Tokkl and Toggl Track send them. Only the fields the
// app uses are here; the decoder skips the rest. Fields that either server may send as null or
// leave out are nullable, so one odd row cannot fail the whole answer.

/** A client from `GET /api/v9/me?with_related_data=true`. Toggl names its workspace `wid`. */
@Serializable
class ServerClient(
    val id: Long,
    @SerialName("wid") val workspaceId: Long,
    val name: String? = null,
    val archived: Boolean? = null,
)

/** A project. [active] is false for an archived project. */
@Serializable
class ServerProject(
    val id: Long,
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("client_id") val clientId: Long? = null,
    val name: String? = null,
    val color: String? = null,
    val active: Boolean? = null,
    val billable: Boolean? = null,
)

/** A tag. Neither Tokkl nor Toggl archives tags. */
@Serializable
class ServerTag(
    val id: Long,
    @SerialName("workspace_id") val workspaceId: Long,
    val name: String? = null,
)

/** A task. [active] is false for a task marked done. */
@Serializable
class ServerTask(
    val id: Long,
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("project_id") val projectId: Long? = null,
    val name: String? = null,
    val active: Boolean? = null,
)

/**
 * A time entry from `GET /api/v9/me/time_entries`. [start] and [stop] are RFC 3339 timestamps,
 * and [stop] is null while the entry runs. A data class, because the timer state holds the running
 * entry in this shape and compares states by value.
 */
@Serializable
data class ServerTimeEntry(
    val id: Long,
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("project_id") val projectId: Long? = null,
    @SerialName("task_id") val taskId: Long? = null,
    val description: String? = null,
    val start: String,
    val stop: String? = null,
    val billable: Boolean? = null,
    val tags: List<String>? = null,
    @SerialName("tag_ids") val tagIds: List<Long>? = null,
    @SerialName("server_deleted_at") val serverDeletedAt: String? = null,
)

/** `GET /api/v9/me/preferences`. Tokkl leaves `favorites` out until the user saves one. */
@Serializable
class ServerPreferences(
    val favorites: List<ServerFavorite>? = null,
)

/** One of Tokkl's favorites: what a tap starts. Tags are names, as in a time entry. */
@Serializable
class ServerFavorite(
    val description: String? = null,
    @SerialName("project_id") val projectId: Long? = null,
    @SerialName("task_id") val taskId: Long? = null,
    val tags: List<String>? = null,
    val billable: Boolean? = null,
)
