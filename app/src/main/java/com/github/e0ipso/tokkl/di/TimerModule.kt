package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.DefaultDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.CurrentHeldState
import com.github.e0ipso.tokkl.timerstate.CurrentTimerControls
import com.github.e0ipso.tokkl.timerstate.DiscardNotices
import com.github.e0ipso.tokkl.timerstate.ElapsedTimerSyncClock
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SignedInAccountName
import com.github.e0ipso.tokkl.timerstate.SignedInTimerState
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import com.github.e0ipso.tokkl.timerstate.TimerEventConnections
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerSync
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.timerstate.TimerSyncServiceSlot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
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
            refreshes = get(),
            lifetime = get(),
            clock = get(),
        )
    } binds arrayOf(
        AccountDataOwner::class,
        SignedInTimerState::class,
        SignedInAccountName::class,
        CurrentHeldState::class,
    )
    single { HeldTimerFlows(get(), get(), io()) } bind DiscardNotices::class
    single {
        TimerControlsSource(database = get(), cache = get(), timerStates = get(), capabilities = get(), io = io())
    } bind CurrentTimerControls::class

    // Pause, Resume and Discard, and a Pomodoro session's Start, Next phase and End, sent to the
    // server at once and never queued. Each answer, refusals included, goes to the account's
    // timer state.
    single {
        TimerActions(
            client = get<Session>(),
            states = get<AccountTimerStates>(),
            io = io(),
        )
    } binds arrayOf(PauseActions::class, SessionActions::class)

    // The Pause, Resume and Stop of the running notification, the widget, the Quick Settings tile
    // and its long press, and the reminder's Pause, checked against the timer controls at each
    // tap. Stop of a running entry goes to the [ChangeQueue]; the rest go to the server through
    // [TimerActions].
    single {
        TimerSurfaceActions(
            controls = get<TimerControlsSource>(),
            account = get<AccountTimerStates>(),
            changes = get<ChangeQueue>(),
            server = get<TimerActions>(),
            sendQueued = get<ChangeSender>(),
        )
    }

    single { TimerSyncServiceSlot() }

    // The one owner of the timer event stream, which [TimerSyncService] runs for the account's
    // timer state, with its calls from [TimerEventConnections].
    //
    // It keeps a scope of its own on [DefaultDispatcher], not the application scope on the main
    // thread, for its reconnect delays.
    single {
        val scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(DefaultDispatcher))
        val connections = TimerEventConnections(
            session = get(),
            timerStates = get(),
            sender = get(),
            service = get(),
            scope = scope,
            io = io(),
        )
        TimerSync(ElapsedTimerSyncClock(scope), connections)
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
            slot = get(),
        )
    } bind AccountDataOwner::class
}
