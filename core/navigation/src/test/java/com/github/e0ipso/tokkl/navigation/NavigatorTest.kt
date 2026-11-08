package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** What the screens' navigation calls leave on the back stack, and the entry form's openings. */
class NavigatorTest {
    private val stack = mutableListOf<Route>(Route.Timer)
    private val openings = EntryFormOpenings()
    private val navigator = Navigator(stack, openings)

    @Test
    fun aBarPageReplacesWhateverStoodAboveTimer() {
        navigator.open(PageRoute)
        navigator.push(AboveRoute)
        navigator.open(OtherPageRoute)
        assertEquals(listOf(Route.Timer, OtherPageRoute), stack)
    }

    @Test
    fun anUnavailablePageOpensTimer() {
        navigator.open(PageRoute)
        navigator.open(OtherPageRoute, unavailable = setOf(OtherPageRoute))
        assertEquals(listOf(Route.Timer), stack)
    }

    @Test
    fun backClosesTheScreenOnTopButNeverTimer() {
        navigator.open(PageRoute)
        navigator.push(AboveRoute)
        navigator.back()
        assertEquals(listOf(Route.Timer, PageRoute), stack)
        navigator.back()
        navigator.back()
        assertEquals(listOf(Route.Timer), stack)
    }

    @Test
    fun closeClosesOnlyTheScreenOnTop() {
        navigator.push(FocusedRoute)
        navigator.openEntry(EntryOpening.New())
        navigator.close(FocusedRoute)
        assertEquals(3, stack.size)
    }

    @Test
    fun startingOverLeavesTheRouteAlone() {
        navigator.open(PageRoute)
        navigator.startOver(StartRoute())
        assertEquals(listOf<Route>(StartRoute()), stack)
    }

    @Test
    fun theFocusedScreenAndTheFormAboveItHideTheBar() {
        navigator.push(FocusedRoute)
        assertTrue(navigator.focused)
        navigator.openEntry(EntryOpening.New())
        assertTrue(navigator.focused)
        navigator.downToTimer()
        assertFalse(navigator.focused)
    }

    @Test
    fun aFormTakesWhatItOpensOnceAndAFormFromAnotherProcessNothing() {
        val older =
            TimeEntry(
                EntryId(
                    7,
                ),
                WorkspaceId(1), null, null, "older", Instant.EPOCH, Instant.EPOCH, false, emptyList(), emptyList(),
            )
        navigator.openEntry(EntryOpening.Edit(EntryId(7)), older)
        val form = stack.last() as Route.Entry
        val request = openings.take(form)
        assertEquals(EntryOpening.Edit(EntryId(7)), request?.opening)
        assertSame(older, request?.older)
        assertNull("a configuration change makes no second opening", openings.take(form))
        assertNull(
            "a restored route that this process did not open",
            openings.take(Route.Entry(EntryOpening.Edit(EntryId(7)), id = "restored")),
        )
    }

    @Test
    fun theRouteOfARunningSegmentOrANewEntryKeepsWhatItOpens() {
        val identity = RunningEntryIdentity(
            entryId = EntryId(3),
            workspaceId = WorkspaceId(4),
            start = Instant.ofEpochSecond(1_700_000_000, 123),
        )
        assertEquals(identity, EntryOpening.Running.of(identity).identity)
        val day = LocalDate.of(2026, 11, 6)
        assertEquals(day, EntryOpening.New.on(day, noTimes = true).day)
        assertNull(EntryOpening.New.on(null).day)
    }
}
