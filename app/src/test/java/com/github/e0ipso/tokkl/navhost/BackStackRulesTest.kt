package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The navigation host's rules over the back stack: the page the bar marks and the pages a server lacks. */
class BackStackRulesTest {
    private val pages = appTopLevelPages
    private val form = Route.Entry(EntryOpening.Edit(EntryId(1)), id = "a")

    @Test
    fun theBarMarksThePageAScreenWasOpenedFrom() {
        assertEquals(Route.Settings, pages.currentPage(listOf(Route.Timer, Route.Settings, Route.Licences)))
        assertEquals(Route.Timer, pages.currentPage(listOf(Route.Timer, form)))
        assertEquals(Route.Reports, pages.currentPage(listOf(Route.Timer, Route.Reports, Route.NeedsAttention)))
        assertEquals(Route.Timer, pages.currentPage(listOf<Route>(Route.SignIn())))
    }

    @Test
    fun aStackHoldingAnUnavailablePageIsFound() {
        val unavailable = setOf(Route.Trash)
        assertTrue(listOf(Route.Timer, Route.Trash).holdsAnyOf(unavailable))
        assertFalse(listOf(Route.Timer, Route.Settings, Route.Licences).holdsAnyOf(unavailable))
    }
}
