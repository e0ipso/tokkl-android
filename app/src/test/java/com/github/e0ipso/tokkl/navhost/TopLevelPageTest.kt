package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

/** The top-level pages the features bind, in the order the bar, the rail and the More sheet show them. */
class TopLevelPageTest {
    private val pages = appTopLevelPages

    @Test
    fun theBarAndRailUseTheOwnerOrder() {
        assertEquals(
            listOf(Route.Timer, Route.Pomodoro, Route.Projects, Route.Settings),
            pages.all.filterNot { it.inMore }.map { it.route },
        )
        assertEquals(Route.Pomodoro, pages.of(Route.Pomodoro)?.route)
    }

    @Test
    fun moreKeepsReportsAndTemplatesBeforeTheOtherPages() {
        assertEquals(
            listOf(Route.Reports, Route.Templates, Route.Clients, Route.Tags, Route.Trash),
            pages.all.filter { it.inMore }.map { it.route },
        )
    }
}
