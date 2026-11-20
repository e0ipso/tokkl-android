package com.github.e0ipso.tokkl.entryfields

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.time.jsTrim

/**
 * A description the timer suggests: a recent entry's [fields], all of which a pick fills, and its
 * project's name and `#rrggbb` colour, null without a project or when the cache lacks it.
 */
@Immutable
data class DescriptionSuggestion(val fields: EntryFields, val project: String?, val projectColor: String?)

/** How many suggestions show at most, as in the web app. */
const val MAX_SUGGESTIONS = 8

/**
 * The cached entries a description field suggests, each joined with its project and client
 * once, when the cache changes, so that each letter typed only filters them ([forQuery]). A
 * view model follows them with [descriptionSuggestions], and the screen limits them to the
 * entry's workspace ([inWorkspace]) before it hands them to the field.
 *
 * Two rules differ from the web app's `suggestionsFrom` in
 * ../tokkl/packages/web/src/components/useEntryFields.ts: an entry without a description never
 * shows, even with a project, and the suggestions come from one workspace only.
 */
@Immutable
class DescriptionSuggestions private constructor(private val candidates: List<Candidate>) {

    /**
     * The suggestions for [query], newest first, as the web app's `suggestionsFrom` in
     * ../tokkl/packages/web/src/components/useEntryFields.ts ranks them: entries in their order,
     * skipping one with the same description, project and task as an earlier one. An entry
     * matches when its description, its project's name or its client's name holds the trimmed
     * query, in any case; an empty query matches every entry. At most [MAX_SUGGESTIONS].
     */
    fun forQuery(query: String): List<DescriptionSuggestion> {
        val needle = query.jsTrim().lowercase()
        return candidates.asSequence()
            .filter { needle.isEmpty() || needle in it.haystack }
            .distinctBy { Triple(it.entry.description, it.entry.projectId, it.entry.taskId) }
            .take(MAX_SUGGESTIONS)
            .map { it.suggestion }
            .toList()
    }

    /** The last [inWorkspace] answer, so a page drawn again for each letter typed filters once. */
    @Volatile
    private var limited: Limited? = null

    /**
     * These suggestions limited to the entries of [workspaceId], or none without a workspace. It
     * keeps its last answer, so a view model can call it each time it publishes a page, in the
     * same step as the form whose workspace it uses.
     */
    fun inWorkspace(workspaceId: WorkspaceId?): DescriptionSuggestions {
        limited?.takeIf { it.workspaceId == workspaceId }?.let { return it.suggestions }
        val suggestions = if (workspaceId == null) {
            NONE
        } else {
            DescriptionSuggestions(candidates.filter { it.entry.workspaceId == workspaceId })
        }
        limited = Limited(workspaceId, suggestions)
        return suggestions
    }

    companion object {
        /** No entries, so no suggestions, as before the cache has been read. */
        val NONE = DescriptionSuggestions(emptyList())

        /**
         * The suggestions among the cached [entries], newest first, with their [projects] and
         * [clients]. An entry whose description is empty or blank gives none.
         */
        fun of(entries: List<TimeEntry>, projects: List<Project>, clients: List<Client>): DescriptionSuggestions {
            val projectsById = projects.associateBy { it.id }
            val clientsById = clients.associateBy { it.id }
            val candidates = entries
                .filterNot { it.description.jsTrim().isEmpty() }
                .map { entry ->
                    val project = entry.projectId?.let(projectsById::get)
                    val client = project?.clientId?.let(clientsById::get)
                    val haystack = "${entry.description} ${project?.name.orEmpty()} ${client?.name.orEmpty()}"
                    Candidate(
                        entry = entry,
                        haystack = haystack.lowercase(),
                        suggestion = DescriptionSuggestion(entry.fields(), project?.name, project?.color),
                    )
                }
            return DescriptionSuggestions(candidates)
        }
    }

    private class Limited(val workspaceId: WorkspaceId?, val suggestions: DescriptionSuggestions)

    private class Candidate(val entry: TimeEntry, val haystack: String, val suggestion: DescriptionSuggestion)
}
