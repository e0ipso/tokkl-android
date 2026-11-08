package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A tag as `GET /api/v9/workspaces/{wid}/tags` sends it. The page reads it as a [Tag]. */
@Serializable
private class ServerTag(val id: Long, @SerialName("workspace_id") val workspaceId: Long, val name: String = "") {
    fun toTag() = Tag(TagId(id), WorkspaceId(workspaceId), name)
}

/**
 * The API v9 tag calls of one [workspace] on the configured server. Each call suspends while
 * its request runs.
 */
class TagsServer(private val api: TokklApi, private val workspace: WorkspaceId) {

    suspend fun list(): ApiResult<List<Tag>> =
        api.tags.list(workspace.value, ListSerializer(ServerTag.serializer())).map { tags -> tags.map { it.toTag() } }

    /**
     * Creates the tag once, as [createOnce] does. When the answer was lost, the tag named [name]
     * whose id is not in [known] counts as the one it made.
     *
     * @param known the ids of the tags the page lists, which the create cannot have made.
     */
    suspend fun create(name: String, known: Set<TagId>): ApiResult<Tag> = createOnce(
        create = { api.tags.create(workspace.value, name, ServerTag.serializer()).map { it.toTag() } },
        list = ::list,
        made = { it.id !in known && it.workspaceId == workspace && it.name == name },
    )

    /** Renames the tag. The server renames it on every entry that has it. */
    suspend fun rename(id: TagId, name: String): ApiResult<Tag> =
        api.tags.rename(workspace.value, id.value, name, ServerTag.serializer()).map { it.toTag() }

    /** Deletes the tag, which comes off every entry that has it. Tokkl answers with an empty body. */
    suspend fun delete(id: TagId): ApiResult<Unit> = api.tags.delete(workspace.value, id.value)
}
