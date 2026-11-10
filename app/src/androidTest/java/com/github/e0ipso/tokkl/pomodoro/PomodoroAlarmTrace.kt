package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.timerstate.CurrentTimerState
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timersync.PomodoroAlarmScheduler
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext

/** Observes the real alarm's dependencies from process start. Contains no timer descriptions or credentials. */
internal object PomodoroAlarmTrace {
    private val events = ArrayDeque<String>()

    fun alarm(
        states: CurrentTimerState,
        capabilities: CapabilitiesSource,
        scheduler: PomodoroAlarmScheduler,
        ringing: PomodoroRinging,
        io: CoroutineContext,
        clock: Clock,
    ): PomodoroPhaseAlarm = PomodoroPhaseAlarm(
        timerStates = object : CurrentTimerState by states {
            override fun heldNow(): HeldTimerState? = states.heldNow().also { record("read ${phase(it)}") }
        },
        available = { capabilities.offersTimerApi().also { record("timer API=$it") } },
        alarms = object : PomodoroAlarmScheduler by scheduler {
            override fun schedule(triggerAt: Instant, phase: PomodoroPhaseKey): Boolean {
                record("schedule starts end=$triggerAt phase=$phase denied=${scheduler.permissionDenied}")
                return try {
                    scheduler.schedule(triggerAt, phase).also { record("schedule returns $it") }
                } catch (failure: Exception) {
                    record("schedule throws $failure")
                    throw failure
                }
            }

            override fun cancel() {
                record("cancel starts")
                scheduler.cancel()
                record("cancel returns")
            }
        },
        ringing = ringing,
        io = io,
        clock = clock,
    )

    fun failure(state: HeldTimerState?, capabilities: CapabilitiesSource): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(AlarmManager::class.java)
        val trace = synchronized(events) { events.joinToString("\n") }
        val native = DeviceShell.run("dumpsys alarm").lineSequence()
            .filter { it.contains(context.packageName) || it.contains("POMODORO") }
            .take(MAX_EVENTS).joinToString("\n")
        return "${phase(state)} timer API=${capabilities.state.value.timerState} " +
            "exact=${manager.canScheduleExactAlarms()} native=${manager.nextAlarmClock?.triggerTime}\n" +
            "$trace\nAndroid alarms:\n$native"
    }

    private fun phase(state: HeldTimerState?): String =
        "revision=${state?.revision} phase=${state?.ring()?.phase} end=${state?.ring()?.end}"

    private fun record(event: String) = synchronized(events) {
        if (events.size == MAX_EVENTS) events.removeFirst()
        events.addLast("${Process.myPid()} ${SystemClock.elapsedRealtimeNanos()}: $event")
    }

    private const val MAX_EVENTS = 100
}
