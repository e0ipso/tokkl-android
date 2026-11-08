package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.WorkspaceTag
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId

/**
 * The API v9 tag calls of one [workspace] on the configured server, read as the model's [Tag].
 * Each call suspends while its request runs.
 */
class TagsServer(private val api: TokklApi, private val workspace: WorkspaceId) {

    suspend fun list(): ApiResult<List<Tag>> = api.tags.list(workspace).map { tags -> tags.map { it.toTag() } }

    /**
     * Creates the tag once, as [createOnce] does. When the answer was lost, the tag named [name]
     * whose id is not in [known] counts as the one it made.
     *
     * @param known the ids of the tags the page lists, which the create cannot have made.
     */
    suspend fun create(name: String, known: Set<TagId>): ApiResult<Tag> = createOnce(
        create = { api.tags.create(workspace, name).map { it.toTag() } },
        list = ::list,
        made = { it.id !in known && it.workspaceId == workspace && it.name == name },
    )

    /** Renames the tag. The server renames it on every entry that has it. */
    suspend fun rename(id: TagId, name: String): ApiResult<Tag> =
        api.tags.rename(workspace, id, name).map { it.toTag() }

    /** Deletes the tag, which comes off every entry that has it. Tokkl answers with an empty body. */
    suspend fun delete(id: TagId): ApiResult<Unit> = api.tags.delete(workspace, id)
}

private fun WorkspaceTag.toTag() = Tag(id, workspaceId, name)
