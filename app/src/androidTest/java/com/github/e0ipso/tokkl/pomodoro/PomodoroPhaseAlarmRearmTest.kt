package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.components.pomodoroAlarmRearm
import com.github.e0ipso.tokkl.components.pomodoroAlarms
import com.github.e0ipso.tokkl.components.timerState
import com.github.e0ipso.tokkl.model.PhaseTransition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * After boot, the held phase's exact alarm is armed again from the stored timer state, with no
 * connection to the server. The phase stays where it was. Only the system sends BOOT_COMPLETED, so
 * the test hands it to [PomodoroAlarmRearm] as the receiver does.
 */
class PomodoroPhaseAlarmRearmTest {

    @get:Rule val session = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as TokklApplication
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    @Test
    fun bootBroadcastArmsTheHeldPhaseAgainWithoutTheServer() {
        HeldPhases.learnTheTimerApi()
        try {
            // Twenty minutes of the work phase are left.
            val held = HeldPhases.hold(
                PhaseTransition.WORK_TO_SHORT_REST,
                overtime = Duration.ofMinutes(-20),
                length = Duration.ofMinutes(25),
            )
            val end = checkNotNull(held.ring()).end
            // The held state arms the alarm; take it away as a reboot does.
            awaitNextAlarm(end.toEpochMilli())
            app.pomodoroAlarms.cancel()
            awaitNextAlarm(null)
            AirplaneMode.set(true, TokklTestServer.url)

            runBlocking { app.pomodoroAlarmRearm.rearm(Intent.ACTION_BOOT_COMPLETED) }

            val next = checkNotNull(alarms.nextAlarmClock) { "boot did not schedule the phase alarm" }
            assertEquals(end.toEpochMilli(), next.triggerTime)
            assertEquals(held, app.timerState.state.value)
        } finally {
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
        while (alarms.nextAlarmClock?.triggerTime != triggerTime && System.currentTimeMillis() < deadline) {
            Thread.sleep(
                50,
            )
        }
        assertEquals(triggerTime, alarms.nextAlarmClock?.triggerTime)
    }
}
