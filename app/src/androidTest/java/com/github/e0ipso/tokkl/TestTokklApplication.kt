package com.github.e0ipso.tokkl

import android.content.SharedPreferences
import com.github.e0ipso.tokkl.notification.RunningNotificationRestartTrace
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmTrace
import com.github.e0ipso.tokkl.pomodoro.PomodoroRingCheck
import com.github.e0ipso.tokkl.pomodoro.ServicePomodoroRinging
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.time.Clock

/**
 * The app as the instrumented tests run it: [TokklApplication] with a [TestClock] as the app's
 * [Clock]. [TokklTestRunner] starts it in place of the manifest's application in every test
 * process. Until a test sets the clock, the app behaves as it does outside the tests.
 */
class TestTokklApplication : TokklApplication() {

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        RunningNotificationRestartTrace.preferences(name, super.getSharedPreferences(name, mode))

    override fun koinModules(): List<Module> = super.koinModules() + testModule

    private val testModule = module {
        single<Clock> { TestClock() }
        // Same alarm and platform dependencies as production, with a bounded diagnostic wrapper.
        single {
            PomodoroAlarmTrace.alarm(
                states = get(),
                capabilities = get(),
                scheduler = get<AlarmClockPomodoroScheduler>(),
                ringing = get<ServicePomodoroRinging>(),
                io = io(),
                clock = get(),
            )
        } bind PomodoroRingCheck::class
    }
}
