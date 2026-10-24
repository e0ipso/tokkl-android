package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.Destination
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TopLevelPageTest {
    @Test
    fun theBarAndRailUseTheOwnerOrder() {
        assertEquals(
            listOf(TopLevelPage.TIMER, TopLevelPage.POMODORO, TopLevelPage.PROJECTS, TopLevelPage.SETTINGS),
            TopLevelPage.entries.filterNot { it.inMore },
        )
        assertEquals(TopLevelPage.POMODORO, TopLevelPage.of(Destination.POMODORO))
    }

    @Test
    fun moreKeepsReportsAndTemplatesBeforeTheOtherPages() {
        assertEquals(
            listOf(TopLevelPage.REPORTS, TopLevelPage.TEMPLATES, TopLevelPage.CLIENTS, TopLevelPage.TAGS, TopLevelPage.TRASH),
            TopLevelPage.entries.filter { it.inMore },
        )
        assertSame(SectionHues.reports, TopLevelPage.REPORTS.hue)
    }
}
