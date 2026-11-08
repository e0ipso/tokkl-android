package com.github.e0ipso.tokkl.widget

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.components.pomodoroAlarm
import com.github.e0ipso.tokkl.components.pomodoroAlarms
import com.github.e0ipso.tokkl.components.pomodoroRinging
import com.github.e0ipso.tokkl.components.timerState
import com.github.e0ipso.tokkl.core.ui.R
import com.github.e0ipso.tokkl.pomodoro.HeldPhases
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarmReceiver
import com.github.e0ipso.tokkl.pomodoro.putPhase
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PomodoroWidgetRedrawTest {
    @get:Rule val session = SessionRule()
    private val fixture = TimerAcceptanceFixture()

    @Before fun prepare() = fixture.prepare()

    @After fun cleanup() = fixture.cleanup()

    @Test fun nativeFallbackRedrawAcceptsOnlyTheHeldPhaseAndNeverStartsRinging() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = fixture.app
        NativeWidget(PomodoroWidgetReceiver::class.java).use { host ->
            val held = HeldPhases.hold(overtime = Duration.ofSeconds(-6))
            host.awaitDescription(context.getString(R.string.pomodoro_remaining))
            runBlocking { app.pomodoroAlarm.follow() }
            app.pomodoroAlarms.cancel()
            host.awaitDescription(context.getString(R.string.pomodoro_overtime), 8_000)
            host.awaitUpdatesSettled()
            assertFalse(app.pomodoroRinging.state.value.serviceRunning)

            // The broadcast the fallback alarm delivers when exact alarms are denied, sent ordered so
            // the result receiver hears when the receiver has finished it.
            fun deliver(phase: PomodoroPhaseKey) {
                val finished = CountDownLatch(1)
                context.sendOrderedBroadcast(
                    Intent(context, PomodoroPhaseAlarmReceiver::class.java)
                        .setAction(PomodoroPhaseAlarmReceiver.ACTION_WIDGET_REDRAW)
                        .putPhase(phase),
                    null,
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = finished.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                    Activity.RESULT_OK,
                    null,
                    null,
                )
                assertTrue("native redraw broadcast finishes", finished.await(10, TimeUnit.SECONDS))
            }
            val before = host.updates.get()
            deliver(PomodoroPhaseKey(HeldPhases.key(held).start.minusSeconds(1)))
            host.awaitUpdatesSettled()
            assertEquals("stale phase cannot redraw", before, host.updates.get())
            deliver(HeldPhases.key(held))
            host.await("matching phase redraws its native widget") { host.updates.get() > before }
            assertEquals("redraw cannot advance or end a session", held, app.timerState.state.value)
            assertFalse("redraw never starts ringing", app.pomodoroRinging.state.value.serviceRunning)
        }
    }
}
