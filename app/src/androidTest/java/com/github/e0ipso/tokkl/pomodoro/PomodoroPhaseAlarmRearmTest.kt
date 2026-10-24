package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.plannedEnd
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A boot broadcast arms the stored phase's exact alarm again. The phase stays where it was.
 */
class PomodoroPhaseAlarmRearmTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as TokklApplication
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    @Test
    fun bootBroadcastSchedulesThePhaseAlarmAgain() {
        var endMillis: Long? = null
        val latch = CountDownLatch(1)
        runBlocking { app.pomodoroSessions.discardRunningSession() }
        PomodoroAlarmRearmReceiver.completed = latch
        try {
            val session = runBlocking {
                app.pomodoroSessions.start(PomodoroSessionConfig(), EntryFields(description = "Focus"), workspaceId = 1)
            }
            val end = plannedEnd(
                SessionPhase(session.phaseKind, session.phaseStartedAt, session.completed),
                PomodoroSessionConfig(),
            )
            endMillis = end.toEpochMilli()
            val operation = checkNotNull((app.pomodoroAlarms as AlarmClockPomodoroScheduler).operation(create = true))
            alarms.cancel(operation)

            PomodoroAlarmRearmReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

            assertTrue("the boot re-arm did not finish", latch.await(5, TimeUnit.SECONDS))
            val next = checkNotNull(alarms.nextAlarmClock) { "boot did not schedule the phase alarm" }
            assertEquals(end.toEpochMilli(), next.triggerTime)
            val kept = checkNotNull(runBlocking { app.pomodoroSessions.current() })
            assertEquals(session.phaseKind, kept.phaseKind)
            assertEquals(session.phaseStartedAt, kept.phaseStartedAt)
            assertEquals(session.completed, kept.completed, 0.0)
        } finally {
            PomodoroAlarmRearmReceiver.completed = null
            runBlocking { app.pomodoroSessions.discardRunningSession() }
            assertNull(runBlocking { app.pomodoroSessions.current() })
            val due = endMillis
            if (due != null) {
                val left = alarms.nextAlarmClock
                assertTrue("the phase alarm was still armed", left == null || left.triggerTime != due)
            }
        }
    }
}
