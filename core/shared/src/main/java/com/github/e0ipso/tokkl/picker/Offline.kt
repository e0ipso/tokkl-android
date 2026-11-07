package com.github.e0ipso.tokkl.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.whileShown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.koinInject

/**
 * True while the phone has no network that reaches the internet. Follows the default network
 * while in the composition, as [offlineIn] does for a view model.
 */
@Composable
fun rememberOffline(network: DefaultNetwork = koinInject()): Boolean {
    val initial = remember(network) { !network.reachesInternet() }
    val offline by remember(network) { network.online.map { !it } }.collectAsState(initial)
    return offline
}

/**
 * True while the phone has no network that reaches the internet, as in airplane mode, for a
 * screen's view model in [scope]. It starts with the default network as it is now and follows it
 * while the screen reads it ([whileShown]). A network that has no internet but reaches a Tokkl
 * server on the local network counts as offline here, which only changes a hint.
 */
fun DefaultNetwork.offlineIn(scope: CoroutineScope): StateFlow<Boolean> =
    online.map { !it }.stateIn(scope, whileShown, !reachesInternet())
