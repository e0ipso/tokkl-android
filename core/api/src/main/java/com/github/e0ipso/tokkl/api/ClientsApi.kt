package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A client's name and notes. Null [notes] go as null. */
@Serializable
data class ClientBody(val name: String, val notes: String?)

@Serializable
private class RestoreBody(@SerialName("restore_all_projects") val restoreAllProjects: Boolean)

/** A workspace's clients. */
class ClientsApi internal constructor(private val client: TogglApiClient) {

    /** `GET /api/v9/workspaces/{workspaceId}/clients?status=both`, archived clients included. */
    suspend fun list(workspaceId: WorkspaceId): ApiResult<List<WorkspaceClient>> = client.get(
        clients(workspaceId).newBuilder().addQueryParameter("status", "both").build(),
        ListSerializer(WorkspaceClient.serializer()),
    )

    /** `POST /api/v9/workspaces/{workspaceId}/clients`, sent once. */
    suspend fun create(workspaceId: WorkspaceId, body: ClientBody): ApiResult<WorkspaceClient> = client.send(
        HttpMethod.POST,
        clients(workspaceId),
        json(ClientBody.serializer(), body),
        WorkspaceClient.serializer(),
        resend = false,
    )

    /** `PUT /api/v9/workspaces/{workspaceId}/clients/{id}`. */
    suspend fun update(workspaceId: WorkspaceId, id: ClientId, body: ClientBody): ApiResult<WorkspaceClient> =
        client.send(
            HttpMethod.PUT,
            clients(workspaceId, id.toString()),
            json(ClientBody.serializer(), body),
            WorkspaceClient.serializer(),
        )

    /**
     * `POST /api/v9/workspaces/{workspaceId}/clients/{id}/archive`, which archives its projects
     * too. Tokkl answers with the ids of the projects it archived, which this call does not read.
     */
    suspend fun archive(workspaceId: WorkspaceId, id: ClientId): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.POST, clients(workspaceId, id.toString(), "archive"), EMPTY_BODY)

    /**
     * `POST /api/v9/workspaces/{workspaceId}/clients/{id}/restore` with `restore_all_projects`,
     * as the web app asks, so its projects come back too.
     */
    suspend fun restore(workspaceId: WorkspaceId, id: ClientId): ApiResult<WorkspaceClient> = client.send(
        HttpMethod.POST,
        clients(workspaceId, id.toString(), "restore"),
        json(RestoreBody.serializer(), RestoreBody(restoreAllProjects = true)),
        WorkspaceClient.serializer(),
    )

    private fun clients(workspaceId: WorkspaceId, vararg path: String) =
        client.server.api("workspaces", workspaceId.toString(), "clients", *path)
}
