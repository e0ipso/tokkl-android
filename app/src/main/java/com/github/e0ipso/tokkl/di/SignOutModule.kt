package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.session.SignOutStep
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind

/**
 * The sign-out steps that belong to no single component: calls to cancel, and steps that join
 * two components. Every other step is a component's own [AccountDataOwner].
 */
internal fun Module.signOutSteps() {
    // The shared client lives in core:api, below the sign-out steps.
    step("http-calls", SignOutPhase.STOP_WORK) {
        val http = get<SharedHttpClient>()
        return@step { http.cancelAll() }
    }
    // A new Pomodoro notification only once the cache is empty.
    step("cache", SignOutPhase.CLEAR_CACHE) {
        val cache = get<AccountCache>()
        val notification = get<AccountPomodoroNotification>()
        return@step {
            cache.clear()
            notification.renew()
        }
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
