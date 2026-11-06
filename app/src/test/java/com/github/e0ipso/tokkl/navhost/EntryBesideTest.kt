package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.timer.TimerView
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
        assertTrue(showsEntryBeside(WindowWidth.EXPANDED, TimerView.LIST, formOverTimer))
    }

    @Test
    fun compactAndMediumWindowsShowTheFormAlone() {
        assertFalse(showsEntryBeside(WindowWidth.COMPACT, TimerView.LIST, formOverTimer))
        assertFalse(showsEntryBeside(WindowWidth.MEDIUM, TimerView.LIST, formOverTimer))
    }

    @Test
    fun theCalendarKeepsTheWholeWidth() {
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, TimerView.CALENDAR, formOverTimer))
    }

    @Test
    fun onlyTheFormOnTopOfTheTimerPageGoesBeside() {
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, TimerView.LIST, listOf(Route.Timer)))
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, TimerView.LIST, listOf(Route.Timer, Route.Reports)))
        assertFalse(showsEntryBeside(WindowWidth.EXPANDED, TimerView.LIST, listOf(form)))
        assertFalse(
            showsEntryBeside(WindowWidth.EXPANDED, TimerView.LIST, listOf(Route.Timer, Route.Settings, form)),
        )
    }
}
