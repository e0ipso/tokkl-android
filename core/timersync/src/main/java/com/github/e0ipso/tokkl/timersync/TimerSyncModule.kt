package com.github.e0ipso.tokkl.timersync

import android.os.SystemClock
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.DefaultDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The timer event stream, its service and what decides whether they run, and the sign-out of the
 * capabilities, which the decision follows.
 */
val timerSyncModule = module {
    // The calls of the timer event stream, which feed the account's timer state.
    single {
        TimerEventConnections(
            session = get(),
            timerStates = get(),
            sender = get(),
            follower = inject(),
            scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(DefaultDispatcher)),
            io = io(),
        )
    } bind TimerSyncConnections::class

    // The one owner of the timer event stream, which TimerSyncService runs for the account's
    // timer state, with its calls from [TimerEventConnections].
    //
    // Its state changes one at a time on a view of [DefaultDispatcher] that runs one task at a
    // time, not on the main thread, with its reconnect delays. Its clock is elapsedRealtime,
    // which counts deep sleep too, so a stream that went quiet while the phone slept counts as
    // silent for all of it once the phone wakes.
    single {
        val oneAtATime = get<CoroutineDispatcher>(DefaultDispatcher).limitedParallelism(1)
        TimerSync(
            scope = CoroutineScope(SupervisorJob() + oneAtATime),
            now = SystemClock::elapsedRealtime,
            connections = get<TimerEventConnections>(),
        )
    }

    single {
        val context = androidContext()
        val sender = get<ChangeSender>()
        TimerSyncFollower(
            startService = { TimerSyncService.start(context) },
            signedIn = get<Session>()::isSignedIn,
            source = get<AccountTimerStates>()::source,
            timerSync = get(),
            timerApi = get<CapabilitiesSource>()::offersTimerApi,
            signInNeeded = { sender.signInNeeded.value },
            syncWatcher = get(),
        )
    } bind AccountDataOwner::class
    // Every start of MainActivity, a rotation's too, starts the timer sync service when it should
    // run, since the app is on screen and the platform lets it start, also offline.
    single { TimerSyncHook(inject()) } bind AppHook::class

    single { CapabilitiesSignOut(get(), get(), get()) } bind AccountDataOwner::class
}
