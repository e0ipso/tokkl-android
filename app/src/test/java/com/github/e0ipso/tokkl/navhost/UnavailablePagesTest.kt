package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnavailablePagesTest {
    private val everything = Capabilities.NONE.copy(trash = true, favorites = true, timerState = true)

    @Test
    fun aServerWithEveryFeatureOffersEveryPage() {
        assertTrue(unavailablePages(everything).isEmpty())
    }

    @Test
    fun withoutTheTimerApiThePomodoroPageIsGone() {
        assertEquals(setOf(Route.Pomodoro), unavailablePages(everything.copy(timerState = false)))
    }

    @Test
    fun trashAndTemplatesStillFollowTheirOwnCapabilities() {
        assertEquals(
            setOf(Route.Trash, Route.Templates, Route.Pomodoro),
            unavailablePages(Capabilities.NONE),
        )
        assertEquals(setOf(Route.Templates), unavailablePages(everything.copy(favorites = false)))
    }

    @Test
    fun aRestoredOrDirectOpenOfAnUnavailablePageShowsTimerAndOtherPagesStay() {
        val unavailable = setOf(Route.Pomodoro)
        assertEquals(Route.Timer, Route.Pomodoro.orTimerIfUnavailable(unavailable))
        assertEquals(Route.Reports, Route.Reports.orTimerIfUnavailable(unavailable))
        assertEquals(
            "a screen below a page is not a page",
            Route.Licences,
            Route.Licences.orTimerIfUnavailable(unavailable),
        )
        assertEquals(Route.Pomodoro, Route.Pomodoro.orTimerIfUnavailable(emptySet()))
    }
}
