package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A tag as `GET /api/v9/workspaces/{wid}/tags` sends it. */
@Serializable
data class Tag(
    val id: Long,
    @SerialName("workspace_id") val workspaceId: Long,
    val name: String = "",
)

/**
 * The API v9 tag calls of one [workspace] on the configured server. Every call blocks, so make
 * them off the main thread.
 */
class TagsServer(private val api: TogglApiClient, private val workspace: Long) {

    fun list(): ApiResult<List<Tag>> = api.get(url(), ListSerializer(Tag.serializer()))

    fun create(name: String): ApiResult<Tag> = api.send("POST", url(), buildJsonObject { put("name", name) }, Tag.serializer())

    /** Renames the tag. The server renames it on every entry that has it. */
    fun rename(id: Long, name: String): ApiResult<Tag> =
        api.send("PUT", url(id.toString()), buildJsonObject { put("name", name) }, Tag.serializer())

    /** Deletes the tag, which comes off every entry that has it. Tokkl answers with an empty body. */
    fun delete(id: Long): ApiResult<Unit> = api.sendNoContent("DELETE", url(id.toString()))

    private fun url(vararg path: String) = api.server.api("workspaces", workspace.toString(), "tags", *path)
}
