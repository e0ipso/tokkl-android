package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.WorkspaceTag
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.catalog.managedWorkspace
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.signedIn

/**
 * The workspace's tags, for the tags page. [ServerTagsRepository] reads and changes them on the
 * server; the view model's unit tests stand in for it without a server.
 */
interface TagsRepository : CatalogRepository<TagsData> {

    /**
     * Creates the tag named [name] once. [known] are the ids of the tags the page lists, which the
     * create cannot have made.
     */
    suspend fun create(workspace: WorkspaceId, name: String, known: Set<TagId>): ApiResult<Tag>

    suspend fun rename(workspace: WorkspaceId, id: TagId, name: String): ApiResult<Tag>

    suspend fun delete(workspace: WorkspaceId, id: TagId): ApiResult<Unit>
}

/**
 * The workspace's tags on the signed-in account's server, from the API v9 tag calls, read as the
 * model's [Tag]. Each call suspends while its request runs.
 */
class ServerTagsRepository(private val client: ApiClientProvider) : TagsRepository {

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = client.managedWorkspace(stored)

    /** The workspace's tags, sorted as Tokkl sorts them. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<TagsData> = client.signedIn { api ->
        api.tagsOf(workspace).map { tags -> TagsData(tags.filter { it.workspaceId == workspace }.sortedForList()) }
    }

    /**
     * Creates the tag once, as [createOnce] does. When the answer was lost, the tag named [name]
     * whose id is not in [known] counts as the one it made.
     */
    override suspend fun create(workspace: WorkspaceId, name: String, known: Set<TagId>) = client.signedIn { api ->
        createOnce(
            create = { api.tags.create(workspace, name).map { it.toTag() } },
            list = { api.tagsOf(workspace) },
            made = { it.id !in known && it.workspaceId == workspace && it.name == name },
        )
    }

    /** Renames the tag. The server renames it on every entry that has it. */
    override suspend fun rename(workspace: WorkspaceId, id: TagId, name: String) =
        client.signedIn { api -> api.tags.rename(workspace, id, name).map { it.toTag() } }

    /** Deletes the tag, which comes off every entry that has it. Tokkl answers with an empty body. */
    override suspend fun delete(workspace: WorkspaceId, id: TagId) = client.signedIn { it.tags.delete(workspace, id) }
}

/** Every tag of [workspace]. */
private suspend fun TokklApi.tagsOf(workspace: WorkspaceId): ApiResult<List<Tag>> =
    tags.list(workspace).map { tags -> tags.map { it.toTag() } }

private fun WorkspaceTag.toTag() = Tag(id, workspaceId, name)
