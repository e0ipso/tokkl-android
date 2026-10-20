package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/**
 * A deleted time entry as `GET /api/app/trash` sends it: a v9 time entry with Tokkl's meta fields.
 *
 * @property duration seconds, negative while the entry runs, as in API v9.
 * @property deletedAt when it went to the trash, `server_deleted_at`.
 */
@Serializable
data class TrashEntry(
    val id: Long,
    val description: String? = null,
    val start: String,
    val stop: String? = null,
    val duration: Long = 0,
    @SerialName("project_id") val projectId: Long? = null,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("server_deleted_at") val deletedAt: String? = null,
)

/** How many entries a purge removed for good. */
@Serializable
data class Purged(val deleted: Int = 0)

/**
 * Tokkl's trash calls on the configured server, outside the Toggl API (docs/PRD.md section 6).
 * The trash belongs to the user, not to a workspace. Every call blocks, so make them off the main
 * thread.
 */
class TrashServer(private val api: TogglApiClient) {

    /** The deleted entries, the most recently deleted first, at most [LIST_LIMIT] of them. */
    fun list(): ApiResult<List<TrashEntry>> = api.get(api.server.app("trash"), ListSerializer(TrashEntry.serializer()))

    /** Puts entry [id] back among the time entries. Tokkl answers with the entry. */
    fun restore(id: Long): ApiResult<Unit> =
        // OkHttp sends a POST only with a body. Tokkl reads none.
        api.sendNoContent("POST", api.server.app("time_entries", id.toString(), "restore"), JsonObject(emptyMap()))

    /** Deletes entry [id] for good. A 404 means it is no longer in the trash. */
    fun purge(id: Long): ApiResult<Purged> = api.send("DELETE", api.server.app("trash", id.toString()), null, Purged.serializer())

    /** Deletes every entry in the trash for good. */
    fun purgeAll(): ApiResult<Purged> = api.send("DELETE", api.server.app("trash"), null, Purged.serializer())

    companion object {
        /** Tokkl lists no more than this many deleted entries. */
        const val LIST_LIMIT = 1000
    }
}
