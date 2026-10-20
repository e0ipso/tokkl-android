package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTag
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Which entries the billable filter keeps, the choices of the web app's Billable select. */
enum class BillableFilter {
    ALL,
    BILLABLE,
    NON_BILLABLE,
}

/**
 * The report filters of the web app's `Filters` (docs/PRD.md R4). Each id list keeps the entries
 * that match any of its ids, and a null id stands for none: an entry without a project, a project
 * without a client, or an entry without tags. A tag id keeps an entry that carries that tag among
 * others. An empty list filters nothing. An entry has to pass every filter and the range.
 *
 * [projectsNot], [clientsNot] and [tagsNot] turn their list into the web app's "Is not": it drops
 * the entries that match any of its ids instead (docs/PRD.md R3). Only Tokkl honours them, so the
 * sheet offers them where [Capabilities.negatedFilters] is on.
 */
data class ReportFilters(
    val projects: List<Long?> = emptyList(),
    val clients: List<Long?> = emptyList(),
    val tags: List<Long?> = emptyList(),
    val billable: BillableFilter = BillableFilter.ALL,
    val projectsNot: Boolean = false,
    val clientsNot: Boolean = false,
    val tagsNot: Boolean = false,
) {

    /** True when no filter is set, so a report covers every entry of its range. */
    val isEmpty: Boolean get() = projects.isEmpty() && clients.isEmpty() && tags.isEmpty() && billable == BillableFilter.ALL

    /** These filters with every "Is not" turned back to "Is", for a server that cannot negate. */
    fun withoutNegation(): ReportFilters = copy(projectsNot = false, clientsNot = false, tagsNot = false)

    /**
     * Adds the fields of the web app's `filterBody` to a Reports API v3 request [body]. Tokkl reads
     * them the same way on its v3 endpoints and on the web app's own, and so does Toggl Track,
     * which also takes null for "without". A negated list goes in its `exclude_` field, as the web
     * app's `idFilter` sends it; Toggl Track has no such field.
     */
    internal fun writeTo(body: JsonObjectBuilder) {
        writeIds(body, if (projectsNot) "exclude_project_ids" else "project_ids", projects)
        writeIds(body, if (clientsNot) "exclude_client_ids" else "client_ids", clients)
        writeIds(body, if (tagsNot) "exclude_tag_ids" else "tag_ids", tags)
        when (billable) {
            BillableFilter.ALL -> Unit
            BillableFilter.BILLABLE -> body.put("billable", true)
            BillableFilter.NON_BILLABLE -> body.put("billable", false)
        }
    }

    private fun writeIds(body: JsonObjectBuilder, field: String, ids: List<Long?>) {
        if (ids.isEmpty()) return
        body.putJsonArray(field) { for (id in ids) add(if (id == null) JsonNull else JsonPrimitive(id)) }
    }
}

/** [this] with [id] added when it was not in it, or without it when it was. */
fun List<Long?>.toggled(id: Long?): List<Long?> = if (id in this) this - id else this + id

/** One choice of a filter: a project, a client or a tag of the workspace. */
data class FilterOption(val id: Long, val name: String)

/**
 * The projects, clients and tags the filter sheet offers, read from the phone's cache, so opening
 * the sheet sends nothing. As in the web app, archived projects and clients are offered too, and
 * active projects come before archived ones. [negation] offers "Is" and "Is not" on each of the
 * three lists, only where the server honours it.
 */
data class FilterOptions(
    val projects: List<FilterOption>,
    val clients: List<FilterOption>,
    val tags: List<FilterOption>,
    val negation: Boolean,
) {

    companion object {
        val NONE = FilterOptions(emptyList(), emptyList(), emptyList(), negation = false)

        /**
         * The options of [workspaceId], the account's default workspace that reports cover, or of
         * every cached workspace when it is unknown. The cache sorts each list by name.
         */
        fun of(
            projects: List<CachedProject>,
            clients: List<CachedClient>,
            tags: List<CachedTag>,
            workspaceId: Long?,
            negation: Boolean,
        ): FilterOptions {
            fun inWorkspace(id: Long) = workspaceId == null || id == workspaceId
            return FilterOptions(
                projects = projects.filter { inWorkspace(it.workspaceId) }.sortedBy { !it.active }.map { FilterOption(it.id, it.name) },
                clients = clients.filter { inWorkspace(it.workspaceId) }.map { FilterOption(it.id, it.name) },
                tags = tags.filter { inWorkspace(it.workspaceId) }.map { FilterOption(it.id, it.name) },
                negation = negation,
            )
        }
    }
}
