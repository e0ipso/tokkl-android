package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.catalog.createOnce
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

    /**
     * Creates the tag once, as [createOnce] does. When the answer was lost, the tag named [name]
     * whose id is not in [known] counts as the one it made.
     *
     * @param known the ids of the tags the page lists, which the create cannot have made.
     */
    fun create(name: String, known: Set<Long>): ApiResult<Tag> = createOnce(
        create = { api.send("POST", url(), buildJsonObject { put("name", name) }, Tag.serializer(), resend = false) },
        list = ::list,
        made = { it.id !in known && it.workspaceId == workspace && it.name == name },
    )

    /** Renames the tag. The server renames it on every entry that has it. */
    fun rename(id: Long, name: String): ApiResult<Tag> =
        api.send("PUT", url(id.toString()), buildJsonObject { put("name", name) }, Tag.serializer())

    /** Deletes the tag, which comes off every entry that has it. Tokkl answers with an empty body. */
    fun delete(id: Long): ApiResult<Unit> = api.sendNoContent("DELETE", url(id.toString()))

    private fun url(vararg path: String) = api.server.api("workspaces", workspace.toString(), "tags", *path)
}
