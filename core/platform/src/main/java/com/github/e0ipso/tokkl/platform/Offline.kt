package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * True while the phone has no network that reaches the internet, as in airplane mode, for a
 * screen's view model in [scope]. It starts with the default network as it is now and follows it
 * while the screen reads it ([whileShown]). A network that has no internet but reaches a Tokkl
 * server on the local network counts as offline here, which only changes a hint.
 */
fun DefaultNetwork.offlineIn(scope: CoroutineScope): StateFlow<Boolean> =
    online.map { !it }.stateIn(scope, whileShown, !reachesInternet())
