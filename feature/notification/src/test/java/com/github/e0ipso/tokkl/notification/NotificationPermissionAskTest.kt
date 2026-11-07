package com.github.e0ipso.tokkl.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a timer start made in the app asks for the notification permission (docs/PRD.md N5). */
class NotificationPermissionAskTest {

    private val record = object : NotificationPermissionAsk.AskRecord {
        var asked = false
        var writes = 0

        override fun asked(): Boolean = asked

        override fun markAsked() {
            asked = true
            writes++
        }
    }

    private var granted = false

    private fun ask(sdkInt: Int) = NotificationPermissionAsk(sdkInt, record) { granted }

    @Test
    fun theFirstStartOnAndroid13AndLaterAsksAndLaterStartsDoNot() {
        for (sdkInt in listOf(TIRAMISU, 34, 36)) {
            record.asked = false
            val ask = ask(sdkInt)

            assertTrue("the first start on API $sdkInt asks", ask.atStartInApp())
            ask.requested()
            assertTrue("API $sdkInt records the launched question", record.asked)
            assertFalse("the second start on API $sdkInt does not ask", ask.atStartInApp())
        }
    }

    @Test
    fun theRecordOutlivesTheModel() {
        val first = ask(TIRAMISU)
        assertTrue(first.atStartInApp())
        first.requested()

        assertFalse("a new process reads the same record", ask(TIRAMISU).atStartInApp())
        assertEquals(1, record.writes)
    }

    @Test
    fun aQuestionThatHasNotLaunchedCanStillAskAfterTheScreenReturns() {
        assertTrue(ask(TIRAMISU).atStartInApp())
        assertFalse(record.asked)
        val returned = ask(TIRAMISU)
        assertTrue(returned.atStartInApp())
        returned.requested()
        assertFalse(returned.atStartInApp())
        assertEquals(1, record.writes)
    }

    @Test
    fun belowAndroid13NothingIsAskedOrRecorded() {
        for (sdkInt in listOf(31, 32)) {
            repeat(2) { assertFalse("API $sdkInt asks nothing", ask(sdkInt).atStartInApp()) }
        }
        assertFalse(record.asked)
        assertEquals(0, record.writes)
    }

    @Test
    fun notificationsAlreadyAllowedAskNothingAndCountAsTheQuestion() {
        granted = true

        assertFalse(ask(TIRAMISU).atStartInApp())
        assertTrue("an allow taken back in Settings later is not asked again", record.asked)

        granted = false
        assertFalse(ask(TIRAMISU).atStartInApp())
    }

    private companion object {
        const val TIRAMISU = 33
    }
}
