package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ClientBody
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.WorkspaceClient
import com.github.e0ipso.tokkl.api.getOrElse
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.catalog.managedWorkspace
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.signedIn

/**
 * The workspace's clients on the signed-in account's server, from the API v9 client calls, read
 * as the model's [Client]. Each call suspends while its request runs.
 */
class ServerClientsRepository(private val client: ApiClientProvider) : CatalogRepository<ClientsData> {

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = client.managedWorkspace(stored)

    /** The workspace's clients, sorted as Tokkl sorts them, and the number of projects each has. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<ClientsData> = client.signedIn { api ->
        val clients = api.clientsOf(workspace).getOrElse { return@signedIn it }
            .filter { it.workspaceId == workspace }
            .sortedForList()
        // Every project of the workspace, archived ones included, for the number each client has.
        api.projects.clients(workspace).map { projects ->
            ClientsData(clients, projects.mapNotNull { it.clientId }.groupingBy { it }.eachCount())
        }
    }

    /**
     * Creates the client once, as [createOnce] does. [known] are the ids of the clients the page
     * lists, which the create cannot have made. When the answer was lost, the client named [name]
     * whose id is not in [known] counts as the one it made. Blank [notes] go as null, as the web
     * app sends them.
     */
    suspend fun create(workspace: WorkspaceId, name: String, notes: String?, known: Set<ClientId>): ApiResult<Client> =
        client.signedIn { api ->
            createOnce(
                create = { api.clients.create(workspace, ClientBody(name, notes)).map { it.toClient() } },
                list = { api.clientsOf(workspace) },
                made = { it.id !in known && it.workspaceId == workspace && it.name == name },
            )
        }

    suspend fun update(workspace: WorkspaceId, id: ClientId, name: String, notes: String?): ApiResult<Client> =
        client.signedIn { api -> api.clients.update(workspace, id, ClientBody(name, notes)).map { it.toClient() } }

    /**
     * Archives the client and, as in Toggl, its projects. Tokkl answers with the ids of the
     * projects it archived, which the page does not need.
     */
    suspend fun archive(workspace: WorkspaceId, id: ClientId): ApiResult<Unit> =
        client.signedIn { it.clients.archive(workspace, id) }

    /** Restores the client and every one of its projects, as the web app asks. */
    suspend fun restore(workspace: WorkspaceId, id: ClientId): ApiResult<Client> =
        client.signedIn { api -> api.clients.restore(workspace, id).map { it.toClient() } }
}

/** Every client of [workspace], archived ones included. */
private suspend fun TokklApi.clientsOf(workspace: WorkspaceId): ApiResult<List<Client>> =
    clients.list(workspace).map { it.map(WorkspaceClient::toClient) }

private fun WorkspaceClient.toClient() = Client(id, workspaceId, name, archived, notes)
