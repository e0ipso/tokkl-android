package com.github.e0ipso.tokkl.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The fields the app reads from `GET /api/v9/me`. Toggl Track and Tokkl always send `id`,
 * `timezone` and `beginning_of_week`, so a body without them is not a `/me` user. They send
 * `default_workspace_id` too, but a body without it still signs in, and a refresh then keeps the
 * stored workspace. `time_resolution_minutes` and `role` are Tokkl's own and may be missing.
 * `role` marks a Tokkl server. `toString` leaves out the token.
 *
 * [clients], [projects], [tags] and [tasks] come only with `with_related_data=true`, and are null
 * without it. Tokkl sends every one of them, archived projects and done tasks included.
 */
@Serializable
class Me(
    val id: Long,
    @SerialName("api_token") val apiToken: String? = null,
    val timezone: String,
    @SerialName("beginning_of_week") val beginningOfWeek: Int,
    @SerialName("time_resolution_minutes") val timeResolutionMinutes: Double? = null,
    @SerialName("default_workspace_id") val defaultWorkspaceId: Long? = null,
    val role: String? = null,
    val clients: List<ServerClient>? = null,
    val projects: List<ServerProject>? = null,
    val tags: List<ServerTag>? = null,
    val tasks: List<ServerTask>? = null,
) {

    override fun toString(): String = "Me(id=$id)"
}
