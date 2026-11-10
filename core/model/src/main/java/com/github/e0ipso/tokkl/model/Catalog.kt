package com.github.e0ipso.tokkl.model

/** A project. An archived one is not [active]. */
data class Project(
    val id: ProjectId,
    val workspaceId: WorkspaceId,
    val clientId: ClientId?,
    val name: String,
    val color: String?,
    val active: Boolean,
    val billable: Boolean,
)

/**
 * A client. [notes] are null where the phone has not read them: the cache keeps none, and only
 * the clients page reads them from the server.
 */
data class Client(
    val id: ClientId,
    val workspaceId: WorkspaceId,
    val name: String,
    val archived: Boolean = false,
    val notes: String? = null,
)

/** A tag. Neither Tokkl nor Toggl archives tags. */
data class Tag(val id: TagId, val workspaceId: WorkspaceId, val name: String)

/** A task of a project. A done one is not [active]. */
data class Task(
    val id: TaskId,
    val workspaceId: WorkspaceId,
    val projectId: ProjectId?,
    val name: String,
    val active: Boolean,
)
