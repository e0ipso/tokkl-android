package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.navigation.Route
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The running dots on the bar's items, as the features' badges say. */
class RunningPagesTest {

    private data object Other : Route {
        override val contentKey: String get() = "Other"
    }

    @Test
    fun aPageCarriesItsDotWithItsLabelWhileItsBadgeSaysSo() = runBlocking {
        val shown = runningPages(
            listOf(
                PageBadge(Route.Timer, label = 1, shown = flowOf(false, true)),
                PageBadge(Other, label = 2, shown = flowOf(true, false)),
            ),
        ).toList()

        assertEquals("no dot before the badges are read", emptyMap<Route, Int>(), shown.first())
        assertEquals("each page as its own badge last said", mapOf(Route.Timer to 1), shown.last())
    }

    @Test
    fun aBadgeNotReadYetShowsNoDot() = runBlocking {
        assertEquals(
            listOf(emptyMap<Route, Int>()),
            runningPages(listOf(PageBadge(Route.Timer, label = 1, shown = emptyFlow()))).toList(),
        )
    }
}
