package com.github.e0ipso.tokkl.clients

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import com.github.e0ipso.tokkl.catalog.PageEvents
import com.github.e0ipso.tokkl.catalog.followTrips
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.AppEvent
import com.github.e0ipso.tokkl.session.DefaultWorkspace
import com.github.e0ipso.tokkl.session.RefreshRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext

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
    fun with(client: Client): ClientsData = copy(
        clients = (clients.filterNot { it.id == client.id } + client).sortedForList(),
    )
}

typealias ClientsState = CatalogState<ClientsData, ClientsOverlay>

/**
 * The clients page: what the server sent and what is open over it in [catalog], whether the
 * phone is [offline], and the clients on the phone of the page's workspace, which the page lists
 * while the server's do not load.
 */
@Immutable
data class ClientsPageState(
    val catalog: ClientsState = ClientsState(),
    val offline: Boolean = false,
    val cachedClients: List<CachedClient> = emptyList(),
)

/**
 * The clients page's state (docs/PRD.md P3): the workspace's clients from the server, loaded each
 * time the page opens or the app comes back to it, and the changes the page sends at once, as
 * [CatalogModel] describes.
 * Archiving a client archives its projects, and restoring it restores them, as in the web app.
 *
 * @param repository the workspace's clients on the server.
 * @param cachedClients the clients on the phone, which the page lists while the server's do not
 *   load.
 * @param offline whether the phone is offline, when nothing can change.
 * @param appEvents MainActivity's trips to the background and back, which load the page again.
 */
class ClientsViewModel(
    private val repository: ClientsRepository,
    workspaceId: DefaultWorkspace,
    refreshCache: RefreshRequest,
    scope: CoroutineScope,
    io: CoroutineContext,
    cachedClients: Flow<List<CachedClient>>,
    offline: StateFlow<Boolean>,
    appEvents: Flow<AppEvent>,
) : ViewModel(scope),
    PageEvents {

    private val catalog = CatalogModel<ClientsData, ClientsOverlay>(
        repository,
        workspaceId,
        refreshCache,
        viewModelScope,
        io,
    )

    val state: StateFlow<ClientsState> = catalog.state

    /** What the page shows, while it reads it. The cached rows are empty until the cache is read. */
    val page: StateFlow<ClientsPageState> = combine(
        state,
        offline,
        cachedClients.onStart { emit(emptyList()) },
    ) { shown, isOffline, cached ->
        val workspace = workspace()
        ClientsPageState(shown, isOffline, cached.filter { workspace == null || it.workspaceId == workspace })
    }.stateIn(viewModelScope, whileShown, ClientsPageState(state.value, offline.value))

    init {
        followTrips(appEvents, viewModelScope)
    }

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
        val known = state.value.data?.clients.orEmpty().mapTo(HashSet()) { it.id }
        catalog.send(
            request = { workspace ->
                if (editing == null) {
                    repository.create(workspace, trimmed, note, known)
                } else {
                    repository.update(workspace, editing.id, trimmed, note)
                }
            },
            apply = ClientsData::with,
            done = { saved ->
                if (editing == null) {
                    CatalogChange.Created(
                        saved.name,
                    )
                } else {
                    CatalogChange.Saved(saved.name)
                }
            },
        )
    }

    /** Archives the client the question is about, with its projects. */
    fun archive() {
        val client = (state.value.overlay as? ClientsOverlay.ConfirmArchive)?.client ?: return
        catalog.send(
            request = { workspace -> repository.archive(workspace, client.id) },
            apply = { data, _ -> data.with(client.copy(archived = true)) },
            done = { CatalogChange.Archived(client.name) },
        )
    }

    /** Restores [client] with its projects. */
    fun restore(client: Client) {
        catalog.send(
            request = { workspace -> repository.restore(workspace, client.id) },
            apply = ClientsData::with,
            done = { saved -> CatalogChange.Restored(saved.name) },
        )
    }
}

/** By name, ignoring case, as Tokkl sorts them. */
internal fun List<Client>.sortedForList(): List<Client> =
    sortedWith(compareBy<Client, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
