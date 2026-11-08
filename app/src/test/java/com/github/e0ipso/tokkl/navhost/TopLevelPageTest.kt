package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.clients.ClientsRoute
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.projects.ProjectsRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.settings.SettingsRoute
import com.github.e0ipso.tokkl.tags.TagsRoute
import com.github.e0ipso.tokkl.timer.TemplatesRoute
import com.github.e0ipso.tokkl.trash.TrashRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/** The top-level pages the features bind, in the order the bar, the rail and the More sheet show them. */
class TopLevelPageTest {
    private val pages = appTopLevelPages

    @Test
    fun theBarAndRailUseTheOwnerOrder() {
        assertEquals(
            listOf(Route.Timer, PomodoroRoute, ProjectsRoute, SettingsRoute),
            pages.all.filterNot { it.inMore }.map { it.route },
        )
        assertEquals(PomodoroRoute, pages.of(PomodoroRoute)?.route)
    }

    @Test
    fun moreKeepsReportsAndTemplatesBeforeTheOtherPages() {
        assertEquals(
            listOf(ReportsRoute, TemplatesRoute, ClientsRoute, TagsRoute, TrashRoute),
            pages.all.filter { it.inMore }.map { it.route },
        )
    }
}
