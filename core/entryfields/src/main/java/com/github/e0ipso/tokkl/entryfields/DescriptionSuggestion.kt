package com.github.e0ipso.tokkl.entryfields

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.TimeEntry
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
 * screen's view model makes it with [of] and the screen hands it to the field.
 */
@Immutable
class DescriptionSuggestions private constructor(private val candidates: List<Candidate>) {

    /**
     * The suggestions for [query], newest first, as the web app's `suggestionsFrom` in
     * ../tokkl/packages/web/src/components/TimerBar.tsx ranks them: entries in their order,
     * skipping one with neither a description nor a project, and one with the same description,
     * project and task as an earlier one. An entry matches when its description, its project's
     * name or its client's name holds the trimmed query, in any case; an empty query matches
     * every entry. At most [MAX_SUGGESTIONS].
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

    companion object {
        /** No entries, so no suggestions, as before the cache has been read. */
        val NONE = DescriptionSuggestions(emptyList())

        /** The suggestions among the cached [entries], newest first, with their [projects] and [clients]. */
        fun of(entries: List<TimeEntry>, projects: List<Project>, clients: List<Client>): DescriptionSuggestions {
            val projectsById = projects.associateBy { it.id }
            val clientsById = clients.associateBy { it.id }
            val candidates = entries
                .filterNot { it.description.isEmpty() && it.projectId == null }
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

    private class Candidate(val entry: TimeEntry, val haystack: String, val suggestion: DescriptionSuggestion)
}

fun descriptionSuggestions(
    entries: List<TimeEntry>,
    projects: List<Project>,
    clients: List<Client>,
    query: String,
): List<DescriptionSuggestion> = DescriptionSuggestions.of(entries, projects, clients).forQuery(query)
