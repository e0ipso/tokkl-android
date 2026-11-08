package com.github.e0ipso.tokkl.di

import android.os.SystemClock
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.platform.DefaultDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.DiscardNotices
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerEventConnections
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerSync
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The server's timer state, its actions and event stream, and the controls every timer surface shows. */
internal val timerModule = module {
    single { TimerStateStore(androidContext()) } bind AccountDataOwner::class

    single {
        AccountTimerStates(
            store = get(),
            lastFields = get(),
            cache = get(),
            database = get(),
            capabilities = get(),
            refreshes = get(),
            lifetime = get(),
            clock = get(),
            io = io(),
        )
    } binds arrayOf(AccountDataOwner::class, DiscardNotices::class)

    // Pause, Resume and Discard, and a Pomodoro session's Start, Next phase and End, sent to the
    // server at once and never queued. Each answer, refusals included, goes to the account's
    // timer state.
    single {
        TimerActions(
            client = get<Session>(),
            states = get<AccountTimerStates>()::source,
            io = io(),
        )
    } binds arrayOf(PauseActions::class, SessionActions::class)

    // The Pause, Resume and Stop of the running notification, the widget, the Quick Settings tile
    // and its long press, and the reminder's Pause, checked against the timer controls at each
    // tap. Stop of a running entry goes to the [ChangeQueue]; the rest go to the server through
    // [TimerActions].
    single {
        TimerSurfaceActions(
            controls = get<AccountTimerStates>()::currentControls,
            account = get<AccountTimerStates>()::account,
            changes = get<ChangeQueue>(),
            server = get<TimerActions>(),
            sendQueued = get<ChangeSender>()::sendQueued,
        )
    }

    // The one owner of the timer event stream, which [TimerSyncService] runs for the account's
    // timer state, with its calls from [TimerEventConnections].
    //
    // Its state changes one at a time on a view of [DefaultDispatcher] that runs one task at a
    // time, not on the main thread, with its reconnect delays. Its clock is elapsedRealtime,
    // which counts deep sleep too, so a stream that went quiet while the phone slept counts as
    // silent for all of it once the phone wakes.
    single {
        val default = get<CoroutineDispatcher>(DefaultDispatcher)
        val connections = TimerEventConnections(
            session = get(),
            timerStates = get(),
            sender = get(),
            scope = CoroutineScope(SupervisorJob() + default),
            io = io(),
        )
        val oneAtATime = CoroutineScope(SupervisorJob() + default.limitedParallelism(1))
        TimerSync(oneAtATime, SystemClock::elapsedRealtime, connections)
    }

    single {
        TimerSyncFollower(
            context = androidContext(),
            session = get(),
            timerStates = get(),
            timerSync = get(),
            timerApi = get<CapabilitiesSource>()::offersTimerApi,
            sender = get(),
            syncWatcher = get(),
        )
    } bind AccountDataOwner::class
    // Every start of MainActivity, a rotation's too, starts the timer sync service when it should
    // run, since the app is on screen and the platform lets it start, also offline.
    single(named("hook-timer-sync")) {
        val follower = inject<TimerSyncFollower>()
        AppHook(AppHookOrder.TIMER_SYNC, foregroundStart = { follower.value.follow() })
    }
}
