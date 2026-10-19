package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedTag
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.catalog.CatalogModel
import com.github.e0ipso.tokkl.catalog.CatalogState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

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
 * the page opens, and the changes the page sends at once, as [CatalogModel] describes.
 *
 * @param cachedTags the tags on the phone, which the page lists while the server's do not load.
 */
class TagsModel(
    client: () -> TogglApiClient?,
    workspaceId: () -> Long?,
    refreshCache: () -> Unit,
    scope: CoroutineScope,
    io: CoroutineContext,
    val cachedTags: Flow<List<CachedTag>> = flowOf(emptyList()),
) {

    private val catalog = CatalogModel<TagsData, TagsOverlay>(client, workspaceId, refreshCache, scope, io, ::read)

    val state: StateFlow<TagsState> = catalog.state

    fun workspace(): Long? = catalog.workspace()

    fun open() = catalog.open()

    fun retry() = catalog.retry()

    fun reconnected() = catalog.reconnected()

    fun close() = catalog.close()

    fun doneShown() = catalog.doneShown()

    fun askToRename(tag: Tag) = catalog.show(TagsOverlay.Rename(tag))

    fun askToDelete(tag: Tag) = catalog.show(TagsOverlay.ConfirmDelete(tag))

    /** Closes the form or the question, which keeps the tag as it is. */
    fun dismiss() = catalog.dismiss()

    /** Creates a tag named [name], trimmed. */
    fun create(name: String) {
        val trimmed = name.trim()
        catalog.send(
            request = { api, workspace -> TagsServer(api, workspace).create(trimmed) },
            apply = TagsData::with,
            done = { CatalogChange.Created(it.name) },
        )
    }

    /** Renames the tag the form is about to [name], trimmed. */
    fun rename(name: String) {
        val tag = (state.value.overlay as? TagsOverlay.Rename)?.tag ?: return
        val trimmed = name.trim()
        catalog.send(
            request = { api, workspace -> TagsServer(api, workspace).rename(tag.id, trimmed) },
            apply = TagsData::with,
            done = { CatalogChange.Renamed(it.name) },
        )
    }

    /** Deletes the tag the question is about. */
    fun delete() {
        val tag = (state.value.overlay as? TagsOverlay.ConfirmDelete)?.tag ?: return
        catalog.send(
            request = { api, workspace -> TagsServer(api, workspace).delete(tag.id) },
            apply = { data, _ -> data.without(tag.id) },
            done = { CatalogChange.Deleted(tag.name) },
        )
    }

    private fun read(api: TogglApiClient, workspace: Long): ApiResult<TagsData> =
        when (val answer = TagsServer(api, workspace).list()) {
            is ApiResult.Success -> ApiResult.Success(TagsData(answer.value.filter { it.workspaceId == workspace }.sortedForList()))
            is ApiResult.Failure -> answer
        }
}

/** By name, ignoring case, as Tokkl sorts them. */
private fun List<Tag>.sortedForList(): List<Tag> =
    sortedWith(compareBy<Tag, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
