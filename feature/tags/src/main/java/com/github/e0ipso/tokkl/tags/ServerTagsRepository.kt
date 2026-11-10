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

/** The workspace's tags on the signed-in account's server, from the API v9 tag calls. */
class ServerTagsRepository(private val client: ApiClientProvider) : CatalogRepository<TagsData> {

    override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = client.managedWorkspace(stored)

    /** The workspace's tags, sorted as Tokkl sorts them. */
    override suspend fun load(workspace: WorkspaceId): ApiResult<TagsData> = client.signedIn { api ->
        api.tagsOf(workspace).map { tags -> TagsData(tags.filter { it.workspaceId == workspace }.sortedForList()) }
    }

    /**
     * Creates the tag once, as [createOnce] does. [known] are the ids of the tags the page lists,
     * which the create cannot have made. When the answer was lost, the tag named [name] whose id
     * is not in [known] counts as the one it made.
     */
    suspend fun create(workspace: WorkspaceId, name: String, known: Set<TagId>): ApiResult<Tag> =
        client.signedIn { api ->
            createOnce(
                create = { api.tags.create(workspace, name).map { it.toTag() } },
                list = { api.tagsOf(workspace) },
                made = { it.id !in known && it.workspaceId == workspace && it.name == name },
            )
        }

    /** Renames the tag. The server renames it on every entry that has it. */
    suspend fun rename(workspace: WorkspaceId, id: TagId, name: String): ApiResult<Tag> =
        client.signedIn { api -> api.tags.rename(workspace, id, name).map { it.toTag() } }

    /** Deletes the tag, which comes off every entry that has it. Tokkl answers with an empty body. */
    suspend fun delete(workspace: WorkspaceId, id: TagId): ApiResult<Unit> =
        client.signedIn { it.tags.delete(workspace, id) }
}

private suspend fun TokklApi.tagsOf(workspace: WorkspaceId): ApiResult<List<Tag>> =
    tags.list(workspace).map { tags -> tags.map { it.toTag() } }

private fun WorkspaceTag.toTag() = Tag(id, workspaceId, name)
