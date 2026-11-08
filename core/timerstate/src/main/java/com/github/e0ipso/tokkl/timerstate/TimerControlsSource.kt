package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/**
 * What every timer surface shows and acts on (docs/PRD.md T1, N1, N3, N4, N7): the Timer page,
 * the full-screen timer, the running notification, the widget, the Quick Settings tile and the
 * reminder. It is the cache's running entry, with its queued Starts, Stops and edits, and the
 * pause chain of the server's timer state in [timerStates], as [serverTimerControls] puts them
 * together, while the server offers the timer API, with targets that name the account. Each
 * value reads the cache and the held state under the holder's lock, so a state never shows
 * apart from the running entry it wrote to the cache. Reads the disk off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerControlsSource(
    database: CacheDatabase,
    timerStates: AccountTimerStates,
    capabilities: CapabilitiesSource,
    io: CoroutineContext,
) {

    val controls: Flow<TimerControls> = run {
        val dao = database.changes()
        val heldStates = timerStates.states.flatMapLatest { it.source.state }
        combine(
            database.invalidationTracker.createFlow("time_entries", "pending_changes"),
            heldStates,
            flow { emitAll(capabilities.state) },
        ) { _, _, flags ->
            val account = timerStates.current()
            account.source.withState { held ->
                serverTimerControls(
                    dao.latestRunning(),
                    held,
                    flags.timerState,
                    account.account,
                )
            }
        }.flowOn(io).distinctUntilChanged()
    }

    /** What [controls] holds now. */
    suspend fun current(): TimerControls = controls.first()
}
