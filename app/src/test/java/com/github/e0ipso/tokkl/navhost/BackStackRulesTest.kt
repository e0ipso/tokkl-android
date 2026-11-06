package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The navigation host's rules over the back stack: the page the bar marks and the pages a server lacks. */
class BackStackRulesTest {
    private val form = Route.Entry(EntryOpening.Edit(1), id = "a")

    @Test
    fun theBarMarksThePageAScreenWasOpenedFrom() {
        assertEquals(TopLevelPage.SETTINGS, listOf(Route.Timer, Route.Settings, Route.Licences).currentPage())
        assertEquals(TopLevelPage.TIMER, listOf(Route.Timer, form).currentPage())
        assertEquals(TopLevelPage.REPORTS, listOf(Route.Timer, Route.Reports, Route.NeedsAttention).currentPage())
        assertEquals(TopLevelPage.TIMER, listOf<Route>(Route.SignIn()).currentPage())
    }

    @Test
    fun aStackHoldingAnUnavailablePageIsFound() {
        val unavailable = setOf(TopLevelPage.TRASH)
        assertTrue(listOf(Route.Timer, Route.Trash).holdsAnyOf(unavailable))
        assertFalse(listOf(Route.Timer, Route.Settings, Route.Licences).holdsAnyOf(unavailable))
    }
}
