package com.github.e0ipso.tokkl.entryfields

import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The timer's suggestions, ranked as `suggestionsFrom` in
 * ../tokkl/packages/web/src/components/TimerBar.tsx ranks them.
 */
class DescriptionSuggestionsTest {

    @Test
    fun anEmptyQuerySuggestsTheNewestDistinctEntries() {
        val entries = listOf(
            entry(1, "Write the report", WEBSITE.id, taskId = TaskId(5), tags = listOf("call"), billable = true),
            entry(2, "Write the report", WEBSITE.id, taskId = TaskId(5), tags = listOf("other"), billable = false),
            entry(3, "", projectId = null),
            entry(4, "", WEBSITE.id),
            entry(5, "Write the report", projectId = null),
        )
        assertEquals(
            listOf(
                DescriptionSuggestion(
                    EntryFields("Write the report", WEBSITE.id, TaskId(5), listOf("call"), billable = true),
                    "Website",
                    "#0b83d9",
                ),
                DescriptionSuggestion(EntryFields("", WEBSITE.id), "Website", "#0b83d9"),
                DescriptionSuggestion(EntryFields("Write the report"), null, null),
            ),
            descriptionSuggestions(entries, PROJECTS, CLIENTS, "  "),
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
            descriptionSuggestions(entries, PROJECTS, CLIENTS, " REVIEW ").map { it.fields.description },
        )
        assertEquals(
            listOf("Design review"),
            descriptionSuggestions(entries, PROJECTS, CLIENTS, "acme").map { it.fields.description },
        )
        assertEquals(
            listOf("Call", "Standup"),
            descriptionSuggestions(entries, PROJECTS, CLIENTS, "intern").map { it.fields.description },
        )
        // The description, project and client are joined with spaces, as the web app joins them.
        assertEquals(
            listOf("Design review"),
            descriptionSuggestions(entries, PROJECTS, CLIENTS, "review website").map { it.fields.description },
        )
        assertEquals(
            emptyList<String>(),
            descriptionSuggestions(entries, PROJECTS, CLIENTS, "nothing").map { it.fields.description },
        )
    }

    @Test
    fun atMostEightShow() {
        val entries = (1L..12L).map { entry(it, "Entry $it", projectId = null) }
        assertEquals(
            (1..8).map { "Entry $it" },
            descriptionSuggestions(entries, PROJECTS, CLIENTS, "entry").map { it.fields.description },
        )
    }

    private fun entry(
        id: Long,
        description: String,
        projectId: ProjectId?,
        taskId: TaskId? = null,
        tags: List<String> = emptyList(),
        billable: Boolean = false,
    ) = TimeEntry(
        EntryId(id), WorkspaceId(WORKSPACE), projectId, taskId, description,
        Instant.EPOCH.minusSeconds(
            id,
        ),
        Instant.EPOCH, billable, tags, emptyList(),
    )

    private companion object {
        const val WORKSPACE = 10L
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
