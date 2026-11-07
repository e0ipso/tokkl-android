package com.github.e0ipso.tokkl.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Timer page and the Pomodoro page ask for the notification permission once between them. */
class SharedPermissionQuestionTest {
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
