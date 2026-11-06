package com.github.e0ipso.tokkl.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelPageTest {
    @Test
    fun theBarAndRailUseTheOwnerOrder() {
        assertEquals(
            listOf(TopLevelPage.TIMER, TopLevelPage.POMODORO, TopLevelPage.PROJECTS, TopLevelPage.SETTINGS),
            TopLevelPage.entries.filterNot { it.inMore },
        )
        assertEquals(TopLevelPage.POMODORO, TopLevelPage.of(Route.Pomodoro))
    }

    @Test
    fun moreKeepsReportsAndTemplatesBeforeTheOtherPages() {
        assertEquals(
            listOf(TopLevelPage.REPORTS, TopLevelPage.TEMPLATES, TopLevelPage.CLIENTS, TopLevelPage.TAGS, TopLevelPage.TRASH),
            TopLevelPage.entries.filter { it.inMore },
        )
    }
}
