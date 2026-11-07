package com.github.e0ipso.tokkl.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.CachedTag
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogPageEvents
import com.github.e0ipso.tokkl.catalog.CatalogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.coroutines.CoroutineContext

/** What the tags page opens over its list. */
sealed interface TagsOverlay {

    /** The form that renames [tag]. */
    data class Rename(val tag: Tag) : TagsOverlay

    /** The question whether to delete [tag]. */
    data class ConfirmDelete(val tag: Tag) : TagsOverlay
}

/** Every tag of the workspace, sorted as Tokkl sorts them. */
data class TagsData(val tags: List<Tag>) {

    /** With [tag] as the server sent it after a change, in its place in the order. */
    fun with(tag: Tag): TagsData = TagsData((without(tag.id).tags + tag).sortedForList())

    fun without(id: Long): TagsData = TagsData(tags.filterNot { it.id == id })
}

typealias TagsState = CatalogState<TagsData, TagsOverlay>

/**
 * The tags page's state (docs/PRD.md P4): the workspace's tags from the server, loaded each time
 * the page opens or the app comes back to it, and the changes the page sends at once, as
 * [CatalogModel] describes.
 *
 * @param repository the workspace's tags on the server.
 * @param cachedTags the tags on the phone, which the page lists while the server's do not load.
 */
class TagsViewModel(
    private val repository: TagsRepository,
    workspaceId: () -> Long?,
    refreshCache: () -> Unit,
    scope: CoroutineScope,
    io: CoroutineContext,
    val cachedTags: Flow<List<CachedTag>> = flowOf(emptyList()),
) : ViewModel(scope),
    CatalogPageEvents {

    private val catalog = CatalogModel<TagsData, TagsOverlay>(
        repository,
        workspaceId,
        refreshCache,
        viewModelScope,
        io,
    )

    val state: StateFlow<TagsState> = catalog.state

    fun workspace(): Long? = catalog.workspace()

    override fun open() = catalog.open()

    fun retry() = catalog.retry()

    override fun reconnected() = catalog.reconnected()

    override fun close() = catalog.close()

    override fun wentToBackground() = catalog.wentToBackground()

    override fun cameToForeground() = catalog.cameToForeground()

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
}

/** By name, ignoring case, as Tokkl sorts them. */
internal fun List<Tag>.sortedForList(): List<Tag> =
    sortedWith(compareBy<Tag, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
