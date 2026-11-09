package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.api.ProjectBody
import com.github.e0ipso.tokkl.api.WorkspaceProject
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable

/**
 * A project with the fields the projects page shows and edits, more than the cache's
 * [com.github.e0ipso.tokkl.model.Project] holds. [active] is false for an archived project.
 * [rate] and [currency] are null when the project takes the workspace's, and [estimatedHours]
 * when it has no estimate. Serializable for the form's saved state ([SavedProjectForm]).
 */
@Serializable
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

/** The project as the projects page shows it. */
internal fun WorkspaceProject.toDetails() = ProjectDetails(
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
internal fun ProjectFields.body() = ProjectBody(
    name = name,
    color = color,
    clientId = clientId?.value ?: 0L,
    billable = billable,
    rate = rate,
    currency = currency,
    estimatedHours = estimatedHours,
)
