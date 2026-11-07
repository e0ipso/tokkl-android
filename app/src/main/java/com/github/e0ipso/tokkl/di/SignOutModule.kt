package com.github.e0ipso.tokkl.di

import android.app.NotificationManager
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotifier
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutOrder
import com.github.e0ipso.tokkl.session.SignOutStep
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind

/**
 * The sign-out steps that belong to no single component: work and calls to cancel, and steps
 * that join two components. Every other step is a component's own [AccountDataOwner].
 */
internal fun Module.signOutSteps() {
    step(SignOutOrder.POMODORO_NOTIFICATION_ID) {
        val context = androidContext()
        return@step {
            context.getSystemService(
                NotificationManager::class.java,
            ).cancel(PomodoroSessionNotifier.NOTIFICATION_ID)
        }
    }
    step(SignOutOrder.BACKGROUND_SEND) {
        val background = get<BackgroundSend>()
        return@step { background.cancel() }
    }
    step(SignOutOrder.RUNNING_CHECK) {
        val check = get<RunningCheck>()
        return@step { check.cancelAndWait() }
    }
    step(SignOutOrder.HTTP_CALLS) {
        val http = get<SharedHttpClient>()
        return@step { http.cancelAll() }
    }
    // A new Pomodoro notification only once the cache is empty.
    step(SignOutOrder.CACHE) {
        val cache = get<AccountCache>()
        val notification = get<AccountPomodoroNotification>()
        return@step {
            cache.clear()
            notification.renew()
        }
    }
    // Without the capabilities, the alarm clock disables the receiver that starts the timer sync at boot.
    step(SignOutOrder.CAPABILITIES) {
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

private fun Module.step(order: SignOutOrder, step: Scope.() -> suspend () -> Unit) {
    single(named(order)) { SignOutStep(order, step()) } bind AccountDataOwner::class
}
