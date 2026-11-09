package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.workspace
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.SignedInApi

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

/** The workspace's tags on the signed-in account's server, through [TagsServer]. */
class ServerTagsRepository(client: ApiClientProvider) : TagsRepository {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = api.workspace(stored)

    /** The workspace's tags, sorted as Tokkl sorts them. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<TagsData> = api.call { client ->
        TagsServer(client, workspace).list().map { tags ->
            TagsData(tags.filter { it.workspaceId == workspace }.sortedForList())
        }
    }

    /** As [TagsServer.create] does. */
    override suspend fun create(workspace: WorkspaceId, name: String, known: Set<TagId>) =
        api.call { TagsServer(it, workspace).create(name, known) }

    override suspend fun rename(workspace: WorkspaceId, id: TagId, name: String) =
        api.call { TagsServer(it, workspace).rename(id, name) }

    override suspend fun delete(workspace: WorkspaceId, id: TagId) = api.call { TagsServer(it, workspace).delete(id) }
}
