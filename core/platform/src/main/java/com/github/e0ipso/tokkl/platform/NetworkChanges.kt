package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * The default network's relevant changes from the moment of collection: true when it reaches the
 * internet after the change, false when it does not or went. The network as it is at collection
 * does not count, and neither do unchanged reports, such as signal strength updates. A new default
 * or newly validated route counts even while INTERNET stays true, since it may let a failed request
 * through. Each collector registers a callback of its own while it collects.
 */
fun DefaultNetwork.changes(): Flow<Boolean> = channelFlow {
    val initial = current()
    val online = initial?.internet == true
    val state = NetworkWatchState(initial?.id, online, initial?.validated == true) { trySend(it) }
    reports.collect { report ->
        when (report) {
            is NetworkReport.Available -> state.available(report.id)

            is NetworkReport.Capabilities ->
                state.capabilities(report.id, report.state.internet, report.state.validated)

            is NetworkReport.Lost -> state.lost(report.id)
        }
    }
}

/**
 * Relevant default-network changes. Validation is a change signal, not a requirement for reaching
 * a server on a local network.
 */
internal class NetworkWatchState<N : Any>(
    initial: N?,
    online: Boolean,
    validated: Boolean,
    private val listener: (Boolean) -> Unit,
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
        listener(online)
    }

    fun lost(network: N) {
        if (network != current) return
        current = null
        last = null
        listener(false)
    }
}
