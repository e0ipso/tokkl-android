package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A project as `GET /api/v9/workspaces/{wid}/projects` sends it, with the fields the projects page
 * shows and edits. [active] is false for an archived project. [rate] and [currency] are null when
 * the project takes the workspace's, and [estimatedHours] when it has no estimate.
 */
@Serializable
data class Project(
    val id: Long,
    @SerialName("workspace_id") val workspaceId: Long,
    val name: String = "",
    val color: String? = null,
    @SerialName("client_id") val clientId: Long? = null,
    @SerialName("client_name") val clientName: String? = null,
    val active: Boolean = true,
    val billable: Boolean = false,
    val rate: Double? = null,
    val currency: String? = null,
    @SerialName("estimated_hours") val estimatedHours: Double? = null,
)

/** The workspace's rate and currency, which a project without its own takes. */
@Serializable
data class WorkspaceDefaults(
    @SerialName("default_currency") val currency: String? = null,
    @SerialName("default_hourly_rate") val hourlyRate: Double? = null,
)

/**
 * What the project form sends, as the web app's ProjectBody: every field, so a save writes what
 * the form shows. A null [clientId] clears the client.
 */
data class ProjectFields(
    val name: String,
    val color: String,
    val clientId: Long?,
    val billable: Boolean,
    val rate: Double?,
    val currency: String?,
    val estimatedHours: Double?,
)

/**
 * The API v9 project calls of one [workspace] on the configured server. Every call blocks, so make
 * them off the main thread.
 */
class ProjectsServer(private val api: TogglApiClient, private val workspace: Long) {

    /** Every project of the workspace, archived ones included, in the server's order. */
    fun list(): ApiResult<List<Project>> = api.get(url(), ListSerializer(Project.serializer()))

    fun defaults(): ApiResult<WorkspaceDefaults> =
        api.get(api.server.api("workspaces", workspace.toString()), WorkspaceDefaults.serializer())

    fun create(fields: ProjectFields): ApiResult<Project> = api.send("POST", url(), fields.toJson(), Project.serializer())

    fun update(id: Long, fields: ProjectFields): ApiResult<Project> =
        api.send("PUT", url(id), fields.toJson(), Project.serializer())

    /** Archives the project when [active] is false and restores it when true. */
    fun setActive(id: Long, active: Boolean): ApiResult<Project> =
        api.send("PUT", url(id), buildJsonObject { put("active", active) }, Project.serializer())

    private fun url(id: Long? = null) =
        if (id == null) {
            api.server.api("workspaces", workspace.toString(), "projects")
        } else {
            api.server.api("workspaces", workspace.toString(), "projects", id.toString())
        }
}

/** No client goes as 0, as the web app sends it. Tokkl reads 0 and null alike as no client. */
private fun ProjectFields.toJson(): JsonObject = buildJsonObject {
    put("name", name)
    put("color", color)
    put("client_id", clientId ?: 0L)
    put("billable", billable)
    put("rate", rate)
    put("currency", currency)
    put("estimated_hours", estimatedHours)
}
