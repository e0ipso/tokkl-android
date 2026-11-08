package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.getOrElse
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.SignedInApi
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId

/** The workspace's clients on the server, for the clients page. */
class ClientsRepository(client: ApiClientProvider) : CatalogRepository<ClientsData> {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = api.workspace(stored)

    /** The workspace's clients, sorted as Tokkl sorts them, and the number of projects each has. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<ClientsData> = api.call { client ->
        val server = ClientsServer(client, workspace)
        val clients = server.list().getOrElse { return@call it }.filter { it.workspaceId == workspace }.sortedForList()
        server.projects().map { projects ->
            ClientsData(clients, projects.mapNotNull { it.clientId?.let(::ClientId) }.groupingBy { it }.eachCount())
        }
    }

    /**
     * Creates the client named [name] once, as [ClientsServer.create] does. [known] are the ids of
     * the clients the page lists, which the create cannot have made.
     */
    suspend fun create(workspace: WorkspaceId, name: String, notes: String?, known: Set<ClientId>) =
        api.call { ClientsServer(it, workspace).create(name, notes, known) }

    suspend fun update(workspace: WorkspaceId, id: ClientId, name: String, notes: String?) =
        api.call { ClientsServer(it, workspace).update(id, name, notes) }

    /** Archives the client and its projects. */
    suspend fun archive(workspace: WorkspaceId, id: ClientId) = api.call {
        ClientsServer(
            it,
            workspace,
        ).archive(id)
    }

    /** Restores the client and its projects. */
    suspend fun restore(workspace: WorkspaceId, id: ClientId) = api.call {
        ClientsServer(
            it,
            workspace,
        ).restore(id)
    }
}
