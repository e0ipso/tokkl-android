package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.widget.*
import java.time.Instant
import org.junit.*
import org.junit.Assert.*

/** API36 cannot revoke USE_EXACT_ALARM: inject denial into the production native scheduler. */
class PomodoroDeniedAlarmWidgetTest {
    @get:Rule val session = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val manager = context.getSystemService(AlarmManager::class.java)

    @Test fun deniedExactAccessArmsOneNativeRedrawAndExactReplacementAndCancelRemoveIt() {
        var allowed = false
        val scheduler = AlarmClockPomodoroScheduler(context, exactAlarmsAllowed = { allowed })
        val start = Instant.now()
        try {
            app.pomodoroAlarms.cancel()
            assertTrue(scheduler.permissionDenied)
            val phase = PomodoroPhaseKey(start)
            assertFalse(scheduler.schedule(start.plusSeconds(600), phase))
            assertNull("denial cannot arm a ringing alarm", manager.nextAlarmClock)
            assertEquals("one native inexact redraw", 1, pendingRedraws())
            assertFalse(scheduler.schedule(start.plusSeconds(700), PomodoroPhaseKey(start.plusSeconds(1))))
            assertEquals("replacement does not accumulate alarms", 1, pendingRedraws())
            allowed = true
            assertTrue(scheduler.schedule(start.plusSeconds(800), phase))
            assertEquals(start.plusSeconds(800).toEpochMilli(), manager.nextAlarmClock!!.triggerTime)
            assertEquals("exact replacement removes the fallback", 0, pendingRedraws())
            allowed = false
            assertFalse(scheduler.schedule(start.plusSeconds(900), phase))
            assertNull(manager.nextAlarmClock)
            assertEquals(1, pendingRedraws())
            scheduler.cancel()
            assertEquals("idle cancellation removes the one-shot", 0, pendingRedraws())
            assertFalse(app.pomodoroRinging.state.value.serviceRunning)
        } finally { scheduler.cancel() }
    }

    private fun pendingRedraws(): Int {
        val dump = DeviceShell.run("dumpsys alarm")
        // Only pending alarm batches, excluding delivery history and per-package statistics.
        val pending = dump.substringBefore("Past-due non-wakeup alarms:").substringBefore("Past-due non-wakeup alarms")
        return pending.lineSequence().count { it.contains("tag=*") && it.contains(PomodoroPhaseAlarmReceiver.ACTION_WIDGET_REDRAW) }
    }
}
