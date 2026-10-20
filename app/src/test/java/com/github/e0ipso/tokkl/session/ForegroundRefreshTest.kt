package com.github.e0ipso.tokkl.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app refreshes in the foreground: MainActivity reports each start and stop, and each
 * change of the default network while started. ForegroundRefreshServerTest checks the same on
 * the emulator against the real server.
 */
class ForegroundRefreshTest {

    private var refreshes = 0
    private var reconnects = 0
    private val foreground = ForegroundRefresh(refresh = { refreshes++ }, refreshOnReconnect = { refreshes++; reconnects++ })

    @Test
    fun eachTripToTheForegroundRefreshesOnce() {
        foreground.started(online = true)
        assertEquals("opening refreshes", 1, refreshes)

        foreground.stopped(changingConfigurations = false)
        foreground.started(online = true)
        assertEquals("coming back refreshes", 2, refreshes)
    }

    @Test
    fun aRotationRefreshesNothing() {
        foreground.started(online = true)
        foreground.stopped(changingConfigurations = true)
        foreground.started(online = true)
        assertEquals(1, refreshes)

        foreground.stopped(changingConfigurations = false)
        foreground.started(online = true)
        assertEquals("the trip after the rotation refreshes", 2, refreshes)
    }

    @Test
    fun theNetworksReturnRefreshesOnce() {
        foreground.started(online = true)
        assertFalse("still online", foreground.networkChanged(online = true))
        assertFalse("going offline asks nothing", foreground.networkChanged(online = false))
        assertFalse(foreground.networkChanged(online = false))
        assertEquals(1, refreshes)

        assertTrue("back online", foreground.networkChanged(online = true))
        assertFalse("a second report of the same network", foreground.networkChanged(online = true))
        assertEquals(2, refreshes)
        assertEquals("the return refreshes as a reconnect", 1, reconnects)
    }

    @Test
    fun anOfflineStartRefreshesAgainWhenTheNetworkComesBack() {
        foreground.started(online = false)
        assertTrue(foreground.networkChanged(online = true))
        assertEquals("the start's refresh failed offline, so the return refreshes", 2, refreshes)
    }

    @Test
    fun aNetworkReportWhileStoppedRefreshesNothing() {
        foreground.started(online = false)
        foreground.stopped(changingConfigurations = false)

        assertFalse(foreground.networkChanged(online = true))
        assertEquals(1, refreshes)
        foreground.started(online = true)
        assertEquals("the start refreshes, once", 2, refreshes)
    }
}
