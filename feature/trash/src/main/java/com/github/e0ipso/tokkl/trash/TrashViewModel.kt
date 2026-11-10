package com.github.e0ipso.tokkl.trash

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.CatalogState
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.platform.stateInPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlin.coroutines.CoroutineContext

/** A change the server accepted, for the message that confirms it. */
sealed interface TrashChange {
    data object Restored : TrashChange

    data object Purged : TrashChange

    /** The trash is empty now, after [count] entries went for good. */
    data class Emptied(val count: Int) : TrashChange
}

/** The question whether to empty the trash, which the page opens over the list. */
data object EmptyTrash

/**
 * What the trash page shows: the deleted entries from the server as [CatalogState.data], or null
 * until they load, and [EmptyTrash] while the page asks whether to empty the trash.
 */
typealias TrashState = CatalogState<List<DeletedEntry>, EmptyTrash, TrashChange>

@Immutable
data class TrashPageState(
    val trash: TrashState = TrashState(),
    val offline: Boolean = false,
    val settings: AccountSettings? = null,
) {
    /** Whether the page says that the trash needs the server. */
    val serverNeeded: Boolean get() = offline || trash.loadProblem == ServerProblem.Unreachable

    val canChange: Boolean get() = trash.canChange && !offline

    /** The deleted entries once loaded, and none while the server is needed: the phone keeps no copy. */
    val entries: List<DeletedEntry>? get() = trash.data.takeUnless { serverNeeded }

    val hasNotices: Boolean
        get() = serverNeeded || trash.loadProblem != null || (trash.loading && trash.data == null) ||
            (trash.changeProblem != null && trash.overlay == null)
}

/**
 * The trash page's state (docs/PRD.md H7): the deleted entries from `GET /api/app/trash`, loaded
 * each time the page opens and each time the app comes back to it, through a [CatalogModel], as on
 * the catalog pages. A load keeps the list and the question open over it until the answer comes,
 * and a load that read the trash before a change the server accepted reads it again. Nothing is
 * queued, so offline nothing changes. After a restore, [changes] reloads the offline cache, so
 * the history shows the entry again. A change the server refuses with a 404, as for an entry no
 * longer in the trash, loads the list again.
 *
 * A server without the trash answers the list with a 404, or with something that is not a list.
 * Then the page tells the capabilities, through [trashMissing] on [io], which hide the page, rather
 * than the page failing.
 *
 * Call every function on the main thread.
 *
 * @param repository the account's trash on the server. Runs on [io].
 * @param changes sends each change in the application's scope.
 * @param app the app's trips to the background and the network's returns, which load the page again
 *   while it shows ([stateInPage]).
 */
class TrashViewModel(
    private val repository: ServerTrashRepository,
    changes: ServerChanges,
    trashMissing: suspend () -> Unit,
    scope: CoroutineScope,
    io: CoroutineContext,
    settings: AccountSettingsSource,
    offline: StateFlow<Boolean>,
    app: AppForeground,
) : ViewModel(scope) {

    private val catalog = CatalogModel<List<DeletedEntry>, EmptyTrash, TrashChange>(
        object : CatalogRepository<List<DeletedEntry>> {
            override suspend fun workspace(stored: WorkspaceId?) = repository.workspace(stored)

            override suspend fun load(workspace: WorkspaceId) = repository.list().also {
                if (it is ApiResult.Failure && it.error.meansNoTrash()) trashMissing()
            }
        },
        settings,
        changes,
        viewModelScope,
        io,
        reloadWhenMissing = true,
    )

    /** The trash and what is open over it, before the network and the settings join them in [page]. */
    internal val state: StateFlow<TrashState> = catalog.state

    val page: StateFlow<TrashPageState> = combine(state, offline, settings.state, ::TrashPageState)
        .stateInPage(viewModelScope, TrashPageState(state.value, offline.value, settings.state.value), app, catalog)

    /** Loads the trash again, as after a failure. */
    fun retry() = catalog.retry()

    /** The page has said that [TrashState.done] happened. */
    fun doneShown() = catalog.doneShown()

    /** Opens the question whether to empty the trash, while there is something to empty. */
    fun askToEmpty() {
        if (!state.value.data.isNullOrEmpty()) catalog.show(EmptyTrash)
    }

    /** Closes the question, which keeps the trash as it is. */
    fun dismiss() = catalog.dismiss()

    fun restore(id: EntryId) = catalog.send(
        request = { repository.restore(id) },
        apply = { entries, _ -> entries.filterNot { it.id == id } },
        done = { TrashChange.Restored },
    )

    /** Deletes entry [id] for good, with no question, as the web app does. */
    fun purge(id: EntryId) = catalog.send(
        request = { repository.purge(id) },
        apply = { entries, _ -> entries.filterNot { it.id == id } },
        done = { TrashChange.Purged },
        refresh = false,
    )

    /** Empties the trash, once the question is open. */
    fun empty() {
        if (state.value.overlay == null) return
        catalog.send(
            request = { repository.purgeAll() },
            apply = { _, _ -> emptyList() },
            done = { TrashChange.Emptied(it) },
            refresh = false,
        )
    }
}

/** A 404, or a 2xx that is not a list, as the capability probe reads them: the server has no trash. */
private fun ApiError.meansNoTrash(): Boolean =
    (this is ApiError.UnexpectedStatus && status == HttpStatus.NOT_FOUND) || this is ApiError.InvalidResponse
