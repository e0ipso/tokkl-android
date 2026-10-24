package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
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

    @Test fun timerAndPomodoroShareOneDurableQuestionIncludingAfterRecreationAndRegrant() {
        for (sdk in listOf(33, 34, 36)) {
            val disk = Record()
            var granted = false
            val pomodoro = NotificationPermissionAsk(sdk, disk) { granted }
            assertTrue(pomodoro.atStartInApp())
            pomodoro.requested()
            val recreatedTimer = NotificationPermissionAsk(sdk, disk) { granted }
            assertFalse(recreatedTimer.atStartInApp())
            granted = true
            assertFalse(pomodoro.atStartInApp())
            granted = false
            assertFalse(NotificationPermissionAsk(sdk, disk) { granted }.atStartInApp())
            assertEquals(1, disk.writes)
        }
    }

    @Test fun timerFirstAndAlreadyGrantedStartsConsumeTheSharedQuestion() {
        for (initiallyGranted in listOf(false, true)) {
            val disk = Record()
            val timer = NotificationPermissionAsk(33, disk) { initiallyGranted }
            if (timer.atStartInApp()) timer.requested()
            assertFalse(NotificationPermissionAsk(33, disk) { false }.atStartInApp())
            assertEquals(1, disk.writes)
        }
    }

    private class Record : NotificationPermissionAsk.AskRecord {
        var value = false
        var writes = 0
        override fun asked() = value
        override fun markAsked() { value = true; writes++ }
    }
}
