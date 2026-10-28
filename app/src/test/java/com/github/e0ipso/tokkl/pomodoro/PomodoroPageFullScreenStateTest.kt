package com.github.e0ipso.tokkl.pomodoro

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroPageFullScreenStateTest {
    private val f = PageFixture()

    @After fun stop() = f.close()

    @Test fun permissionResumeRefreshKeepsExactAlarmDenialIndependent() {
        f.page.permissionsChanged(PomodoroPermissions(notifications = true, exactAlarm = false, fullScreen = false))
        assertTrue(f.page.state.value.fullScreenDenied)
        assertTrue(f.page.state.value.alarmDenied)
        f.page.fullScreenPermissionChanged(true)
        assertFalse(f.page.state.value.fullScreenDenied)
        assertTrue(f.page.state.value.alarmDenied)
        assertFalse(f.page.state.value.busy)
    }
}
