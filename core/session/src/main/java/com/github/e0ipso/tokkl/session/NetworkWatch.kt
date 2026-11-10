package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.changes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Follows whether the phone's default network reaches the internet, from [start] to [stop], and
 * reports each relevant change ([changes]) to [listener] on the main thread. The activity watches
 * only while it is started, so in the background no network change reaches the app. A network that
 * has no internet but reaches a Tokkl server on the local network counts as offline, as in the
 * picker.
 *
 * @param main the main thread, where the watch starts at once, so the callback is registered
 *   before [start] returns and each report reaches [listener] as it arrives.
 */
class NetworkWatch(
    private val network: DefaultNetwork,
    private val scope: CoroutineScope,
    private val main: CoroutineContext,
    private val listener: (Boolean) -> Unit,
) {

    private var watching: Job? = null

    /** Starts watching, and returns whether the phone is online now. Call it on the main thread. */
    fun start(): Boolean {
        val online = network.reachesInternet()
        if (watching == null) watching = scope.launch(main) { network.changes().collect(listener) }
        return online
    }

    /** Stops watching. Call it on the main thread. */
    fun stop() {
        watching?.cancel()
        watching = null
    }
}
