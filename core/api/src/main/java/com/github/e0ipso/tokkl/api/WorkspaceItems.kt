package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The answers of the workspace routes that the projects, clients and tags pages send, with the
// fields those pages read. Each page's repository maps them to its own types.

/** The default workspace and Tokkl's role from `GET /api/v9/me`, for the pages that read nothing else of it. */
@Serializable
class MeWorkspace(
    @SerialName("default_workspace_id") val defaultWorkspaceId: WorkspaceId? = null,
    val role: String? = null,
)

/**
 * `GET /api/v9/workspaces/{id}`: the rate and currency a project without its own takes, and the
 * rounding a report asks for. [rounding] is -1 down, 0 to the nearest and 1 up, each entry to
 * [roundingMinutes], with 0 minutes for no rounding.
 */
@Serializable
class WorkspaceSettings(
    @SerialName("default_currency") val defaultCurrency: String? = null,
    @SerialName("default_hourly_rate") val defaultHourlyRate: Double? = null,
    val rounding: Int = 0,
    @SerialName("rounding_minutes") val roundingMinutes: Int = 0,
)

/** A client of `GET /api/v9/workspaces/{wid}/clients`. Toggl names its workspace `wid`. */
@Serializable
class WorkspaceClient(
    val id: ClientId,
    @SerialName("wid") val workspaceId: WorkspaceId,
    val name: String = "",
    val archived: Boolean = false,
    val notes: String? = null,
)

/** A project's client, from the projects list with `active=both`, for the number of projects each client has. */
@Serializable
class ProjectClient(val id: ProjectId, @SerialName("client_id") val clientId: ClientId? = null)

/**
 * A project of `GET /api/v9/workspaces/{wid}/projects`, with the fields the projects page shows
 * and edits. [active] is false for an archived project. [rate] and [currency] are null when the
 * project takes the workspace's, and [estimatedHours] when it has no estimate.
 */
@Serializable
class WorkspaceProject(
    val id: ProjectId,
    @SerialName("workspace_id") val workspaceId: WorkspaceId,
    val name: String = "",
    val color: String? = null,
    @SerialName("client_id") val clientId: ClientId? = null,
    @SerialName("client_name") val clientName: String? = null,
    val active: Boolean = true,
    val billable: Boolean = false,
    val rate: Double? = null,
    val currency: String? = null,
    @SerialName("estimated_hours") val estimatedHours: Double? = null,
)

/** A task of `GET /api/v9/workspaces/{wid}/projects/{pid}/tasks`. A done task is not [active]. */
@Serializable
class ProjectTask(
    val id: TaskId,
    @SerialName("project_id") val projectId: ProjectId,
    val name: String = "",
    val active: Boolean = true,
)

/** A tag of `GET /api/v9/workspaces/{wid}/tags`. Neither Tokkl nor Toggl archives tags. */
@Serializable
class WorkspaceTag(val id: TagId, @SerialName("workspace_id") val workspaceId: WorkspaceId, val name: String = "")

/** How many entries a purge of Tokkl's trash removed for good. */
@Serializable
class PurgedEntries(val deleted: Int = 0)
