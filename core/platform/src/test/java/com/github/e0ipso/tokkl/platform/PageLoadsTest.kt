package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

/** When a page's shared state tells it to load, as its screen comes and goes. */
class PageLoadsTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private val app = TestApp(scheduler)
    private val calls = mutableListOf<String>()
    private val page = MutableStateFlow(0).stateInPage(
        scope,
        initial = 0,
        app.app,
        object : PageLoads {
            override fun shown() {
                calls += "shown"
            }

            override fun returned() {
                calls += "returned"
            }

            override fun reconnected() {
                calls += "reconnected"
            }

            override fun left() {
                calls += "left"
            }
        },
    )
    private val screen = TestScreen(page, scope, scheduler)

    @Test
    fun aRotationLoadsNothing() {
        screen.show()
        screen.hide()
        screen.show()

        assertEquals(listOf("shown"), calls)
    }

    @Test
    fun aShortTripLoadsOnTheWayBack() {
        screen.show()
        screen.hide()
        app.trip()
        screen.show()

        assertEquals(listOf("shown", "returned"), calls)
    }

    @Test
    fun aLongTripLoadsWhenTheScreenReadsAgainWithoutLeaving() {
        screen.show()
        app.foreground.value = false
        screen.leave()
        app.foreground.value = true
        screen.show()

        assertEquals(listOf("shown", "shown"), calls)
    }

    @Test
    fun aPageThatWentLeavesAndLoadsWhenItShowsAgain() {
        screen.show()
        screen.leave()
        screen.show()

        assertEquals(listOf("shown", "left", "shown"), calls)
    }

    @Test
    fun theNetworksReturnReachesOnlyAPageOnScreen() {
        screen.show()
        app.reconnect()
        screen.leave()
        app.reconnect()

        assertEquals(listOf("shown", "reconnected", "left"), calls)
    }
}
