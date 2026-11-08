package com.github.e0ipso.tokkl.notification

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

/** The Timer page and the Pomodoro page ask for the notification permission once between them. */
class SharedPermissionQuestionTest {
    @Test fun timerAndPomodoroShareOneDurableQuestionIncludingAfterRecreationAndRegrant() = runTest {
        for (sdk in listOf(33, 34, 36)) {
            val disk = Record()
            var granted = false
            val pomodoro = NotificationPermissionAsk(sdk, disk, EmptyCoroutineContext) { granted }
            assertTrue(pomodoro.atStartInApp())
            pomodoro.requested()
            val recreatedTimer = NotificationPermissionAsk(sdk, disk, EmptyCoroutineContext) { granted }
            assertFalse(recreatedTimer.atStartInApp())
            granted = true
            assertFalse(pomodoro.atStartInApp())
            granted = false
            assertFalse(NotificationPermissionAsk(sdk, disk, EmptyCoroutineContext) { granted }.atStartInApp())
            assertEquals(1, disk.writes)
        }
    }

    @Test fun timerFirstAndAlreadyGrantedStartsConsumeTheSharedQuestion() = runTest {
        for (initiallyGranted in listOf(false, true)) {
            val disk = Record()
            val timer = NotificationPermissionAsk(33, disk, EmptyCoroutineContext) { initiallyGranted }
            if (timer.atStartInApp()) timer.requested()
            assertFalse(NotificationPermissionAsk(33, disk, EmptyCoroutineContext) { false }.atStartInApp())
            assertEquals(1, disk.writes)
        }
    }

    private class Record : NotificationPermissionAsk.AskRecord {
        var value = false
        var writes = 0
        override suspend fun asked() = value
        override suspend fun markAsked() {
            value = true
            writes++
        }
    }
}
