package com.github.e0ipso.tokkl.entryfields

import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The description suggestions, ranked as `suggestionsFrom` in
 * ../tokkl/packages/web/src/components/useEntryFields.ts ranks them, without entries that have
 * no description, and limited to one workspace.
 */
class DescriptionSuggestionsTest {

    @Test
    fun anEmptyQuerySuggestsTheNewestDistinctEntriesWithADescription() {
        val entries = listOf(
            entry(1, "Write the report", WEBSITE.id, taskId = TaskId(5), tags = listOf("call"), billable = true),
            entry(2, "Write the report", WEBSITE.id, taskId = TaskId(5), tags = listOf("other"), billable = false),
            entry(3, "", projectId = null),
            entry(4, "", WEBSITE.id),
            entry(5, "  ", WEBSITE.id),
            entry(6, "Write the report", projectId = null),
        )
        assertEquals(
            listOf(
                DescriptionSuggestion(
                    EntryFields("Write the report", WEBSITE.id, TaskId(5), listOf("call"), billable = true),
                    "Website",
                    "#0b83d9",
                ),
                DescriptionSuggestion(EntryFields("Write the report"), null, null),
            ),
            DescriptionSuggestions.of(entries, PROJECTS, CLIENTS).forQuery("  "),
        )
    }

    @Test
    fun theQueryMatchesTheDescriptionProjectOrClientInAnyCase() {
        val entries = listOf(
            entry(1, "Lunch", projectId = null),
            entry(2, "Design review", WEBSITE.id),
            entry(3, "Call", INTERNAL.id),
            entry(4, "Standup", INTERNAL.id),
        )
        assertEquals(
            listOf("Design review"),
            DescriptionSuggestions.of(entries, PROJECTS, CLIENTS).forQuery(" REVIEW ").map { it.fields.description },
        )
        assertEquals(
            listOf("Design review"),
            DescriptionSuggestions.of(entries, PROJECTS, CLIENTS).forQuery("acme").map { it.fields.description },
        )
        assertEquals(
            listOf("Call", "Standup"),
            DescriptionSuggestions.of(entries, PROJECTS, CLIENTS).forQuery("intern").map { it.fields.description },
        )
        // The description, project and client are joined with spaces, as the web app joins them.
        assertEquals(
            listOf("Design review"),
            DescriptionSuggestions.of(entries, PROJECTS, CLIENTS).forQuery("review website")
                .map { it.fields.description },
        )
        assertEquals(
            emptyList<String>(),
            DescriptionSuggestions.of(entries, PROJECTS, CLIENTS).forQuery("nothing").map { it.fields.description },
        )
    }

    @Test
    fun atMostEightShow() {
        val entries = (1L..12L).map { entry(it, "Entry $it", projectId = null) }
        assertEquals(
            (1..8).map { "Entry $it" },
            DescriptionSuggestions.of(entries, PROJECTS, CLIENTS).forQuery("entry").map { it.fields.description },
        )
    }

    @Test
    fun aWorkspaceLimitKeepsOnlyThatWorkspacesEntriesAndNoWorkspaceKeepsNone() {
        val entries = listOf(
            entry(1, "Here", projectId = null),
            entry(2, "Elsewhere", projectId = null, workspace = OTHER_WORKSPACE),
            entry(3, "Here too", WEBSITE.id),
        )
        val all = DescriptionSuggestions.of(entries, PROJECTS, CLIENTS)
        assertEquals(
            listOf("Here", "Here too"),
            all.inWorkspace(WorkspaceId(WORKSPACE)).forQuery("").map { it.fields.description },
        )
        assertEquals(
            listOf("Elsewhere"),
            all.inWorkspace(WorkspaceId(OTHER_WORKSPACE)).forQuery("").map { it.fields.description },
        )
        assertEquals(emptyList<DescriptionSuggestion>(), all.inWorkspace(null).forQuery(""))
    }

    private fun entry(
        id: Long,
        description: String,
        projectId: ProjectId?,
        taskId: TaskId? = null,
        tags: List<String> = emptyList(),
        billable: Boolean = false,
        workspace: Long = WORKSPACE,
    ) = TimeEntry(
        EntryId(id), WorkspaceId(workspace), projectId, taskId, description,
        EntryTiming.Stopped(
            Instant.EPOCH.minusSeconds(
                id,
            ),
            Instant.EPOCH,
        ),
        billable, tags, emptyList(),
    )

    private companion object {
        const val WORKSPACE = 10L
        const val OTHER_WORKSPACE = 11L
        val ACME = Client(ClientId(7), WorkspaceId(WORKSPACE), "Acme", archived = false)
        val WEBSITE = Project(
            ProjectId(1),
            WorkspaceId(WORKSPACE),
            clientId = ACME.id,
            name = "Website",
            color = "#0b83d9",
            active = true,
            billable = false,
        )
        val INTERNAL = Project(
            ProjectId(2),
            WorkspaceId(WORKSPACE),
            clientId = null,
            name = "Internal",
            color = null,
            active = true,
            billable = false,
        )
        val PROJECTS = listOf(WEBSITE, INTERNAL)
        val CLIENTS = listOf(ACME)
    }
}
