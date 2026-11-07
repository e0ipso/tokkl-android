package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.catalog.createOnce
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A client as `GET /api/v9/workspaces/{wid}/clients` sends it. Toggl names its workspace `wid`. */
@Serializable
data class Client(
    val id: Long,
    @SerialName("wid") val workspaceId: Long,
    val name: String = "",
    val archived: Boolean = false,
    val notes: String? = null,
)

/** A project's client, for the number of projects each client has. */
@Serializable
class ClientProject(val id: Long, @SerialName("client_id") val clientId: Long? = null)

/**
 * The API v9 client calls of one [workspace] on the configured server. Every call blocks, so make
 * them off the main thread.
 */
class ClientsServer(private val api: TogglApiClient, private val workspace: Long) {

    /** Every client of the workspace, archived ones included. */
    fun list(): ApiResult<List<Client>> =
        api.get(url().newBuilder().addQueryParameter("status", "both").build(), ListSerializer(Client.serializer()))

    /** Every project of the workspace, archived ones included, for the number each client has. */
    fun projects(): ApiResult<List<ClientProject>> {
        val url = api.server.api("workspaces", workspace.toString(), "projects").newBuilder()
            .addQueryParameter("active", "both")
            .build()
        return api.get(url, ListSerializer(ClientProject.serializer()))
    }

    /**
     * Creates the client once, as [createOnce] does. When the answer was lost, the client named
     * [name] whose id is not in [known] counts as the one it made. Blank [notes] go as null, as
     * the web app sends them.
     *
     * @param known the ids of the clients the page lists, which the create cannot have made.
     */
    fun create(name: String, notes: String?, known: Set<Long>): ApiResult<Client> = createOnce(
        create = { api.send("POST", url(), body(name, notes), Client.serializer(), resend = false) },
        list = ::list,
        made = { it.id !in known && it.workspaceId == workspace && it.name == name },
    )

    fun update(id: Long, name: String, notes: String?): ApiResult<Client> =
        api.send("PUT", url(id.toString()), body(name, notes), Client.serializer())

    /**
     * Archives the client and, as in Toggl, its projects. Tokkl answers with the ids of the
     * projects it archived, which the page does not need. OkHttp sends no POST without a body, so
     * the body is an empty object.
     */
    fun archive(id: Long): ApiResult<Unit> = api.sendNoContent("POST", url(id.toString(), "archive"), buildJsonObject {})

    /** Restores the client and every one of its projects, as the web app asks. */
    fun restore(id: Long): ApiResult<Client> =
        api.send("POST", url(id.toString(), "restore"), buildJsonObject { put("restore_all_projects", true) }, Client.serializer())

    private fun url(vararg path: String) = api.server.api("workspaces", workspace.toString(), "clients", *path)

    private fun body(name: String, notes: String?) = buildJsonObject {
        put("name", name)
        put("notes", notes)
    }
}
