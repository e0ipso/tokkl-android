package com.github.e0ipso.tokkl.picker

import androidx.compose.ui.graphics.toArgb
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerOptionsTest {

    private val source = PickerSource(
        projects = listOf(
            project(INTERNAL, "Internal", client = null),
            project(OLD_SITE, "Old site", client = ACME, active = false),
            project(RETIRED, "Retired", client = ACME, active = false),
            project(WEBSITE, "Website", client = ACME),
            project(ELSEWHERE, "Other workspace", client = null, workspace = 2),
        ),
        clients = listOf(
            Client(ClientId(ACME), WorkspaceId(WORKSPACE), "Acme", archived = false),
            Client(ClientId(BETA), WorkspaceId(WORKSPACE), "Beta", archived = true),
        ),
        tasks = listOf(
            Task(TaskId(DESIGN), WorkspaceId(WORKSPACE), ProjectId(WEBSITE), "Design", active = true),
            Task(TaskId(DROPPED), WorkspaceId(WORKSPACE), ProjectId(WEBSITE), "Dropped", active = false),
            Task(TaskId(OLD_TASK), WorkspaceId(WORKSPACE), ProjectId(OLD_SITE), "Old task", active = false),
        ),
        tags = listOf(
            Tag(TagId(1), WorkspaceId(WORKSPACE), "focus"),
            Tag(TagId(2), WorkspaceId(WORKSPACE), "legacy"),
            Tag(TagId(3), WorkspaceId(2), "elsewhere"),
        ),
    )

    @Test
    fun archivedItemsTheEntryUsesStaySelectedWithTheirLabelAndNoOtherArchivedItemShows() {
        val original = Picked(ProjectId(OLD_SITE), TaskId(OLD_TASK), listOf("legacy"))

        val options = pickerOptions(WorkspaceId(WORKSPACE), original, original, "", source)

        assertEquals(listOf(null, "Acme"), options.groups.map { it.clientName })
        assertEquals(listOf(INTERNAL), options.groups[0].projects.map { it.project.id.value })
        val acme = options.groups[1].projects
        assertEquals("Retired is archived and unused", listOf(OLD_SITE, WEBSITE), acme.map { it.project.id.value })
        val oldSite = acme[0]
        assertTrue(oldSite.selected && oldSite.archived)
        assertEquals(
            listOf(Triple(OLD_TASK, true, true)),
            oldSite.tasks.map { Triple(it.task.id.value, it.selected, it.archived) },
        )
        assertEquals("Dropped is done and unused", listOf(DESIGN), acme[1].tasks.map { it.task.id.value })
        assertFalse(options.noProjectSelected)
        assertEquals(listOf(TagOption("focus", false, true), TagOption("legacy", true, true)), options.tags)
    }

    @Test
    fun anArchivedProjectStaysOnOfferAfterTheUserPicksAnotherOne() {
        val original = Picked(ProjectId(OLD_SITE), null)

        val options = pickerOptions(
            WorkspaceId(WORKSPACE),
            original,
            original.withProject(ProjectId(WEBSITE)),
            "",
            source,
        )

        val acme = options.groups.single { it.clientName == "Acme" }.projects
        assertEquals(listOf(OLD_SITE to false, WEBSITE to true), acme.map { it.project.id.value to it.selected })
    }

    @Test
    fun searchMatchesProjectClientAndTaskNamesIgnoringCase() {
        val byTask = pickerOptions(WorkspaceId(WORKSPACE), Picked(), Picked(), "  DES ", source)
        assertEquals(listOf("Acme"), byTask.groups.map { it.clientName })
        assertEquals(
            listOf(WEBSITE to listOf(DESIGN)),
            byTask.groups.single().projects.map { it.project.id.value to it.tasks.map { t -> t.task.id.value } },
        )
        assertFalse("No project shows only without a search", byTask.noProject)

        val byClient = pickerOptions(WorkspaceId(WORKSPACE), Picked(), Picked(), "acme", source)
        assertEquals(listOf(WEBSITE), byClient.groups.single().projects.map { it.project.id.value })
        assertEquals(
            "a matching project lists all its tasks",
            listOf(DESIGN),
            byClient.groups.single().projects.single().tasks.map { it.task.id.value },
        )

        val none = pickerOptions(WorkspaceId(WORKSPACE), Picked(), Picked(), "zzz", source)
        assertTrue(none.groups.isEmpty())
    }

    @Test
    fun aTagTypedByNameIsOfferedAsNewAndKeptByName() {
        val typed = pickerOptions(WorkspaceId(WORKSPACE), Picked(), Picked(), "client call", source)
        assertEquals("client call", typed.newTag)
        assertTrue(typed.tags.isEmpty())

        val picked = Picked().withTagToggled("client call")
        val after = pickerOptions(WorkspaceId(WORKSPACE), Picked(), picked, "", source)
        assertEquals(TagOption("client call", selected = true, cached = false), after.tags.last())
        assertNull(pickerOptions(WorkspaceId(WORKSPACE), Picked(), picked, "Client Call", source).newTag)
    }

    @Test
    fun anExistingTagIsNotOfferedAgainInAnotherCase() {
        val options = pickerOptions(WorkspaceId(WORKSPACE), Picked(), Picked(), "FOCUS", source)

        assertNull(options.newTag)
        assertEquals(listOf("focus"), options.tags.map { it.name })
        assertEquals(listOf<String>(), Picked(tags = listOf("focus")).withTagToggled("Focus").tags)
    }

    @Test
    fun pickingATaskPicksItsProjectAndPickingAProjectDropsTheTask() {
        val task = source.tasks.first { it.id.value == DESIGN }

        val withTask = Picked().withTask(task)
        assertEquals(Picked(ProjectId(WEBSITE), TaskId(DESIGN)), withTask)
        assertEquals(Picked(ProjectId(INTERNAL), null), withTask.withProject(ProjectId(INTERNAL)))
        assertEquals(Picked(null, null), withTask.withProject(null))
    }

    @Test
    fun projectColoursParseAsHex() {
        assertEquals(0xFF0B83D9.toInt(), projectColor("#0b83d9")!!.toArgb())
        assertNull(projectColor(null))
        assertNull(projectColor("blue"))
    }

    private fun project(id: Long, name: String, client: Long?, active: Boolean = true, workspace: Long = WORKSPACE) =
        Project(
            ProjectId(id),
            WorkspaceId(workspace),
            client?.let(::ClientId),
            name,
            "#0b83d9",
            active,
            billable = false,
        )

    private companion object {
        const val WORKSPACE = 1L
        const val ACME = 10L
        const val BETA = 11L
        const val INTERNAL = 20L
        const val OLD_SITE = 21L
        const val RETIRED = 22L
        const val WEBSITE = 23L
        const val ELSEWHERE = 24L
        const val DESIGN = 30L
        const val DROPPED = 31L
        const val OLD_TASK = 32L
    }
}
