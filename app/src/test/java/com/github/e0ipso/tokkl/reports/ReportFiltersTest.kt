package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTag
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFiltersTest {

    /** As the web app's Filters: the default workspace only, archived ones too, active projects first. */
    @Test
    fun theOptionsAreTheWorkspacesWithActiveProjectsFirst() {
        // The cache lists each table by name.
        val projects = listOf(
            CachedProject(1, WORKSPACE, null, "Archive", null, active = false, billable = false),
            CachedProject(2, WORKSPACE, 5, "Beta", null, active = true, billable = true),
            CachedProject(3, OTHER_WORKSPACE, null, "Other", null, active = true, billable = false),
            CachedProject(4, WORKSPACE, null, "Zed", null, active = true, billable = false),
        )
        val clients = listOf(CachedClient(5, WORKSPACE, "Acme", archived = true), CachedClient(6, OTHER_WORKSPACE, "Elsewhere", archived = false))
        val tags = listOf(CachedTag(7, WORKSPACE, "deep"), CachedTag(8, OTHER_WORKSPACE, "other"))

        val options = FilterOptions.of(projects, clients, tags, WORKSPACE, negation = true)

        assertEquals(listOf(2L to "Beta", 4L to "Zed", 1L to "Archive"), options.projects.map { it.id to it.name })
        assertEquals(listOf(5L to "Acme"), options.clients.map { it.id to it.name })
        assertEquals(listOf(7L to "deep"), options.tags.map { it.id to it.name })
        assertTrue(options.negation)
        assertEquals("every workspace while the default one is unknown", 4, FilterOptions.of(projects, clients, tags, null, negation = false).projects.size)
    }

    @Test
    fun aToggleAddsOrRemovesOneIdAndKeepsTheOrderPicked() {
        assertEquals(listOf(3L, null), listOf<Long?>(3L).toggled(null))
        assertEquals(listOf<Long?>(null), listOf(3L, null).toggled(3L))
        assertEquals(emptyList<Long?>(), listOf<Long?>(null).toggled(null))
    }

    /** As the web app's filter menus: the name holds the text, ignoring case, and a ticked option always stays. */
    @Test
    fun aSearchKeepsTheNamesHoldingItAndThePickedOnes() {
        val options = listOf(FilterOption(1, "Website"), FilterOption(2, "Internal"), FilterOption(3, "Web shop"))

        assertEquals(listOf(1L, 3L), options.matching("WEB", picked = emptyList()).map { it.id })
        assertEquals(listOf(1L, 2L), options.matching("site", picked = listOf(null, 2L)).map { it.id })
        assertEquals(emptyList<Long>(), options.matching("zzz", picked = listOf(null)).map { it.id })
        assertEquals("a blank search keeps them all", options, options.matching("", picked = emptyList()))
    }

    /** Toggl's Reports API v3 takes the same fields, with null for "without". */
    @Test
    fun theFieldsAreTheWebAppsAndOnlyForFiltersInUse() {
        assertTrue(ReportFilters().isEmpty)
        assertEquals("{}", buildJsonObject { ReportFilters().writeTo(this) }.toString())

        val filters = ReportFilters(projects = listOf(null, 9L), tags = listOf(4L, 2L), billable = BillableFilter.NON_BILLABLE)
        assertFalse(filters.isEmpty)
        assertEquals(
            """{"project_ids":[null,9],"tag_ids":[4,2],"billable":false}""",
            buildJsonObject { filters.writeTo(this) }.toString(),
        )
        assertFalse(ReportFilters(billable = BillableFilter.BILLABLE).isEmpty)
    }

    /** As the web app's idFilter: "Is not" sends a list in its exclude field instead, null still for "without". */
    @Test
    fun aNegatedFilterSendsItsExcludeField() {
        val filters = ReportFilters(
            projects = listOf(9L),
            clients = listOf(null, 5L),
            tags = listOf(4L),
            projectsNot = true,
            clientsNot = true,
        )
        assertEquals(
            """{"exclude_project_ids":[9],"exclude_client_ids":[null,5],"tag_ids":[4]}""",
            buildJsonObject { filters.writeTo(this) }.toString(),
        )
        assertEquals(
            "a negated filter with nothing picked filters nothing, as in the web app",
            "{}",
            buildJsonObject { ReportFilters(projectsNot = true, clientsNot = true, tagsNot = true).writeTo(this) }.toString(),
        )
        assertTrue(ReportFilters(tagsNot = true).isEmpty)
        assertEquals(
            """{"project_ids":[9],"client_ids":[null,5],"tag_ids":[4]}""",
            buildJsonObject { filters.withoutNegation().writeTo(this) }.toString(),
        )
    }

    private companion object {
        const val WORKSPACE = 10L
        const val OTHER_WORKSPACE = 11L
    }
}
