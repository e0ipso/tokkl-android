package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.settings.LicencesRoute
import com.github.e0ipso.tokkl.timer.TemplatesRoute
import com.github.e0ipso.tokkl.trash.TrashRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pages each feature leaves out while its server lacks what the page needs. */
class UnavailablePagesTest {
    private val everything = Capabilities.NONE.copy(trash = true, favorites = true, timerState = true)

    @Test
    fun aServerWithEveryFeatureOffersEveryPage() {
        assertTrue(appTopLevelPages.unavailable(everything).isEmpty())
    }

    @Test
    fun withoutTheTimerApiThePomodoroPageIsGone() {
        assertEquals(setOf(PomodoroRoute), appTopLevelPages.unavailable(everything.copy(timerState = false)))
    }

    @Test
    fun trashAndTemplatesStillFollowTheirOwnCapabilities() {
        assertEquals(
            setOf(TrashRoute, TemplatesRoute, PomodoroRoute),
            appTopLevelPages.unavailable(Capabilities.NONE),
        )
        assertEquals(setOf(TemplatesRoute), appTopLevelPages.unavailable(everything.copy(favorites = false)))
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
