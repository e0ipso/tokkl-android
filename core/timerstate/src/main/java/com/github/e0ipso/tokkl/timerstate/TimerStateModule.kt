package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The server's timer state, its actions, and the controls every timer surface shows. */
val timerStateModule = module {
    single { TimerStateStore(androidContext(), io()) } bind AccountDataOwner::class

    single {
        AccountTimerStates(
            store = get(),
            lastFields = get(),
            entriesOrQueueChanged = get<AccountCache>().entriesOrQueueChanged,
            entries = get(),
            capabilities = get<CapabilitiesSource>().loaded,
            refreshes = get(),
            account = get(),
            clock = get(),
            io = io(),
        )
    } binds arrayOf(
        AccountDataOwner::class,
        DiscardNotices::class,
        CurrentTimerState::class,
    )

    // Pause, Resume and Discard, and a Pomodoro session's Start, Next phase and End, sent to the
    // server at once and never queued. Each answer, refusals included, goes to the account's
    // timer state.
    single {
        TimerActions(
            client = get<Session>(),
            states = get<AccountTimerStates>()::intake,
            scope = get(),
            io = io(),
        )
    } binds arrayOf(PauseActions::class, SessionActions::class)

    // The Pause, Resume and Stop of the running notification, the widget, the Quick Settings tile
    // and its long press, and the reminder's Pause, checked against the timer controls at each
    // tap. Stop of a running entry goes to the [ChangeQueue]; the rest go to the server through
    // [TimerActions].
    single {
        TimerSurfaceActions(
            state = get(),
            changes = get<ChangeQueue>(),
            server = get<TimerActions>(),
            sender = get<ChangeSender>()::sendQueued,
        )
    }
}
