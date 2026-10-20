package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The timer's suggestions, ranked as `suggestionsFrom` in
 * ../tokkl/packages/web/src/components/TimerBar.tsx ranks them.
 */
class DescriptionSuggestionsTest {

    @Test
    fun anEmptyQuerySuggestsTheNewestDistinctEntries() {
        val entries = listOf(
            entry(1, "Write the report", WEBSITE.id, taskId = 5, tags = listOf("call"), billable = true),
            entry(2, "Write the report", WEBSITE.id, taskId = 5, tags = listOf("other"), billable = false),
            entry(3, "", projectId = null),
            entry(4, "", WEBSITE.id),
            entry(5, "Write the report", projectId = null),
        )
        assertEquals(
            listOf(
                DescriptionSuggestion(EntryFields("Write the report", WEBSITE.id, 5, listOf("call"), billable = true), "Website", "#0b83d9"),
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
        assertEquals(listOf("Design review"), descriptionSuggestions(entries, PROJECTS, CLIENTS, " REVIEW ").map { it.fields.description })
        assertEquals(listOf("Design review"), descriptionSuggestions(entries, PROJECTS, CLIENTS, "acme").map { it.fields.description })
        assertEquals(listOf("Call", "Standup"), descriptionSuggestions(entries, PROJECTS, CLIENTS, "intern").map { it.fields.description })
        // The description, project and client are joined with spaces, as the web app joins them.
        assertEquals(listOf("Design review"), descriptionSuggestions(entries, PROJECTS, CLIENTS, "review website").map { it.fields.description })
        assertEquals(emptyList<String>(), descriptionSuggestions(entries, PROJECTS, CLIENTS, "nothing").map { it.fields.description })
    }

    @Test
    fun atMostEightShow() {
        val entries = (1L..12L).map { entry(it, "Entry $it", projectId = null) }
        assertEquals((1..8).map { "Entry $it" }, descriptionSuggestions(entries, PROJECTS, CLIENTS, "entry").map { it.fields.description })
    }

    private fun entry(
        id: Long,
        description: String,
        projectId: Long?,
        taskId: Long? = null,
        tags: List<String> = emptyList(),
        billable: Boolean = false,
    ) = CachedTimeEntry(id, WORKSPACE, projectId, taskId, description, Instant.EPOCH.minusSeconds(id), Instant.EPOCH, billable, tags, emptyList())

    private companion object {
        const val WORKSPACE = 10L
        val ACME = CachedClient(7, WORKSPACE, "Acme", archived = false)
        val WEBSITE = CachedProject(1, WORKSPACE, clientId = ACME.id, name = "Website", color = "#0b83d9", active = true, billable = false)
        val INTERNAL = CachedProject(2, WORKSPACE, clientId = null, name = "Internal", color = null, active = true, billable = false)
        val PROJECTS = listOf(WEBSITE, INTERNAL)
        val CLIENTS = listOf(ACME)
    }
}
