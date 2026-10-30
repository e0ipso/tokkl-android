package com.github.e0ipso.tokkl.widget

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.*
import com.github.e0ipso.tokkl.pomodoro.*
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PomodoroWidgetRedrawTest {
    @get:Rule val session = SessionRule()
    private val fixture = TimerAcceptanceFixture()
    @Before fun prepare() = fixture.prepare()
    @After fun cleanup() = fixture.cleanup()

    @Test fun nativeFallbackRedrawAcceptsOnlyTheHeldPhaseAndNeverStartsRinging() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = fixture.app
        val scheduler = AlarmClockPomodoroScheduler(context, exactAlarmsAllowed = { false })
        NativeWidget(PomodoroWidgetReceiver::class.java).use { host ->
            val held = HeldPhases.hold(overtime = Duration.ofSeconds(-6))
            host.awaitDescription(context.getString(R.string.pomodoro_remaining))
            runBlocking { app.pomodoroAlarm.follow() }
            app.pomodoroAlarms.cancel()
            host.awaitDescription(context.getString(R.string.pomodoro_overtime), 8_000)
            host.awaitUpdatesSettled()
            assertFalse(app.pomodoroRinging.state.value.serviceRunning)

            fun deliver(phase: PomodoroPhaseKey) {
                val finished = CountDownLatch(1)
                scheduler.operation(create = true, phase, redraw = true)!!.send(context, 0, null,
                    PendingIntent.OnFinished { _, _, _, _, _ -> finished.countDown() }, Handler(Looper.getMainLooper()))
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
            scheduler.cancel()
        }
    }
}
