package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.TimerControlsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.ElapsedTimerSyncClock
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.StreamEnd
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import com.github.e0ipso.tokkl.timerstate.TimerSync
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.timerstate.TimerSyncService
import com.github.e0ipso.tokkl.timerstate.holdTimerEvents
import com.github.e0ipso.tokkl.widget.PomodoroWidgetFieldsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The server's timer state, its actions and event stream, and the controls every timer surface shows. */
internal val timerModule = module {
    single { TimerStateStore(androidContext()) }
    single { PomodoroWidgetFieldsStore(androidContext()) }

    single { AccountTimerStates(get(), get(), inject(), inject()) }
    single { HeldTimerFlows(get(), inject(), io()) }
    single { TimerControlsSource(get(), get(), inject(), io()) }

    /**
     * Pause, Resume and Discard, and a Pomodoro session's Start, Next phase and End, sent to the
     * server at once and never queued. Each answer, refusals included, goes to the account's
     * timer state.
     */
    single {
        val timerStates = get<AccountTimerStates>()
        TimerActions(client = get<Session>()::client, source = { timerStates.source }, io = io())
    }

    /**
     * The Pause, Resume and Stop of the running notification, the widget, the Quick Settings tile
     * and its long press, and the reminder's Pause, checked against the timer controls at each
     * tap. Stop of a running entry goes to the [ChangeQueue]; the rest go to the server through
     * [TimerActions].
     */
    single {
        val controls = inject<TimerControlsSource>()
        val timerStates = get<AccountTimerStates>()
        val sender = inject<ChangeSender>()
        TimerSurfaceActions(
            controls = { controls.value.controls.first() },
            account = { timerStates.current().account },
            changes = get<ChangeQueue>(),
            server = get<TimerActions>(),
            sendQueued = { sender.value.sendAndWait() },
        )
    }

    /**
     * The one owner of the timer event stream, which [TimerSyncService] runs for the account's
     * timer state. Each connection takes the signed-in client and feeds the source it was opened
     * for, so a stream of a retired account changes nothing. A refused credential pauses the queue
     * as the queue's own refusal does, which shows the existing prompt to sign in again, and stops
     * the service, as a stream with no account does.
     *
     * It keeps a scope of its own on [DefaultDispatcher], not the application scope on the main
     * thread, for its reconnect delays.
     */
    single {
        val scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(DefaultDispatcher))
        val io = io()
        val session = get<Session>()
        val timerStates = get<AccountTimerStates>()
        val sender = inject<ChangeSender>()
        val follower = inject<TimerSyncFollower>()
        TimerSync<TimerStateSource>(
            clock = ElapsedTimerSyncClock(scope),
            open = { source, link ->
                scope.launch(io) { link.ended(holdTimerEvents(session.client(), source, link)) }
            },
            stopped = { source, end ->
                if (end == StreamEnd.Refused && source === timerStates.source) sender.value.signInRefused()
                follower.value.stopService()
            },
        )
    }

    single { TimerSyncFollower(androidContext(), get(), get(), inject(), inject(), inject(), get()) }
}
