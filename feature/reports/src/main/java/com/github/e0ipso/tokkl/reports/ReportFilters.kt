package com.github.e0ipso.tokkl.reports

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.api.ReportFilter
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
@Immutable
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
    val isEmpty: Boolean get() = projects.isEmpty() && clients.isEmpty() && tags.isEmpty() &&
        billable == BillableFilter.ALL

    /** These filters with every "Is not" turned back to "Is", for a server that cannot negate. */
    fun withoutNegation(): ReportFilters = copy(projectsNot = false, clientsNot = false, tagsNot = false)

    /**
     * These filters as a report request sends them, the fields of the web app's `filterBody`.
     * Tokkl reads them the same way on its v3 endpoints and on the web app's own, and so does
     * Toggl Track, which also takes null for "without". A negated list goes in its `exclude_`
     * field, as the web app's `idFilter` sends it; Toggl Track has no such field.
     */
    internal fun apiFilter(): ReportFilter = ReportFilter(
        projectIds = projects,
        excludeProjects = projectsNot,
        clientIds = clients,
        excludeClients = clientsNot,
        tagIds = tags,
        excludeTags = tagsNot,
        billable = when (billable) {
            BillableFilter.ALL -> null
            BillableFilter.BILLABLE -> true
            BillableFilter.NON_BILLABLE -> false
        },
    )
}

/** [this] with [id] added when it was not in it, or without it when it was. */
fun List<Long?>.toggled(id: Long?): List<Long?> = if (id in this) this - id else this + id

/** One choice of a filter: a project, a client or a tag of the workspace. */
data class FilterOption(val id: Long, val name: String)

/**
 * The options whose name holds [search], ignoring case, as the web app's filter menus narrow
 * theirs, and those [picked] holds, so a search never hides what is ticked. A blank [search]
 * keeps them all. Trim [search] first, as the web app does.
 */
fun List<FilterOption>.matching(search: String, picked: List<Long?>): List<FilterOption> =
    if (search.isEmpty()) this else filter { it.id in picked || it.name.contains(search, ignoreCase = true) }

/**
 * The projects, clients and tags the filter sheet offers, read from the phone's cache, so opening
 * the sheet sends nothing. As in the web app, archived projects and clients are offered too, and
 * active projects come before archived ones. [negation] offers "Is" and "Is not" on each of the
 * three lists, only where the server honours it.
 */
@Immutable
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
            projects: List<Project>,
            clients: List<Client>,
            tags: List<Tag>,
            workspaceId: WorkspaceId?,
            negation: Boolean,
        ): FilterOptions {
            fun inWorkspace(id: WorkspaceId) = workspaceId == null || id == workspaceId
            return FilterOptions(
                projects = projects.filter {
                    inWorkspace(
                        it.workspaceId,
                    )
                }.sortedBy { !it.active }.map { FilterOption(it.id.value, it.name) },
                clients = clients.filter { inWorkspace(it.workspaceId) }.map { FilterOption(it.id.value, it.name) },
                tags = tags.filter { inWorkspace(it.workspaceId) }.map { FilterOption(it.id.value, it.name) },
                negation = negation,
            )
        }
    }
}

/**
 * The options of the filter sheet as the cache, the settings and the capabilities change: the
 * projects, clients and tags of the default workspace, and negation where the server offers it.
 * From the Room cache, never from the server.
 */
internal fun reportFilterOptions(
    cache: AccountCache,
    settings: Flow<AccountSettings?>,
    capabilities: Flow<Capabilities>,
): Flow<FilterOptions> = combine(
    cache.projects,
    cache.clients,
    cache.tags,
    settings,
    capabilities,
) { projects, clients, tags, account, flags ->
    FilterOptions.of(projects, clients, tags, account?.defaultWorkspaceId, negation = flags.negatedFilters)
}
