package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.ui.theme.SectionHues
import org.junit.Assert.assertEquals
import org.junit.Test

/** The order of the pages the features bind, whatever order Koin hands them over in. */
class TopLevelPagesTest {
    private val timer = page(Route.Timer, position = 0)
    private val reports = page(PageRoute, position = 20)
    private val settings = page(OtherPageRoute, position = 80)

    @Test
    fun pagesStandInTheOrderOfTheirPositions() {
        assertEquals(listOf(timer, reports, settings), TopLevelPages(listOf(settings, timer, reports)).all)
    }

    @Test(expected = IllegalArgumentException::class)
    fun twoPagesCannotShareAPosition() {
        TopLevelPages(listOf(timer, page(AboveRoute, position = 0)))
    }

    private fun page(route: Route, position: Int) =
        TopLevelDestination(route, label = 0, icon = 0, hue = SectionHues.timer, position = position)
}
