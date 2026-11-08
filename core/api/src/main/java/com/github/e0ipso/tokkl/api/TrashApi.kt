package com.github.e0ipso.tokkl.api

import kotlinx.serialization.DeserializationStrategy

/**
 * Tokkl's trash, outside the Toggl API. It belongs to the user, not to a workspace. Toggl Track
 * and older Tokkl servers answer 404.
 */
interface TrashApi {

    /**
     * `GET /api/app/trash`, the most recently deleted first, read as [deserializer]: the trash
     * page reads [TrashEntry] rows, while the capability probe only asks whether it is a list.
     */
    suspend fun <T> list(deserializer: DeserializationStrategy<T>): ApiResult<T>

    /**
     * `POST /api/app/time_entries/{id}/restore` from the trash page. Tokkl answers with the
     * entry, which this call does not read, so any 2xx is a success.
     */
    suspend fun restore(id: Long): ApiResult<Unit>

    /** `DELETE /api/app/trash/{id}`, which deletes the entry for good, read as [deserializer]. */
    suspend fun <T> purge(id: Long, deserializer: DeserializationStrategy<T>): ApiResult<T>

    /** `DELETE /api/app/trash`, which deletes every entry in the trash for good, read as [deserializer]. */
    suspend fun <T> purgeAll(deserializer: DeserializationStrategy<T>): ApiResult<T>
}

/** [TrashApi] over [client]. */
internal class HttpTrashApi(private val client: TogglApiClient) : TrashApi {

    override suspend fun <T> list(deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.get(client.server.app("trash"), deserializer)

    override suspend fun restore(id: Long): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.POST, client.server.app("time_entries", id.toString(), "restore"), EMPTY_BODY)

    override suspend fun <T> purge(id: Long, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.send(HttpMethod.DELETE, client.server.app("trash", id.toString()), null, deserializer)

    override suspend fun <T> purgeAll(deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.send(HttpMethod.DELETE, client.server.app("trash"), null, deserializer)
}
