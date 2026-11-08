package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ClientBody
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.catalog.createOnce
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A client as `GET /api/v9/workspaces/{wid}/clients` sends it. Toggl names its workspace `wid`. */
@Serializable
data class Client(
    val id: Long,
    @SerialName("wid") val workspaceId: Long,
    val name: String = "",
    val archived: Boolean = false,
    val notes: String? = null,
)

/** A project's client, for the number of projects each client has. */
@Serializable
class ClientProject(val id: Long, @SerialName("client_id") val clientId: Long? = null)

/**
 * The API v9 client calls of one [workspace] on the configured server. Each call suspends while
 * its request runs.
 */
class ClientsServer(private val api: TokklApi, private val workspace: Long) {

    /** Every client of the workspace, archived ones included. */
    suspend fun list(): ApiResult<List<Client>> = api.clients.list(workspace, ListSerializer(Client.serializer()))

    /** Every project of the workspace, archived ones included, for the number each client has. */
    suspend fun projects(): ApiResult<List<ClientProject>> =
        api.projects.list(workspace, ListSerializer(ClientProject.serializer()), activeBoth = true)

    /**
     * Creates the client once, as [createOnce] does. When the answer was lost, the client named
     * [name] whose id is not in [known] counts as the one it made. Blank [notes] go as null, as
     * the web app sends them.
     *
     * @param known the ids of the clients the page lists, which the create cannot have made.
     */
    suspend fun create(name: String, notes: String?, known: Set<Long>): ApiResult<Client> = createOnce(
        create = { api.clients.create(workspace, ClientBody(name, notes), Client.serializer()) },
        list = ::list,
        made = { it.id !in known && it.workspaceId == workspace && it.name == name },
    )

    suspend fun update(id: Long, name: String, notes: String?): ApiResult<Client> =
        api.clients.update(workspace, id, ClientBody(name, notes), Client.serializer())

    /**
     * Archives the client and, as in Toggl, its projects. Tokkl answers with the ids of the
     * projects it archived, which the page does not need.
     */
    suspend fun archive(id: Long): ApiResult<Unit> = api.clients.archive(workspace, id)

    /** Restores the client and every one of its projects, as the web app asks. */
    suspend fun restore(id: Long): ApiResult<Client> = api.clients.restore(workspace, id, Client.serializer())
}
