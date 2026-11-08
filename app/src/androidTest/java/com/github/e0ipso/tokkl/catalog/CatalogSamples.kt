package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.clients.ClientsData
import com.github.e0ipso.tokkl.clients.ClientsOverlay
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.projects.ProjectDetails
import com.github.e0ipso.tokkl.projects.ProjectForm
import com.github.e0ipso.tokkl.projects.ProjectsData
import com.github.e0ipso.tokkl.projects.TaskOverlay
import com.github.e0ipso.tokkl.projects.TasksData
import com.github.e0ipso.tokkl.projects.WorkspaceDefaults
import com.github.e0ipso.tokkl.tags.TagsData
import com.github.e0ipso.tokkl.tags.TagsOverlay

/**
 * Fixed data for the Projects, Clients and Tags pages, as the screenshot tests and CatalogFitTest
 * show them: a client with notes long enough to cut, an archived client and project, a project
 * with a rate and an estimate, and names long enough to wrap.
 */
object CatalogSamples {

    val CACHED_CLIENTS = listOf(
        Client(ClientId(1), WorkspaceId(7), "Acme", archived = false),
        Client(ClientId(2), WorkspaceId(7), "Gone", archived = true),
    )

    val PROJECTS = listOf(
        ProjectDetails(
            ProjectId(11),
            WorkspaceId(7),
            "Tokkl for Android",
            "#0b83d9",
            clientId = ClientId(1),
            clientName = "Acme",
            billable = true,
            rate = 65.0,
            currency = "EUR",
            estimatedHours = 120.0,
        ),
        ProjectDetails(ProjectId(12), WorkspaceId(7), "Graffiti wall", "#d94182"),
        ProjectDetails(
            ProjectId(13),
            WorkspaceId(7),
            "Surf shop site with a long name that wraps",
            "#2da608",
            clientId = ClientId(1),
            clientName = "Acme",
        ),
        ProjectDetails(
            ProjectId(14),
            WorkspaceId(7),
            "Old site",
            "#525266",
            clientId = ClientId(2),
            clientName = "Gone",
            active = false,
        ),
    )

    val PROJECTS_LOADED = CatalogState<ProjectsData, ProjectForm>(
        data = ProjectsData(PROJECTS, WorkspaceDefaults("EUR", 50.0)),
    )

    /** Tasks of the first project: an open one, one with a name long enough to wrap, and a done one. */
    val TASKS = listOf(
        task(21, "Design"),
        task(22, "Write the store listing and a caption for every screenshot"),
        task(23, "Release 1.0", active = false),
    )

    val TASKS_LOADED = CatalogState<TasksData, TaskOverlay>(data = TasksData(TASKS))

    val CLIENTS = listOf(
        Client(
            ClientId(1),
            WorkspaceId(7),
            "Acme",
            notes = "Pays late, so send the invoice on the first of the month and chase it on the tenth.",
        ),
        Client(ClientId(3), WorkspaceId(7), "Surf school"),
        Client(ClientId(2), WorkspaceId(7), "Gone", archived = true),
    )

    /** Acme has three projects and Gone one. */
    val CLIENTS_LOADED = CatalogState<ClientsData, ClientsOverlay>(
        data = ClientsData(CLIENTS, mapOf(ClientId(1) to 3, ClientId(2) to 1)),
    )

    val TAGS = listOf(
        Tag(TagId(21), WorkspaceId(7), "deep work"),
        Tag(TagId(22), WorkspaceId(7), "focus"),
        Tag(TagId(23), WorkspaceId(7), "meeting with a client who talks a lot"),
    )

    val TAGS_LOADED = CatalogState<TagsData, TagsOverlay>(data = TagsData(TAGS))

    private fun task(id: Long, name: String, active: Boolean = true) =
        Task(TaskId(id), WorkspaceId(7), ProjectId(11), name, active)
}
