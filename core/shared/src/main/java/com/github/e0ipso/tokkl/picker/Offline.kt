package com.github.e0ipso.tokkl.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * True while the phone has no network that reaches the internet, as in airplane mode. Follows the
 * default network while in the composition. A network that has no internet but reaches a Tokkl
 * server on the local network counts as offline here, which only changes a hint.
 */
@Composable
fun rememberOffline(network: DefaultNetwork = koinInject()): Boolean {
    val initial = remember(network) { !network.reachesInternet() }
    val offline by remember(network) { network.online.map { !it } }.collectAsState(initial)
    return offline
}
