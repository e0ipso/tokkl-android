package com.github.e0ipso.tokkl.clients

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.platform.stateInPage
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** What the client form holds, as typed. */
data class ClientDraft(val name: String = "", val notes: String = "") {
    companion object {
        fun of(client: Client) = ClientDraft(client.name, client.notes.orEmpty())
    }
}

/** What the clients page opens over its list. */
sealed interface ClientsOverlay {

    sealed interface Form : ClientsOverlay {
        val draft: ClientDraft

        fun holding(draft: ClientDraft): Form
    }

    data class New(override val draft: ClientDraft = ClientDraft()) : Form {
        override fun holding(draft: ClientDraft) = copy(draft = draft)
    }

    data class Edit(val client: Client, override val draft: ClientDraft = ClientDraft.of(client)) : Form {
        override fun holding(draft: ClientDraft) = copy(draft = draft)
    }

    data class ConfirmArchive(val client: Client) : ClientsOverlay
}

/**
 * What the clients page loads: every client of the workspace, archived ones included, sorted as
 * Tokkl sorts them, and how many projects each one has, archived ones included, by client id.
 */
data class ClientsData(val clients: List<Client>, val projectCounts: Map<ClientId, Int>) {

    fun projectCount(client: Client): Int = projectCounts[client.id] ?: 0

    fun with(client: Client): ClientsData = copy(
        clients = (clients.filterNot { it.id == client.id } + client).sortedForList(),
    )
}

typealias ClientsState = CatalogState<ClientsData, ClientsOverlay, CatalogChange>

/** The clients on the phone, of the page's workspace, are what the page lists while the server's do not load. */
@Immutable
data class ClientsPageState(
    val catalog: ClientsState = ClientsState(),
    val offline: Boolean = false,
    val cachedClients: List<Client> = emptyList(),
) {
    /** Whether the page says that changing the clients needs the server. */
    val serverNeeded: Boolean get() = offline || catalog.loadProblem == ServerProblem.Unreachable

    val canChange: Boolean get() = catalog.canChange && !offline

    val formOpen: Boolean get() = catalog.overlay is ClientsOverlay.Form
}

/**
 * The clients page's state (docs/PRD.md P3): the workspace's clients from the server, loaded each
 * time the page opens or the app comes back to it, and the changes the page sends at once, as
 * [CatalogModel] describes.
 * Archiving a client archives its projects, and restoring it restores them, as in the web app.
 *
 * @param app the app's trips to the background and the network's returns, which load the page again
 *   while it shows ([stateInPage]).
 * @param saved the route's saved state, which keeps the open form and what it holds, or the open
 *   question, for a new process that restores the page.
 */
class ClientsViewModel(
    private val repository: ServerClientsRepository,
    settings: AccountSettingsSource,
    changes: ServerChanges,
    scope: CoroutineScope,
    io: CoroutineContext,
    cachedClients: Flow<List<Client>>,
    private val offline: StateFlow<Boolean>,
    app: AppForeground,
    private val saved: SavedStateHandle,
) : ViewModel(scope) {

    private val catalog = CatalogModel<ClientsData, ClientsOverlay, CatalogChange>(
        repository,
        settings,
        changes,
        viewModelScope,
        io,
        restored = saved.get<String>(SAVED_OVERLAY)?.let(SavedClientsOverlay::decode)?.overlay(),
    )

    /** The list and what is open over it, before the cache and the network join them in [page]. */
    internal val state: StateFlow<ClientsState> = catalog.state

    /** The cached rows are empty until the cache is read. */
    val page: StateFlow<ClientsPageState> = combine(
        state,
        offline,
        cachedClients.onStart { emit(emptyList()) },
    ) { shown, isOffline, cached ->
        val workspace = workspace()
        ClientsPageState(shown, isOffline, cached.filter { workspace == null || it.workspaceId == workspace })
    }.stateInPage(viewModelScope, ClientsPageState(state.value, offline.value), app, catalog)

    init {
        // A new process that restores the page opens the form or the question again as it was.
        viewModelScope.launch {
            state.map { it.overlay }.distinctUntilChanged().collect { overlay ->
                saved[SAVED_OVERLAY] = overlay?.let(SavedClientsOverlay::of)?.encode()
            }
        }
    }

    fun workspace(): WorkspaceId? = catalog.workspace()

    fun retry() = catalog.retry()

    fun doneShown() = catalog.doneShown()

    fun newClient() = catalog.show(ClientsOverlay.New())

    fun edit(client: Client) = catalog.show(ClientsOverlay.Edit(client))

    /** Asks whether to archive [client], since that archives its projects too. */
    fun askToArchive(client: Client) = catalog.show(ClientsOverlay.ConfirmArchive(client))

    fun editDraft(draft: ClientDraft) = catalog.change { (it as? ClientsOverlay.Form)?.holding(draft) ?: it }

    fun dismiss() = catalog.dismiss()

    /** Creates the client, or saves the one the form edits. Blank [notes] go as none. */
    fun save(name: String, notes: String) {
        val editing = when (val overlay = state.value.overlay) {
            is ClientsOverlay.New -> null
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
            done = { client ->
                if (editing == null) {
                    CatalogChange.Created(client.name)
                } else {
                    CatalogChange.Saved(client.name)
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
            done = { restored -> CatalogChange.Restored(restored.name) },
        )
    }

    private companion object {
        const val SAVED_OVERLAY = "client-form"
    }
}

/** By name, ignoring case, as Tokkl sorts them. */
internal fun List<Client>.sortedForList(): List<Client> =
    sortedWith(compareBy<Client, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id.value })
