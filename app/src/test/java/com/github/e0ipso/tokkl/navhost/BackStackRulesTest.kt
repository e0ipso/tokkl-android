package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.settings.LicencesRoute
import com.github.e0ipso.tokkl.settings.SettingsRoute
import com.github.e0ipso.tokkl.signin.SignInRoute
import com.github.e0ipso.tokkl.sync.NeedsAttentionRoute
import com.github.e0ipso.tokkl.trash.TrashRoute
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
        assertEquals(SettingsRoute, pages.currentPage(listOf(Route.Timer, SettingsRoute, LicencesRoute)))
        assertEquals(Route.Timer, pages.currentPage(listOf(Route.Timer, form)))
        assertEquals(ReportsRoute, pages.currentPage(listOf(Route.Timer, ReportsRoute, NeedsAttentionRoute)))
        assertEquals(Route.Timer, pages.currentPage(listOf<Route>(SignInRoute())))
    }

    @Test
    fun aStackHoldingAnUnavailablePageIsFound() {
        val unavailable = setOf(TrashRoute)
        assertTrue(listOf(Route.Timer, TrashRoute).holdsAnyOf(unavailable))
        assertFalse(listOf(Route.Timer, SettingsRoute, LicencesRoute).holdsAnyOf(unavailable))
    }
}
