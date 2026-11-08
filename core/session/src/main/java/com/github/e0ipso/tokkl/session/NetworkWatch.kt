package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.NetworkReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Hears whether the phone is [online], each time that changes. */
fun interface NetworkListener {
    fun changed(online: Boolean)
}

/**
 * Follows whether the phone's default network reaches the internet, from [start] to [stop], and
 * reports each change to [listener] on the main thread. The activity watches only while it is
 * started, so in the background no network change reaches the app. A network that has no
 * internet but reaches a Tokkl server on the local network counts as offline, as in the picker.
 *
 * @param scope where the watch runs between [start] and [stop].
 * @param main the main thread, where the watch starts at once, so the callback is registered
 *   before [start] returns and each report reaches [listener] as it arrives.
 */
class NetworkWatch(
    private val network: DefaultNetwork,
    private val scope: CoroutineScope,
    private val main: CoroutineContext,
    private val listener: NetworkListener,
) {

    private var watching: Job? = null

    /** Starts watching, and returns whether the phone is online now. Call it on the main thread. */
    fun start(): Boolean {
        val initial = network.current()
        val online = initial?.internet == true
        if (watching == null) {
            val state = NetworkWatchState(initial?.id, online, initial?.validated == true, listener)
            watching = scope.launch(main) {
                network.reports.collect { report ->
                    when (report) {
                        is NetworkReport.Available -> state.available(report.id)

                        is NetworkReport.Capabilities ->
                            state.capabilities(report.id, report.state.internet, report.state.validated)

                        is NetworkReport.Lost -> state.lost(report.id)
                    }
                }
            }
        }
        return online
    }

    /** Stops watching. Call it on the main thread. */
    fun stop() {
        watching?.cancel()
        watching = null
    }
}

/**
 * Relevant default-network changes. Initial and unchanged capability reports, such as signal
 * strength updates, ask nothing. A new default or newly validated route may recover a failed
 * request even while INTERNET stays true. Validation is a change signal, not a requirement for
 * reaching a server on a local network.
 */
internal class NetworkWatchState<N : Any>(
    initial: N?,
    online: Boolean,
    validated: Boolean,
    private val listener: NetworkListener,
) {
    private data class Snapshot<N>(val network: N, val online: Boolean, val validated: Boolean)
    private var current = initial
    private var last = initial?.let { Snapshot(it, online, validated) }

    fun available(network: N) {
        current = network
    }

    fun capabilities(network: N, online: Boolean, validated: Boolean) {
        if (network != current) return
        val next = Snapshot(network, online, validated)
        if (next == last) return
        last = next
        listener.changed(online)
    }

    fun lost(network: N) {
        if (network != current) return
        current = null
        last = null
        listener.changed(false)
    }
}
