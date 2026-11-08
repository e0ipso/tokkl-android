package com.github.e0ipso.tokkl.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The back stack rules of docs/PRD.md B3 and of the focused timer, on plain lists. */
class BackStackRulesTest {
    private val form = Route.Entry(EntryOpening.Edit(1), id = "a")

    @Test
    fun aPageOpensRightAboveTimerAndTimerOpensAlone() {
        assertEquals(listOf(Route.Timer, Route.Reports), stackOpening(Route.Reports))
        assertEquals(listOf(Route.Timer, Route.Settings), stackOpening(Route.Settings))
        assertEquals(listOf(Route.Timer), stackOpening(Route.Timer))
    }

    @Test
    fun aFormOpenedOverAFormReplacesItAndOtherwiseStacks() {
        val other = Route.Entry(EntryOpening.Edit(1), id = "b")
        assertEquals(listOf(Route.Timer, form), listOf<Route>(Route.Timer).withEntry(form))
        assertEquals(listOf(Route.Timer, other), listOf(Route.Timer, form).withEntry(other))
        assertEquals(
            listOf(Route.Timer, Route.FullscreenTimer, form),
            listOf(Route.Timer, Route.FullscreenTimer).withEntry(form),
        )
        assertEquals(
            listOf(Route.Timer, Route.NeedsAttention, form),
            listOf(Route.Timer, Route.NeedsAttention).withEntry(form),
        )
    }

    @Test
    fun theFullscreenTimerAndTheFormOpenedFromItHideTheBar() {
        assertTrue(listOf(Route.Timer, Route.FullscreenTimer).isFocused())
        assertTrue(listOf(Route.Timer, Route.FullscreenTimer, form).isFocused())
        assertFalse(listOf(Route.Timer, form).isFocused())
        assertFalse(listOf<Route>(Route.Timer).isFocused())
    }

    @Test
    fun anIdleTimerClosesTheFocusedScreensDownToTimer() {
        assertEquals(listOf(Route.Timer), listOf(Route.Timer, Route.FullscreenTimer, form).downToTimer())
        assertEquals(listOf(Route.Timer), listOf(Route.Timer, Route.FullscreenTimer).downToTimer())
        assertEquals(listOf<Route>(Route.Pomodoro), listOf<Route>(Route.Pomodoro).downToTimer())
    }

    @Test
    fun eachOpeningOfTheSameEntryHasItsOwnKey() {
        val again = form.copy(id = "b")
        assertTrue(form.contentKey != again.contentKey)
        assertTrue(Route.SignIn().contentKey != Route.SignIn(server = "https://tokkl.example", userId = 1).contentKey)
    }
}
