package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        navigator.open(Route.Settings)
        navigator.push(Route.Licences)
        navigator.open(Route.Reports)
        assertEquals(listOf(Route.Timer, Route.Reports), stack)
    }

    @Test
    fun anUnavailablePageOpensTimer() {
        navigator.open(Route.Settings)
        navigator.open(Route.Pomodoro, unavailable = setOf(Route.Pomodoro))
        assertEquals(listOf(Route.Timer), stack)
    }

    @Test
    fun backClosesTheScreenOnTopButNeverTimer() {
        navigator.open(Route.Settings)
        navigator.push(Route.Licences)
        navigator.back()
        assertEquals(listOf(Route.Timer, Route.Settings), stack)
        navigator.back()
        navigator.back()
        assertEquals(listOf(Route.Timer), stack)
    }

    @Test
    fun closeClosesOnlyTheScreenOnTop() {
        navigator.push(Route.FullscreenTimer)
        navigator.openEntry(EntryOpening.New())
        navigator.close(Route.FullscreenTimer)
        assertEquals(3, stack.size)
    }

    @Test
    fun theNeedsAttentionListOpensOnce() {
        navigator.openNeedsAttention()
        navigator.openNeedsAttention()
        assertEquals(listOf(Route.Timer, Route.NeedsAttention), stack)
    }

    @Test
    fun signInStartsOver() {
        navigator.open(Route.Settings)
        navigator.signIn()
        assertEquals(listOf<Route>(Route.SignIn()), stack)
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
