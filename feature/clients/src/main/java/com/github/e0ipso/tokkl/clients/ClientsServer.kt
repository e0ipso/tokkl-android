package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ClientBody
import com.github.e0ipso.tokkl.api.ProjectClient
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.WorkspaceClient
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId

/**
 * The API v9 client calls of one [workspace] on the configured server, read as the model's
 * [Client]. Each call suspends while its request runs.
 */
class ClientsServer(private val api: TokklApi, private val workspace: WorkspaceId) {

    /** Every client of the workspace, archived ones included. */
    suspend fun list(): ApiResult<List<Client>> = api.clients.list(workspace).map { it.map(WorkspaceClient::toClient) }

    /** Every project of the workspace, archived ones included, for the number each client has. */
    suspend fun projects(): ApiResult<List<ProjectClient>> = api.projects.clients(workspace)

    /**
     * Creates the client once, as [createOnce] does. When the answer was lost, the client named
     * [name] whose id is not in [known] counts as the one it made. Blank [notes] go as null, as
     * the web app sends them.
     *
     * @param known the ids of the clients the page lists, which the create cannot have made.
     */
    suspend fun create(name: String, notes: String?, known: Set<ClientId>): ApiResult<Client> = createOnce(
        create = { api.clients.create(workspace, ClientBody(name, notes)).map { it.toClient() } },
        list = ::list,
        made = { it.id !in known && it.workspaceId == workspace && it.name == name },
    )

    suspend fun update(id: ClientId, name: String, notes: String?): ApiResult<Client> =
        api.clients.update(workspace, id, ClientBody(name, notes)).map { it.toClient() }

    /**
     * Archives the client and, as in Toggl, its projects. Tokkl answers with the ids of the
     * projects it archived, which the page does not need.
     */
    suspend fun archive(id: ClientId): ApiResult<Unit> = api.clients.archive(workspace, id)

    /** Restores the client and every one of its projects, as the web app asks. */
    suspend fun restore(id: ClientId): ApiResult<Client> = api.clients.restore(workspace, id).map { it.toClient() }
}

private fun WorkspaceClient.toClient() = Client(id, workspaceId, name, archived, notes)
