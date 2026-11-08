package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.session.AppEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a page that shows what it loads from the server loads it: once on opening, and again after
 * a trip to the background, as the catalog pages, the trash and the reports share it.
 */
class PageReloadsTest {

    private val loads = mutableListOf<String>()
    private val reloads = PageReloads(open = { loads += "open" }, reload = { loads += "reload" })

    @Test
    fun openingLoadsOnceAndAgainOnlyAfterItCloses() {
        reloads.open()
        reloads.open()
        assertEquals("a rotation loads nothing", listOf("open"), loads)

        reloads.close()
        assertFalse(reloads.isOpen)
        reloads.open()
        assertEquals(listOf("open", "open"), loads)
    }

    @Test
    fun aStartAfterAStopReloadsOnce() {
        reloads.open()
        reloads.cameToForeground()
        assertEquals("the start replayed on opening loads nothing", listOf("open"), loads)

        reloads.wentToBackground()
        reloads.cameToForeground()
        reloads.cameToForeground()
        assertEquals(listOf("open", "reload"), loads)
    }

    @Test
    fun aStopWhileClosedOrForgottenByClosingReloadsNothing() {
        reloads.wentToBackground()
        reloads.open()
        reloads.cameToForeground()
        reloads.wentToBackground()
        reloads.close()
        reloads.cameToForeground()

        assertEquals(listOf("open"), loads)
    }

    @Test
    fun retryReloadsOnlyWhileOpen() {
        reloads.retry()
        reloads.open()
        reloads.retry()

        assertEquals(listOf("open", "reload"), loads)
    }

    @Test
    fun openingWithItsOwnLoadLoadsNothingAndForgetsAStop() {
        reloads.wentToBackground()
        reloads.openLoading()
        reloads.open()
        reloads.cameToForeground()

        assertTrue(reloads.isOpen)
        assertEquals(emptyList<String>(), loads)
    }

    @Test
    fun theAppsTripsReachThePageAndTheNetworkDoesNot() {
        val heard = mutableListOf<String>()
        val page = object : PageEvents {
            override fun open() = Unit
            override fun close() = Unit
            override fun reconnected() {
                heard += "reconnected"
            }
            override fun wentToBackground() {
                heard += "background"
            }
            override fun cameToForeground() {
                heard += "foreground"
            }
        }
        val events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        page.followTrips(events, scope)

        AppEvent.entries.forEach { events.tryEmit(it) }
        scope.cancel()

        assertEquals(listOf("foreground", "background"), heard)
    }
}
