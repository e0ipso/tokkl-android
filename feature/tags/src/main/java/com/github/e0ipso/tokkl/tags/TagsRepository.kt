package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.SignedInApi
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId

/** The workspace's tags on the server, for the tags page. */
class TagsRepository(client: ApiClientProvider) : CatalogRepository<TagsData> {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = api.workspace(stored)

    /** The workspace's tags, sorted as Tokkl sorts them. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<TagsData> = api.call { client ->
        when (val answer = TagsServer(client, workspace).list()) {
            is ApiResult.Success -> ApiResult.Success(
                TagsData(answer.value.filter { it.workspaceId == workspace }.sortedForList()),
            )

            is ApiResult.Failure -> answer
        }
    }

    /**
     * Creates the tag named [name] once, as [TagsServer.create] does. [known] are the ids of the
     * tags the page lists, which the create cannot have made.
     */
    suspend fun create(workspace: WorkspaceId, name: String, known: Set<TagId>) =
        api.call { TagsServer(it, workspace).create(name, known) }

    suspend fun rename(workspace: WorkspaceId, id: TagId, name: String) =
        api.call { TagsServer(it, workspace).rename(id, name) }

    suspend fun delete(workspace: WorkspaceId, id: TagId) = api.call { TagsServer(it, workspace).delete(id) }
}
