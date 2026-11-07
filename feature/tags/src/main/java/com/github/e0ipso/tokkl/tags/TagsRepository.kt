package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.catalog.CatalogRepository
import com.github.e0ipso.tokkl.catalog.SignedInApi

/** The workspace's tags on the server, for the tags page. */
interface TagsRepository : CatalogRepository<TagsData> {

    /**
     * Creates the tag named [name] once, as [TagsServer.create] does. [known] are the ids of the
     * tags the page lists, which the create cannot have made.
     */
    suspend fun create(workspace: Long, name: String, known: Set<Long>): ApiResult<Tag>

    suspend fun rename(workspace: Long, id: Long, name: String): ApiResult<Tag>

    suspend fun delete(workspace: Long, id: Long): ApiResult<Unit>
}

/** [TagsRepository] through the signed-in account's [client] and [TagsServer]. */
class ServerTagsRepository(client: ApiClientProvider) : TagsRepository {

    private val api = SignedInApi(client)

    override suspend fun workspace(stored: Long?): ApiResult<Long> = api.workspace(stored)

    /** The workspace's tags, sorted as Tokkl sorts them. */
    override suspend fun load(workspace: Long): ApiResult<TagsData> = api.call { client ->
        when (val answer = TagsServer(client, workspace).list()) {
            is ApiResult.Success -> ApiResult.Success(
                TagsData(answer.value.filter { it.workspaceId == workspace }.sortedForList()),
            )

            is ApiResult.Failure -> answer
        }
    }

    override suspend fun create(workspace: Long, name: String, known: Set<Long>) =
        api.call { TagsServer(it, workspace).create(name, known) }

    override suspend fun rename(workspace: Long, id: Long, name: String) =
        api.call { TagsServer(it, workspace).rename(id, name) }

    override suspend fun delete(workspace: Long, id: Long) = api.call { TagsServer(it, workspace).delete(id) }
}
