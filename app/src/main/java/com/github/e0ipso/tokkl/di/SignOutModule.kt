package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.session.SignOutStep
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind

/**
 * The sign-out steps that belong to no single component: calls to cancel, and a step that joins
 * two components. Every other step is a component's own [AccountDataOwner].
 */
internal fun Module.signOutSteps() {
    // The shared client lives in core:api, below the sign-out steps.
    step("http-calls", SignOutPhase.STOP_WORK) {
        val http = get<SharedHttpClient>()
        return@step { http.cancelAll() }
    }
    // Without the capabilities, the alarm clock disables the receiver that starts the timer sync at boot.
    step("capabilities", SignOutPhase.CLEAR_DATA) {
        val capabilities = get<CapabilitiesSource>()
        val store = get<CapabilitiesStore>()
        val alarmClock = get<AlarmClockPomodoroScheduler>()
        return@step {
            capabilities.forget()
            store.clear()
            alarmClock.watchTimerSync()
        }
    }
}

private fun Module.step(name: String, phase: SignOutPhase, step: Scope.() -> suspend () -> Unit) {
    single(named("sign-out-$name")) { SignOutStep(phase, step()) } bind AccountDataOwner::class
}
