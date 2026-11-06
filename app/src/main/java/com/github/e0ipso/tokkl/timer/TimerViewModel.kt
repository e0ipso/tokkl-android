package com.github.e0ipso.tokkl.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.TimerControlsSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.favorites.FavoritesModel
import com.github.e0ipso.tokkl.favorites.FavoritesServer
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.TimerActions
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Keeps the timer's draft and the taps the cache or the server does not show yet across
 * configuration changes, and the timer templates on its panel. The timer reads the app's shared
 * timer controls and sends the pause chain's actions to the server.
 */
class TimerViewModel(
    controls: TimerControlsSource,
    cache: AccountCache,
    accountSettings: AccountSettingsSource,
    changes: ChangeQueue,
    actions: TimerActions,
    sender: ChangeSender,
    held: HeldTimerFlows,
    timerStates: AccountTimerStates,
    capabilities: CapabilitiesSource,
    session: Session,
    io: CoroutineContext,
) : ViewModel() {

    val model = TimerModel(
        controls = controls.controls,
        projects = cache.projects,
        tasks = cache.tasks,
        accountSettings = accountSettings.state,
        changes = changes,
        scope = viewModelScope,
        trace = TapTrace.System,
        pauseChain = actions,
        sendQueued = { sender.sendAndWait() },
        discardNotices = held.discardNotice,
        // Records the notice as shown in the account's timer state store.
        takeDiscardNotice = { withContext(io) { timerStates.source.takeDiscardNotice() } },
        timerApi = capabilities.state.map { it.timerState },
    )

    val favorites = FavoritesModel(
        favorites = cache.favorites,
        projects = cache.projects,
        tasks = cache.tasks,
        available = capabilities.state.map { it.favorites },
        save = FavoritesServer(client = session::client, cache = cache)::save,
        scope = viewModelScope,
        io = io,
    )
}
