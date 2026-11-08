package com.github.e0ipso.tokkl.platform

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the app is on screen, and the network's returns while it is, for the screens' view
 * models. [processForeground] builds [foreground] from ProcessLifecycleOwner, which counts every
 * activity of the app and waits a moment after the last one stops, so a configuration change is no
 * trip to the background.
 *
 * @property foreground true while one of the app's activities is started.
 * @property reconnects one item each time the default network reaches the internet anew while the
 *   app is in the foreground ([foregroundReconnects]).
 */
class AppForeground(val foreground: StateFlow<Boolean>, val reconnects: Flow<Unit>) {

    /** One item each time the app comes back to the foreground. The state at collection does not count. */
    val returns: Flow<Unit> get() = foreground.drop(1).filter { it }.map { }
}

/**
 * Each of [network]'s [changes] that reaches the internet, while [foreground] is true. Each
 * collector watches the network with a callback of its own, and only while the app is in the
 * foreground, so no network change reaches the app in the background.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun foregroundReconnects(foreground: StateFlow<Boolean>, network: DefaultNetwork): Flow<Unit> =
    foreground.flatMapLatest { shown -> if (shown) network.changes().filter { it }.map { } else emptyFlow() }

/**
 * The process's [AppForeground]: whether one of its activities is started, as ProcessLifecycleOwner
 * says, kept in [scope] from the state it has now, and [network]'s returns while it is.
 */
fun processForeground(scope: CoroutineScope, network: DefaultNetwork): AppForeground {
    val lifecycle = ProcessLifecycleOwner.get().lifecycle
    val started = lifecycle.currentStateFlow
        .map { it.isAtLeast(Lifecycle.State.STARTED) }
        .stateIn(scope, SharingStarted.Eagerly, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    return AppForeground(started, foregroundReconnects(started, network))
}
