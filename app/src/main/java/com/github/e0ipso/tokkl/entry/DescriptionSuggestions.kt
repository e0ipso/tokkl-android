package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.time.jsTrim

/**
 * A description the timer suggests: a recent entry's [fields], all of which a pick fills, and its
 * project's name and `#rrggbb` colour, null without a project or when the cache lacks it.
 */
data class DescriptionSuggestion(val fields: EntryFields, val project: String?, val projectColor: String?)

/** How many suggestions show at most, as in the web app. */
const val MAX_SUGGESTIONS = 8

/**
 * The suggestions for [query] among the cached [entries], newest first, as the web app's
 * `suggestionsFrom` in ../tokkl/packages/web/src/components/TimerBar.tsx ranks them: entries in
 * their order, skipping one with neither a description nor a project, and one with the same
 * description, project and task as an earlier one. An entry matches when its description, its
 * project's name or its client's name holds the trimmed query, in any case; an empty query
 * matches every entry. At most [MAX_SUGGESTIONS].
 */
fun descriptionSuggestions(
    entries: List<CachedTimeEntry>,
    projects: List<CachedProject>,
    clients: List<CachedClient>,
    query: String,
): List<DescriptionSuggestion> {
    val projectsById = projects.associateBy { it.id }
    val clientsById = clients.associateBy { it.id }
    val needle = query.jsTrim().lowercase()
    val seen = mutableSetOf<Triple<String, Long?, Long?>>()
    val out = mutableListOf<DescriptionSuggestion>()
    for (entry in entries) {
        if (entry.description.isEmpty() && entry.projectId == null) continue
        val key = Triple(entry.description, entry.projectId, entry.taskId)
        if (key in seen) continue
        val project = entry.projectId?.let(projectsById::get)
        val client = project?.clientId?.let(clientsById::get)
        val haystack = "${entry.description} ${project?.name.orEmpty()} ${client?.name.orEmpty()}".lowercase()
        if (needle.isNotEmpty() && needle !in haystack) continue
        seen += key
        out += DescriptionSuggestion(entry.fields(), project?.name, project?.color)
        if (out.size >= MAX_SUGGESTIONS) break
    }
    return out
}
