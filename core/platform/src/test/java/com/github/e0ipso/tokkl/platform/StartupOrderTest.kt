package com.github.e0ipso.tokkl.platform

import org.junit.Assert.assertEquals
import org.junit.Test

/** How the hooks the modules bind come into the order they run in. */
class StartupOrderTest {

    private class Hook(override val phase: StartupPhase) : AppHook

    @Test
    fun hooksRunInTheOrderOfTheirPhasesWhateverOrderTheGraphGivesThem() {
        val sync = Hook(StartupPhase.TIMER_SYNC)
        val refreshes = Hook(StartupPhase.FORWARD_REFRESHES)
        val widget = Hook(StartupPhase.TIMER_WIDGET)

        assertEquals(listOf(refreshes, widget, sync), listOf(sync, refreshes, widget).inStartupOrder())
    }

    @Test(expected = IllegalArgumentException::class)
    fun twoHooksInOnePhaseAreRefusedSinceNothingWouldOrderThem() {
        listOf(Hook(StartupPhase.REMINDER), Hook(StartupPhase.REMINDER)).inStartupOrder()
    }
}
