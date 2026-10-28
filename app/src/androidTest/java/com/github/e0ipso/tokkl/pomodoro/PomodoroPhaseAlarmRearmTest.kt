package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A boot broadcast arms the held phase's exact alarm again from the stored timer state, with no
 * connection to the server. The phase stays where it was.
 */
class PomodoroPhaseAlarmRearmTest {

    @get:Rule val session = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as TokklApplication
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    @Test
    fun bootBroadcastArmsTheHeldPhaseAgainWithoutTheServer() {
        HeldPhases.learnTheTimerApi()
        val latch = CountDownLatch(1)
        PomodoroAlarmRearmReceiver.completed = latch
        try {
            // Twenty minutes of the work phase are left.
            val held = HeldPhases.hold(PhaseTransition.WORK_TO_SHORT_REST, overtime = Duration.ofMinutes(-20), length = Duration.ofMinutes(25))
            val end = checkNotNull(held.ring()).end
            // The held state arms the alarm; take it away as a reboot does.
            awaitNextAlarm(end.toEpochMilli())
            alarms.cancel(checkNotNull((app.pomodoroAlarms as AlarmClockPomodoroScheduler).operation(create = true)))
            awaitNextAlarm(null)
            AirplaneMode.set(true, TokklTestServer.url)

            PomodoroAlarmRearmReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

            assertTrue("the boot re-arm did not finish", latch.await(5, TimeUnit.SECONDS))
            val next = checkNotNull(alarms.nextAlarmClock) { "boot did not schedule the phase alarm" }
            assertEquals(end.toEpochMilli(), next.triggerTime)
            assertEquals(held, app.timerState.state.value)
        } finally {
            PomodoroAlarmRearmReceiver.completed = null
            try {
                AirplaneMode.set(false, TokklTestServer.url)
            } finally {
                HeldPhases.holdNoSession()
                SessionRule.signOut()
            }
        }
        // Without a session, the alarm is gone again.
        awaitNextAlarm(null)
    }

    private fun awaitNextAlarm(triggerTime: Long?) {
        val deadline = System.currentTimeMillis() + 5_000
        while (alarms.nextAlarmClock?.triggerTime != triggerTime && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(triggerTime, alarms.nextAlarmClock?.triggerTime)
    }
}
