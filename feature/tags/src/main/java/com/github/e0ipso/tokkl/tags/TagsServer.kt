package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.catalog.createOnce
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A tag as `GET /api/v9/workspaces/{wid}/tags` sends it. */
@Serializable
data class Tag(val id: Long, @SerialName("workspace_id") val workspaceId: Long, val name: String = "")

/**
 * The API v9 tag calls of one [workspace] on the configured server. Each call suspends while
 * its request runs.
 */
class TagsServer(private val api: TokklApi, private val workspace: Long) {

    suspend fun list(): ApiResult<List<Tag>> = api.tags.list(workspace, ListSerializer(Tag.serializer()))

    /**
     * Creates the tag once, as [createOnce] does. When the answer was lost, the tag named [name]
     * whose id is not in [known] counts as the one it made.
     *
     * @param known the ids of the tags the page lists, which the create cannot have made.
     */
    suspend fun create(name: String, known: Set<Long>): ApiResult<Tag> = createOnce(
        create = { api.tags.create(workspace, name, Tag.serializer()) },
        list = ::list,
        made = { it.id !in known && it.workspaceId == workspace && it.name == name },
    )

    /** Renames the tag. The server renames it on every entry that has it. */
    suspend fun rename(id: Long, name: String): ApiResult<Tag> = api.tags.rename(workspace, id, name, Tag.serializer())

    /** Deletes the tag, which comes off every entry that has it. Tokkl answers with an empty body. */
    suspend fun delete(id: Long): ApiResult<Unit> = api.tags.delete(workspace, id)
}
