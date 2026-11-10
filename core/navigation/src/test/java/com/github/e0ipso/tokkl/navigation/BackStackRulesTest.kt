package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.model.EntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The back stack rules of docs/PRD.md B3 and of the focused timer, on plain lists. */
class BackStackRulesTest {
    private val form = Route.Entry(EntryOpening.Edit(EntryId(1)), id = "a")

    @Test
    fun aPageOpensRightAboveTimerAndTimerOpensAlone() {
        assertEquals(listOf(Route.Timer, PageRoute), stackOpening(PageRoute))
        assertEquals(listOf(Route.Timer, OtherPageRoute), stackOpening(OtherPageRoute))
        assertEquals(listOf(Route.Timer), stackOpening(Route.Timer))
    }

    @Test
    fun aFormOpenedOverAFormReplacesItAndOtherwiseStacks() {
        val other = Route.Entry(EntryOpening.Edit(EntryId(1)), id = "b")
        assertEquals(listOf(Route.Timer, form), listOf<Route>(Route.Timer).withEntry(form))
        assertEquals(listOf(Route.Timer, other), listOf(Route.Timer, form).withEntry(other))
        assertEquals(listOf(Route.Timer, FocusedRoute, form), listOf(Route.Timer, FocusedRoute).withEntry(form))
        assertEquals(listOf(Route.Timer, AboveRoute, form), listOf(Route.Timer, AboveRoute).withEntry(form))
    }

    @Test
    fun aFocusedScreenAndTheFormOpenedFromItHideTheBar() {
        assertTrue(listOf(Route.Timer, FocusedRoute).isFocused())
        assertTrue(listOf(Route.Timer, FocusedRoute, form).isFocused())
        assertFalse(listOf(Route.Timer, form).isFocused())
        assertFalse(listOf(Route.Timer, AboveRoute, form).isFocused())
        assertFalse(listOf<Route>(Route.Timer).isFocused())
    }

    @Test
    fun anIdleTimerClosesTheFocusedScreensDownToTimer() {
        assertEquals(listOf(Route.Timer), listOf(Route.Timer, FocusedRoute, form).downToTimer())
        assertEquals(listOf(Route.Timer), listOf(Route.Timer, FocusedRoute).downToTimer())
        assertEquals(listOf<Route>(PageRoute), listOf<Route>(PageRoute).downToTimer())
    }

    @Test
    fun eachOpeningOfTheSameEntryHasItsOwnKey() {
        val again = form.copy(id = "b")
        assertTrue(form.contentKey != again.contentKey)
        assertTrue(StartRoute().contentKey != StartRoute(name = "again").contentKey)
    }
}
