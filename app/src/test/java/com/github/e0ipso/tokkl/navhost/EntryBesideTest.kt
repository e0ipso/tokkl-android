package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.settings.SettingsRoute
import com.github.e0ipso.tokkl.ui.WindowWidth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entry form shows beside the history only on an expanded window, in the List view, above the
 * Timer page. Everywhere else it takes the screen, as on a phone.
 */
class EntryBesideTest {
    private val form = Route.Entry(EntryOpening.New(), id = "form")
    private val formOverTimer = listOf(Route.Timer, form)

    @Test
    fun anExpandedWindowShowsTheFormBesideTheHistory() {
        assertTrue(showsEntryBeside(WindowWidth.EXPANDED, LIST_VIEW, formOverTimer))
    }

    @Test
    fun compactAndMediumWindowsShowTheFormAlone() {
        assertFalse(showsEntryBeside(WindowWidth.COMPACT, LIST_VIEW, formOverTimer))
        assertFalse(showsEntryBeside(WindowWidth.MEDIUM, LIST_VIEW, formOverTimer))
    }

    @Test
    fun theCalendarKeepsTheWholeWidth() {
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, CALENDAR_VIEW, formOverTimer))
    }

    @Test
    fun onlyTheFormOnTopOfTheTimerPageGoesBeside() {
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, LIST_VIEW, listOf(Route.Timer)))
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, LIST_VIEW, listOf(Route.Timer, ReportsRoute)))
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, LIST_VIEW, listOf(form)))
        assertFalse(
            showsEntryBeside(WindowWidth.EXPANDED, LIST_VIEW, listOf(Route.Timer, SettingsRoute, form)),
        )
    }

    private companion object {
        /** The Timer page's List view, beside which the form may open, and its Calendar view. */
        const val LIST_VIEW = true
        const val CALENDAR_VIEW = false
    }
}
