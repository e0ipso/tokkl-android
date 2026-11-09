package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.DefaultDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
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
    single<MonotonicClock> { ElapsedRealtimeClock }
    single {
        val oneAtATime = get<CoroutineDispatcher>(DefaultDispatcher).limitedParallelism(1)
        TimerSync(
            scope = CoroutineScope(SupervisorJob() + oneAtATime),
            now = get(),
            connections = get<TimerEventConnections>(),
        )
    }

    single<ServiceStart> { TimerSyncServiceStart(androidContext()) }
    // A refused credential on the stream pauses the queue, as the queue's own refusal does.
    single<SignInRefusal> { PauseQueueOnRefusal(get()) }
    single {
        TimerSyncFollower(
            startService = get(),
            signedIn = get(),
            timerState = get(),
            timerSync = get(),
            timerApi = get(),
            signInNeeded = get<ChangeSender>().signInNeeded,
            syncWatcher = get(),
            refusal = get(),
            scope = get<CoroutineScope>() + io(),
        )
    } bind AccountDataOwner::class
    // Every start of MainActivity, a rotation's too, starts the timer sync service when it should
    // run, since the app is on screen and the platform lets it start, also offline.
    single { TimerSyncHook(inject()) } bind AppHook::class

    single { CapabilitiesSignOut(get(), get(), get()) } bind AccountDataOwner::class
}
