package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.model.EntryId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray

/**
 * Tokkl's trash, outside the Toggl API. It belongs to the user, not to a workspace. Toggl Track
 * and older Tokkl servers answer 404.
 */
class TrashApi internal constructor(private val client: TogglApiClient) {

    /** `GET /api/app/trash`: the deleted entries, the most recently deleted first. */
    suspend fun list(): ApiResult<List<TrashEntry>> =
        client.get(client.server.app("trash"), ListSerializer(TrashEntry.serializer()))

    /**
     * `GET /api/app/trash` for the capability probe, which asks only whether the answer is a list,
     * whatever its rows hold.
     */
    suspend fun probe(): ApiResult<JsonArray> = client.get(client.server.app("trash"), JsonArray.serializer())

    /**
     * `POST /api/app/time_entries/{id}/restore` from the trash page. Tokkl answers with the
     * entry, which this call does not read, so any 2xx is a success.
     */
    suspend fun restore(id: EntryId): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.POST, client.server.app("time_entries", id.toString(), "restore"), EMPTY_BODY)

    /** `DELETE /api/app/trash/{id}`, which deletes the entry for good. */
    suspend fun purge(id: EntryId): ApiResult<PurgedEntries> =
        client.send(HttpMethod.DELETE, client.server.app("trash", id.toString()), null, PurgedEntries.serializer())

    /** `DELETE /api/app/trash`, which deletes every entry in the trash for good. */
    suspend fun purgeAll(): ApiResult<PurgedEntries> =
        client.send(HttpMethod.DELETE, client.server.app("trash"), null, PurgedEntries.serializer())
}
