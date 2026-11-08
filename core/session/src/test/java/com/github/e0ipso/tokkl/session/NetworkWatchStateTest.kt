package com.github.e0ipso.tokkl.session

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkWatchStateTest {
    @Test
    fun initialAndUnchangedReportsAskNothing() {
        val events = mutableListOf<Boolean>()
        val state = NetworkWatchState("wifi", online = true, validated = true, changed = { events += it })

        state.available("wifi")
        repeat(3) { state.capabilities("wifi", online = true, validated = true) }

        assertEquals(emptyList<Boolean>(), events)
    }

    @Test
    fun aRouteThatBecomesValidatedReportsOnlineAgain() {
        val events = mutableListOf<Boolean>()
        val state = NetworkWatchState("mobile", online = true, validated = false, changed = { events += it })

        state.capabilities("mobile", online = true, validated = true)
        state.capabilities("mobile", online = true, validated = true)

        assertEquals(listOf(true), events)
    }

    @Test
    fun aNewDefaultReportsOnlineAndIgnoresTheOldNetworksLateLoss() {
        val events = mutableListOf<Boolean>()
        val state = NetworkWatchState("mobile", online = true, validated = true, changed = { events += it })

        state.available("wifi")
        state.capabilities("wifi", online = true, validated = true)
        state.lost("mobile")
        state.capabilities("mobile", online = false, validated = false)
        assertEquals(listOf(true), events)

        state.lost("wifi")
        assertEquals(listOf(true, false), events)
    }
}
