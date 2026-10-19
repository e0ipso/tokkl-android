package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.clients.Client
import com.github.e0ipso.tokkl.clients.ClientsData
import com.github.e0ipso.tokkl.clients.ClientsOverlay
import com.github.e0ipso.tokkl.projects.ProjectForm
import com.github.e0ipso.tokkl.projects.ProjectsData
import com.github.e0ipso.tokkl.projects.WorkspaceDefaults
import com.github.e0ipso.tokkl.tags.Tag
import com.github.e0ipso.tokkl.tags.TagsData
import com.github.e0ipso.tokkl.tags.TagsOverlay
import com.github.e0ipso.tokkl.projects.Project as CatalogProject

/**
 * Fixed data for the Projects, Clients and Tags pages, as the screenshot tests and CatalogFitTest
 * show them: a client with notes long enough to cut, an archived client and project, a project
 * with a rate and an estimate, and names long enough to wrap.
 */
object CatalogSamples {

    val CACHED_CLIENTS = listOf(CachedClient(1, 7, "Acme", archived = false), CachedClient(2, 7, "Gone", archived = true))

    val PROJECTS = listOf(
        CatalogProject(11, 7, "Tokkl for Android", "#0b83d9", clientId = 1, clientName = "Acme", billable = true, rate = 65.0, currency = "EUR", estimatedHours = 120.0),
        CatalogProject(12, 7, "Graffiti wall", "#d94182"),
        CatalogProject(13, 7, "Surf shop site with a long name that wraps", "#2da608", clientId = 1, clientName = "Acme"),
        CatalogProject(14, 7, "Old site", "#525266", clientId = 2, clientName = "Gone", active = false),
    )

    val PROJECTS_LOADED = CatalogState<ProjectsData, ProjectForm>(data = ProjectsData(PROJECTS, WorkspaceDefaults("EUR", 50.0)))

    val CLIENTS = listOf(
        Client(1, 7, "Acme", notes = "Pays late, so send the invoice on the first of the month and chase it on the tenth."),
        Client(3, 7, "Surf school"),
        Client(2, 7, "Gone", archived = true),
    )

    /** Acme has three projects and Gone one. */
    val CLIENTS_LOADED = CatalogState<ClientsData, ClientsOverlay>(data = ClientsData(CLIENTS, mapOf(1L to 3, 2L to 1)))

    val TAGS = listOf(Tag(21, 7, "deep work"), Tag(22, 7, "focus"), Tag(23, 7, "meeting with a client who talks a lot"))

    val TAGS_LOADED = CatalogState<TagsData, TagsOverlay>(data = TagsData(TAGS))
}
