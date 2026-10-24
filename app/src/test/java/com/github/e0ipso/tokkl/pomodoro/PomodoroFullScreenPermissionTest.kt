package com.github.e0ipso.tokkl.pomodoro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroFullScreenPermissionTest {
    @Test fun api31Through33NeverCallTheNewPlatformApi() {
        for (sdk in 31..33) assertTrue(fullScreenAllowed(sdk) { error("API 34 only") })
    }

    @Test fun api34Through36UseTheActualGrantIncludingInstallerDenial() {
        for (sdk in 34..36) {
            assertFalse(fullScreenAllowed(sdk) { false })
            assertTrue(fullScreenAllowed(sdk) { true })
        }
    }
}
