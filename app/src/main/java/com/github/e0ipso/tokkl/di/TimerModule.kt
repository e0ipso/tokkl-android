package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.platform.DefaultDispatcher
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.ElapsedTimerSyncClock
import com.github.e0ipso.tokkl.timerstate.HeldFieldsMemory
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import com.github.e0ipso.tokkl.timerstate.TimerEventConnections
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import com.github.e0ipso.tokkl.timerstate.TimerSync
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.timerstate.TimerSyncService
import com.github.e0ipso.tokkl.timerstate.TimerSyncServiceSlot
import com.github.e0ipso.tokkl.widget.PomodoroWidgetFieldsStore
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
    single { PomodoroWidgetFieldsStore(androidContext()) } binds arrayOf(HeldFieldsMemory::class, AccountDataOwner::class)

    single { AccountTimerStates(get(), get(), get(), get()) } bind AccountDataOwner::class
    single { HeldTimerFlows(get(), get(), io()) }
    single { TimerControlsSource(get(), get(), get(), io()) }

    /**
     * Pause, Resume and Discard, and a Pomodoro session's Start, Next phase and End, sent to the
     * server at once and never queued. Each answer, refusals included, goes to the account's
     * timer state.
     */
    single { TimerActions(client = get<Session>(), source = get<AccountTimerStates>()::source, io = io()) } binds arrayOf(PauseActions::class, SessionActions::class)

    /**
     * The Pause, Resume and Stop of the running notification, the widget, the Quick Settings tile
     * and its long press, and the reminder's Pause, checked against the timer controls at each
     * tap. Stop of a running entry goes to the [ChangeQueue]; the rest go to the server through
     * [TimerActions].
     */
    single {
        TimerSurfaceActions(
            controls = get<TimerControlsSource>()::current,
            account = get<AccountTimerStates>()::account,
            changes = get<ChangeQueue>(),
            server = get<TimerActions>(),
            sendQueued = get<ChangeSender>()::sendAndWait,
        )
    }

    single { TimerSyncServiceSlot() }

    /**
     * The one owner of the timer event stream, which [TimerSyncService] runs for the account's
     * timer state, with its calls from [TimerEventConnections].
     *
     * It keeps a scope of its own on [DefaultDispatcher], not the application scope on the main
     * thread, for its reconnect delays.
     */
    single {
        val scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(DefaultDispatcher))
        val connections = TimerEventConnections(get(), get(), get(), get(), scope, io())
        TimerSync<TimerStateSource>(ElapsedTimerSyncClock(scope), connections::open, connections::stopped)
    }

    single { TimerSyncFollower(androidContext(), get(), get(), get(), get(), get(), get(), get()) } bind AccountDataOwner::class
}
