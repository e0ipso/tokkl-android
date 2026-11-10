package com.github.e0ipso.tokkl.tags

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
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

/** What the tags page opens over its list. */
sealed interface TagsOverlay {

    data class Rename(val tag: Tag) : TagsOverlay

    data class ConfirmDelete(val tag: Tag) : TagsOverlay
}

/** Every tag of the workspace, sorted as Tokkl sorts them. */
data class TagsData(val tags: List<Tag>) {

    fun with(tag: Tag): TagsData = TagsData((without(tag.id).tags + tag).sortedForList())

    fun without(id: TagId): TagsData = TagsData(tags.filterNot { it.id == id })
}

typealias TagsState = CatalogState<TagsData, TagsOverlay, CatalogChange>

/** The tags on the phone, of the page's workspace, are what the page lists while the server's do not load. */
@Immutable
data class TagsPageState(
    val catalog: TagsState = TagsState(),
    val offline: Boolean = false,
    val cachedTags: List<Tag> = emptyList(),
) {
    /** Whether the page says that changing the tags needs the server. */
    val serverNeeded: Boolean get() = offline || catalog.loadProblem == ServerProblem.Unreachable

    val canChange: Boolean get() = catalog.canChange && !offline

    /** The server's tags once loaded, the phone's until then. */
    val rows: List<TagRow>
        get() = catalog.data?.tags?.map { TagRow(it, fromServer = true) }
            ?: cachedTags.map { TagRow(it, fromServer = false) }

    val hasNotices: Boolean
        get() = serverNeeded || catalog.loadProblem != null || (catalog.loading && catalog.data == null) ||
            (catalog.changeProblem != null && catalog.overlay == null)
}

/**
 * The tags page's state (docs/PRD.md P4): the workspace's tags from the server, loaded each time
 * the page opens or the app comes back to it, and the changes the page sends at once, as
 * [CatalogModel] describes.
 *
 * @param app the app's trips to the background and the network's returns, which load the page again
 *   while it shows ([stateInPage]).
 * @param saved the route's saved state, which keeps the open rename form or delete question for a
 *   new process that restores the page.
 */
class TagsViewModel(
    private val repository: ServerTagsRepository,
    settings: AccountSettingsSource,
    changes: ServerChanges,
    scope: CoroutineScope,
    io: CoroutineContext,
    cachedTags: Flow<List<Tag>>,
    private val offline: StateFlow<Boolean>,
    app: AppForeground,
    saved: SavedStateHandle,
) : ViewModel(scope) {

    private val catalog = CatalogModel<TagsData, TagsOverlay, CatalogChange>(
        repository,
        settings,
        changes,
        viewModelScope,
        io,
        restored = saved.get<String>(SAVED_OVERLAY)?.let(SavedTagsOverlay::decode)?.overlay(),
    )

    /** The list and what is open over it, before the cache and the network join them in [page]. */
    internal val state: StateFlow<TagsState> = catalog.state

    /** The cached rows are empty until the cache is read. */
    val page: StateFlow<TagsPageState> = combine(
        state,
        offline,
        cachedTags.onStart { emit(emptyList()) },
    ) { shown, isOffline, cached ->
        val workspace = workspace()
        TagsPageState(shown, isOffline, cached.filter { workspace == null || it.workspaceId == workspace })
    }.stateInPage(viewModelScope, TagsPageState(state.value, offline.value), app, catalog)

    init {
        // A new process that restores the page opens the form or the question again as it was.
        viewModelScope.launch {
            state.map { it.overlay }.distinctUntilChanged().collect { overlay ->
                saved[SAVED_OVERLAY] = overlay?.let(SavedTagsOverlay::of)?.encode()
            }
        }
    }

    fun workspace(): WorkspaceId? = catalog.workspace()

    fun retry() = catalog.retry()

    fun doneShown() = catalog.doneShown()

    fun askToRename(tag: Tag) = catalog.show(TagsOverlay.Rename(tag))

    fun askToDelete(tag: Tag) = catalog.show(TagsOverlay.ConfirmDelete(tag))

    /** Closes the form or the question, which keeps the tag as it is. */
    fun dismiss() = catalog.dismiss()

    /** Creates a tag named [name], trimmed. */
    fun create(name: String) {
        val trimmed = name.trim()
        val known = state.value.data?.tags.orEmpty().mapTo(HashSet()) { it.id }
        catalog.send(
            request = { workspace -> repository.create(workspace, trimmed, known) },
            apply = TagsData::with,
            done = { CatalogChange.Created(it.name) },
        )
    }

    /** Renames the tag the form is about to [name], trimmed. */
    fun rename(name: String) {
        val tag = (state.value.overlay as? TagsOverlay.Rename)?.tag ?: return
        val trimmed = name.trim()
        catalog.send(
            request = { workspace -> repository.rename(workspace, tag.id, trimmed) },
            apply = TagsData::with,
            done = { CatalogChange.Renamed(it.name) },
        )
    }

    /** Deletes the tag the question is about. */
    fun delete() {
        val tag = (state.value.overlay as? TagsOverlay.ConfirmDelete)?.tag ?: return
        catalog.send(
            request = { workspace -> repository.delete(workspace, tag.id) },
            apply = { data, _ -> data.without(tag.id) },
            done = { CatalogChange.Deleted(tag.name) },
        )
    }

    private companion object {
        const val SAVED_OVERLAY = "tag-overlay"
    }
}

/** By name, ignoring case, as Tokkl sorts them. */
internal fun List<Tag>.sortedForList(): List<Tag> =
    sortedWith(compareBy<Tag, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id.value })
