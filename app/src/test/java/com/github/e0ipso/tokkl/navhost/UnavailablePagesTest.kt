package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.settings.LicencesRoute
import com.github.e0ipso.tokkl.timer.TemplatesRoute
import com.github.e0ipso.tokkl.trash.TrashRoute
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
        assertEquals(setOf(PomodoroRoute), unavailablePages(everything.copy(timerState = false)))
    }

    @Test
    fun trashAndTemplatesStillFollowTheirOwnCapabilities() {
        assertEquals(
            setOf(TrashRoute, TemplatesRoute, PomodoroRoute),
            unavailablePages(Capabilities.NONE),
        )
        assertEquals(setOf(TemplatesRoute), unavailablePages(everything.copy(favorites = false)))
    }

    @Test
    fun aRestoredOrDirectOpenOfAnUnavailablePageShowsTimerAndOtherPagesStay() {
        val unavailable = setOf(PomodoroRoute)
        assertEquals(Route.Timer, PomodoroRoute.orTimerIfUnavailable(unavailable))
        assertEquals(ReportsRoute, ReportsRoute.orTimerIfUnavailable(unavailable))
        assertEquals(
            "a screen below a page is not a page",
            LicencesRoute,
            LicencesRoute.orTimerIfUnavailable(unavailable),
        )
        assertEquals(PomodoroRoute, PomodoroRoute.orTimerIfUnavailable(emptySet()))
    }
}
