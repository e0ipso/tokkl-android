package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogPageModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** What the clients page opens over its list. */
sealed interface ClientsOverlay {

    /** The form for a new client. */
    data object New : ClientsOverlay

    /** The form for [client]. */
    data class Edit(val client: Client) : ClientsOverlay

    /** The question whether to archive [client] and its projects. */
    data class ConfirmArchive(val client: Client) : ClientsOverlay
}

/**
 * What the clients page loads: every client of the workspace, archived ones included, sorted as
 * Tokkl sorts them, and how many projects each one has, archived ones included, by client id.
 */
data class ClientsData(val clients: List<Client>, val projectCounts: Map<Long, Int>) {

    fun projectCount(client: Client): Int = projectCounts[client.id] ?: 0

    /** With [client] as it is after a change, in its place in the order. */
    fun with(client: Client): ClientsData = copy(clients = (clients.filterNot { it.id == client.id } + client).sortedForList())
}

typealias ClientsState = CatalogState<ClientsData, ClientsOverlay>

/**
 * The clients page's state (docs/PRD.md P3): the workspace's clients from the server, loaded each
 * time the page opens or the app comes back to it, and the changes the page sends at once, as
 * [CatalogModel] describes.
 * Archiving a client archives its projects, and restoring it restores them, as in the web app.
 *
 * @param cachedClients the clients on the phone, which the page lists while the server's do not
 *   load.
 */
class ClientsModel(
    client: () -> TogglApiClient?,
    workspaceId: () -> Long?,
    refreshCache: () -> Unit,
    scope: CoroutineScope,
    io: CoroutineContext,
    val cachedClients: Flow<List<CachedClient>> = flowOf(emptyList()),
) : CatalogPageModel {

    private val catalog = CatalogModel<ClientsData, ClientsOverlay>(client, workspaceId, refreshCache, scope, io, ::read)

    val state: StateFlow<ClientsState> = catalog.state

    fun workspace(): Long? = catalog.workspace()

    override fun open() = catalog.open()

    fun retry() = catalog.retry()

    override fun reconnected() = catalog.reconnected()

    override fun close() = catalog.close()

    override fun wentToBackground() = catalog.wentToBackground()

    override fun cameToForeground() = catalog.cameToForeground()

    fun doneShown() = catalog.doneShown()

    fun newClient() = catalog.show(ClientsOverlay.New)

    fun edit(client: Client) = catalog.show(ClientsOverlay.Edit(client))

    /** Asks whether to archive [client], since that archives its projects too. */
    fun askToArchive(client: Client) = catalog.show(ClientsOverlay.ConfirmArchive(client))

    /** Closes the form or the question. */
    fun dismiss() = catalog.dismiss()

    /** Creates the client, or saves the one the form edits. Blank [notes] go as none. */
    fun save(name: String, notes: String) {
        val editing = when (val overlay = state.value.overlay) {
            ClientsOverlay.New -> null
            is ClientsOverlay.Edit -> overlay.client
            is ClientsOverlay.ConfirmArchive, null -> return
        }
        val trimmed = name.trim()
        val note = notes.trim().ifEmpty { null }
        catalog.send(
            request = { api, workspace ->
                val server = ClientsServer(api, workspace)
                if (editing == null) server.create(trimmed, note) else server.update(editing.id, trimmed, note)
            },
            apply = ClientsData::with,
            done = { saved -> if (editing == null) CatalogChange.Created(saved.name) else CatalogChange.Saved(saved.name) },
        )
    }

    /** Archives the client the question is about, with its projects. */
    fun archive() {
        val client = (state.value.overlay as? ClientsOverlay.ConfirmArchive)?.client ?: return
        catalog.send(
            request = { api, workspace -> ClientsServer(api, workspace).archive(client.id) },
            apply = { data, _ -> data.with(client.copy(archived = true)) },
            done = { CatalogChange.Archived(client.name) },
        )
    }

    /** Restores [client] with its projects. */
    fun restore(client: Client) {
        catalog.send(
            request = { api, workspace -> ClientsServer(api, workspace).restore(client.id) },
            apply = ClientsData::with,
            done = { saved -> CatalogChange.Restored(saved.name) },
        )
    }

    /** The workspace's clients and the number of projects each has. */
    private fun read(api: TogglApiClient, workspace: Long): ApiResult<ClientsData> {
        val server = ClientsServer(api, workspace)
        val clients = when (val answer = server.list()) {
            is ApiResult.Success -> answer.value.filter { it.workspaceId == workspace }.sortedForList()
            is ApiResult.Failure -> return answer
        }
        val counts = when (val answer = server.projects()) {
            is ApiResult.Success -> answer.value.mapNotNull { it.clientId }.groupingBy { it }.eachCount()
            is ApiResult.Failure -> return answer
        }
        return ApiResult.Success(ClientsData(clients, counts))
    }
}

/** By name, ignoring case, as Tokkl sorts them. */
private fun List<Client>.sortedForList(): List<Client> =
    sortedWith(compareBy<Client, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
