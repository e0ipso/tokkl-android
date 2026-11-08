package com.github.e0ipso.tokkl.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A workspace's clients. The answers read as the caller's [DeserializationStrategy], since the
 * clients page owns the shape it reads.
 */
interface ClientsApi {

    /** `GET /api/v9/workspaces/{workspaceId}/clients?status=both`, archived clients included. */
    suspend fun <T> list(workspaceId: Long, deserializer: DeserializationStrategy<T>): ApiResult<T>

    /** `POST /api/v9/workspaces/{workspaceId}/clients`, sent once. */
    suspend fun <T> create(workspaceId: Long, body: ClientBody, deserializer: DeserializationStrategy<T>): ApiResult<T>

    /** `PUT /api/v9/workspaces/{workspaceId}/clients/{id}`. */
    suspend fun <T> update(
        workspaceId: Long,
        id: Long,
        body: ClientBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T>

    /**
     * `POST /api/v9/workspaces/{workspaceId}/clients/{id}/archive`, which archives its projects
     * too. Tokkl answers with the ids of the projects it archived, which this call does not read.
     */
    suspend fun archive(workspaceId: Long, id: Long): ApiResult<Unit>

    /**
     * `POST /api/v9/workspaces/{workspaceId}/clients/{id}/restore` with `restore_all_projects`,
     * as the web app asks, so its projects come back too.
     */
    suspend fun <T> restore(workspaceId: Long, id: Long, deserializer: DeserializationStrategy<T>): ApiResult<T>
}

/** A client's name and notes. Null [notes] go as null. */
@Serializable
data class ClientBody(val name: String, val notes: String?)

@Serializable
private class RestoreBody(@SerialName("restore_all_projects") val restoreAllProjects: Boolean)

/** [ClientsApi] over [client]. */
internal class HttpClientsApi(private val client: TogglApiClient) : ClientsApi {

    override suspend fun <T> list(workspaceId: Long, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        client.get(clients(workspaceId).newBuilder().addQueryParameter("status", "both").build(), deserializer)

    override suspend fun <T> create(
        workspaceId: Long,
        body: ClientBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.POST,
        clients(workspaceId),
        json(ClientBody.serializer(), body),
        deserializer,
        resend = false,
    )

    override suspend fun <T> update(
        workspaceId: Long,
        id: Long,
        body: ClientBody,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.PUT,
        clients(workspaceId, id.toString()),
        json(ClientBody.serializer(), body),
        deserializer,
    )

    override suspend fun archive(workspaceId: Long, id: Long): ApiResult<Unit> =
        client.sendNoContent(HttpMethod.POST, clients(workspaceId, id.toString(), "archive"), EMPTY_BODY)

    override suspend fun <T> restore(
        workspaceId: Long,
        id: Long,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = client.send(
        HttpMethod.POST,
        clients(workspaceId, id.toString(), "restore"),
        json(RestoreBody.serializer(), RestoreBody(restoreAllProjects = true)),
        deserializer,
    )

    private fun clients(workspaceId: Long, vararg path: String) =
        client.server.api("workspaces", workspaceId.toString(), "clients", *path)
}
