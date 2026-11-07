package com.github.e0ipso.tokkl.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroPermissionsTest {
    @Test fun sdkThresholdsQueryOnlyAvailablePlatformGrants() {
        for (sdk in listOf(31, 32, 33, 34, 36)) {
            val queried = mutableListOf<String>()
            val grants = pomodoroPermissions(sdk,
                notificationRuntime = { queried += "post"; true },
                notificationsEnabled = { queried += "enabled"; true },
                exactAlarm = { queried += "exact"; false },
                fullScreen = { queried += "full"; false },
            )
            assertTrue(grants.notifications)
            assertFalse(grants.exactAlarm)
            assertEquals(sdk < 34, grants.fullScreen)
            assertEquals(buildList {
                if (sdk >= 33) add("post")
                add("enabled")
                add("exact")
                if (sdk >= 34) add("full")
            }, queried)
        }
    }

    @Test fun allGrantedAndNotificationSettingsDisabledPathsUseActualGrants() {
        for (sdk in listOf(31, 32, 33, 34, 36)) {
            assertEquals(PomodoroPermissions(true, true, true), pomodoroPermissions(sdk, { true }, { true }, { true }, { true }))
            assertFalse(pomodoroPermissions(sdk, { true }, { false }, { true }, { true }).notifications)
            if (sdk >= 33) assertFalse(pomodoroPermissions(sdk, { false }, { true }, { true }, { true }).notifications)
        }
    }
}
