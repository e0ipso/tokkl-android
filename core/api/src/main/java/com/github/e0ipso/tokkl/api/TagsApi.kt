package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private class TagBody(val name: String)

/** A workspace's tags. Neither Tokkl nor Toggl archives tags. */
class TagsApi internal constructor(private val client: ApiTransport) {

    /** `GET /api/v9/workspaces/{workspaceId}/tags`. */
    suspend fun list(workspaceId: WorkspaceId): ApiResult<List<WorkspaceTag>> =
        client.get(tags(workspaceId), ListSerializer(WorkspaceTag.serializer()))

    /** `POST /api/v9/workspaces/{workspaceId}/tags` with [name], sent once. */
    suspend fun create(workspaceId: WorkspaceId, name: String): ApiResult<WorkspaceTag> = client.send(
        HttpMethod.POST,
        tags(workspaceId),
        json(TagBody.serializer(), TagBody(name)),
        WorkspaceTag.serializer(),
        resend = false,
    )

    /** `PUT /api/v9/workspaces/{workspaceId}/tags/{id}` with [name]. The server renames it on every entry. */
    suspend fun rename(workspaceId: WorkspaceId, id: TagId, name: String): ApiResult<WorkspaceTag> = client.send(
        HttpMethod.PUT,
        tags(workspaceId, id.toString()),
        json(TagBody.serializer(), TagBody(name)),
        WorkspaceTag.serializer(),
    )

    /** `DELETE /api/v9/workspaces/{workspaceId}/tags/{id}`. Tokkl answers with no body. */
    suspend fun delete(workspaceId: WorkspaceId, id: TagId): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.DELETE, tags(workspaceId, id.toString()))

    private fun tags(workspaceId: WorkspaceId, vararg path: String) =
        client.server.apiUrl("workspaces", workspaceId.toString(), "tags", *path)
}
