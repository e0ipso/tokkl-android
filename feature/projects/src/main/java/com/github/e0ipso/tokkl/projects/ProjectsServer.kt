package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ProjectBody
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.WorkspaceProject
import com.github.e0ipso.tokkl.api.map
import com.github.e0ipso.tokkl.catalog.createOnce
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.WorkspaceId

/**
 * A project with the fields the projects page shows and edits, more than the cache's
 * [com.github.e0ipso.tokkl.model.Project] holds. [active] is false for an archived project.
 * [rate] and [currency] are null when the project takes the workspace's, and [estimatedHours]
 * when it has no estimate.
 */
data class ProjectDetails(
    val id: ProjectId,
    val workspaceId: WorkspaceId,
    val name: String = "",
    val color: String? = null,
    val clientId: ClientId? = null,
    val clientName: String? = null,
    val active: Boolean = true,
    val billable: Boolean = false,
    val rate: Double? = null,
    val currency: String? = null,
    val estimatedHours: Double? = null,
)

/** The workspace's rate and currency, which a project without its own takes. */
data class WorkspaceDefaults(val currency: String? = null, val hourlyRate: Double? = null)

/**
 * What the project form sends, as the web app's ProjectBody: every field, so a save writes what
 * the form shows. A null [clientId] clears the client.
 */
data class ProjectFields(
    val name: String,
    val color: String,
    val clientId: ClientId?,
    val billable: Boolean,
    val rate: Double?,
    val currency: String?,
    val estimatedHours: Double?,
)

/**
 * The API v9 project calls of one [workspace] on the configured server. Each call suspends while
 * its request runs.
 */
class ProjectsServer(private val api: TokklApi, private val workspace: WorkspaceId) {

    /** Every project of the workspace, archived ones included, in the server's order. */
    suspend fun list(): ApiResult<List<ProjectDetails>> =
        api.projects.list(workspace).map { projects -> projects.map { it.toDetails() } }

    suspend fun defaults(): ApiResult<WorkspaceDefaults> =
        api.account.workspace(workspace).map { WorkspaceDefaults(it.defaultCurrency, it.defaultHourlyRate) }

    /**
     * Creates the project once, as [createOnce] does. When the answer was lost, the project with
     * the name and client of [fields] whose id is not in [known] counts as the one it made.
     *
     * @param known the ids of the projects the page lists, which the create cannot have made.
     */
    suspend fun create(fields: ProjectFields, known: Set<ProjectId>): ApiResult<ProjectDetails> = createOnce(
        create = { api.projects.create(workspace, fields.body()).map { it.toDetails() } },
        list = ::list,
        made = {
            it.id !in known && it.workspaceId == workspace && it.name == fields.name &&
                it.clientId == fields.clientId
        },
    )

    suspend fun update(id: ProjectId, fields: ProjectFields): ApiResult<ProjectDetails> =
        api.projects.update(workspace, id, fields.body()).map { it.toDetails() }

    /** Archives the project when [active] is false and restores it when true. */
    suspend fun setActive(id: ProjectId, active: Boolean): ApiResult<ProjectDetails> =
        api.projects.setActive(workspace, id, active).map { it.toDetails() }
}

private fun WorkspaceProject.toDetails() = ProjectDetails(
    id = id,
    workspaceId = workspaceId,
    name = name,
    color = color,
    clientId = clientId,
    clientName = clientName,
    active = active,
    billable = billable,
    rate = rate,
    currency = currency,
    estimatedHours = estimatedHours,
)

/** No client goes as 0, as the web app sends it. Tokkl reads 0 and null alike as no client. */
private fun ProjectFields.body() = ProjectBody(
    name = name,
    color = color,
    clientId = clientId?.value ?: 0L,
    billable = billable,
    rate = rate,
    currency = currency,
    estimatedHours = estimatedHours,
)
