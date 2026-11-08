package com.github.e0ipso.tokkl.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable

/**
 * A workspace's tags. Neither Tokkl nor Toggl archives tags. The answers read as the caller's
 * [DeserializationStrategy], since the tags page owns the shape it reads.
 */
interface TagsApi {

    /** `GET /api/v9/workspaces/{workspaceId}/tags`. */
    suspend fun <T> list(workspaceId: Long, deserializer: DeserializationStrategy<T>): ApiResult<T>

    /** `POST /api/v9/workspaces/{workspaceId}/tags` with [name], sent once. */
    suspend fun <T> create(workspaceId: Long, name: String, deserializer: DeserializationStrategy<T>): ApiResult<T>

    /** `PUT /api/v9/workspaces/{workspaceId}/tags/{id}` with [name]. The server renames it on every entry. */
    suspend fun <T> rename(
        workspaceId: Long,
        id: Long,
        name: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T>

    /** `DELETE /api/v9/workspaces/{workspaceId}/tags/{id}`. Tokkl answers with no body. */
    suspend fun delete(workspaceId: Long, id: Long): ApiResult<Unit>
}

@Serializable
private class TagBody(val name: String)

/** [TagsApi] over [client]. */
internal class HttpTagsApi(private val client: TogglApiClient) : TagsApi {

    override suspend fun <T> list(workspaceId: Long, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.get(tags(workspaceId), deserializer)

    override suspend fun <T> create(
        workspaceId: Long,
        name: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.POST,
        tags(workspaceId),
        json(TagBody.serializer(), TagBody(name)),
        deserializer,
        resend = false,
    )

    override suspend fun <T> rename(
        workspaceId: Long,
        id: Long,
        name: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.PUT,
        tags(workspaceId, id.toString()),
        json(TagBody.serializer(), TagBody(name)),
        deserializer,
    )

    override suspend fun delete(workspaceId: Long, id: Long): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.DELETE, tags(workspaceId, id.toString()))

    private fun tags(workspaceId: Long, vararg path: String) =
        client.server.api("workspaces", workspaceId.toString(), "tags", *path)
}
