package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.Destination
import com.github.e0ipso.tokkl.capabilities.Capabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnavailablePagesTest {
    private val everything = Capabilities.NONE.copy(trash = true, favorites = true, timerState = true)

    @Test
    fun aServerWithEveryFeatureOffersEveryPage() {
        assertTrue(unavailablePages(everything, hasFavorites = true).isEmpty())
    }

    @Test
    fun withoutTheTimerApiThePomodoroPageIsGone() {
        assertEquals(setOf(TopLevelPage.POMODORO), unavailablePages(everything.copy(timerState = false), hasFavorites = true))
    }

    @Test
    fun trashAndTemplatesStillFollowTheirOwnCapabilities() {
        assertEquals(
            setOf(TopLevelPage.TRASH, TopLevelPage.TEMPLATES, TopLevelPage.POMODORO),
            unavailablePages(Capabilities.NONE, hasFavorites = true),
        )
        assertEquals(setOf(TopLevelPage.TEMPLATES), unavailablePages(everything, hasFavorites = false))
    }

    @Test
    fun aRestoredOrDirectOpenOfAnUnavailablePageShowsTimerAndOtherPagesStay() {
        val unavailable = setOf(TopLevelPage.POMODORO)
        assertEquals(Destination.TIMER, Destination.POMODORO.orTimerIfUnavailable(unavailable))
        assertEquals(Destination.REPORTS, Destination.REPORTS.orTimerIfUnavailable(unavailable))
        assertEquals("a screen below a page is not a page", Destination.LICENCES, Destination.LICENCES.orTimerIfUnavailable(unavailable))
        assertEquals(Destination.POMODORO, Destination.POMODORO.orTimerIfUnavailable(emptySet()))
    }
}
