package com.github.e0ipso.tokkl.platform

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

/** The default network as one report of its callback names it: its handle, and what it reaches. */
data class NetworkState(val id: Long, val internet: Boolean, val validated: Boolean)

/** One report of the default network's callback. [id] is the network's handle. */
sealed interface NetworkReport {
    val id: Long

    /** A network became the default. Its capabilities follow in a [Capabilities] report. */
    data class Available(override val id: Long) : NetworkReport

    /** The default network's capabilities, on registration and on each change. */
    data class Capabilities(val state: NetworkState) : NetworkReport {
        override val id: Long get() = state.id
    }

    data class Lost(override val id: Long) : NetworkReport
}

/**
 * The phone's default network, the one source of network changes for the app. Each collector of
 * [reports] or [online] gets a callback of its own, registered on the main thread while it
 * collects, and Android starts each with the default network it has at registration.
 */
interface DefaultNetwork {

    fun current(): NetworkState?

    fun reachesInternet(): Boolean = current()?.internet == true

    /** The callback's reports, delivered on the main thread. */
    val reports: Flow<NetworkReport>

    /**
     * Whether the default network reaches the internet, from each capability report, and false
     * when it goes. A network that has no internet but reaches a Tokkl server on the local network
     * counts as offline.
     */
    val online: Flow<Boolean>
        get() = reports.mapNotNull { report ->
            when (report) {
                is NetworkReport.Available -> null
                is NetworkReport.Capabilities -> report.state.internet
                is NetworkReport.Lost -> false
            }
        }
}

/**
 * True while the phone has no network that reaches the internet, as in airplane mode, for a
 * screen's view model in [scope]. It starts with the default network as it is now and follows it
 * while the screen reads it ([whileShown]). A network that has no internet but reaches a Tokkl
 * server on the local network counts as offline here, which only changes a hint.
 */
fun DefaultNetwork.offlineIn(scope: CoroutineScope): StateFlow<Boolean> =
    online.map { !it }.stateIn(scope, whileShown, !reachesInternet())

class ConnectivityDefaultNetwork(private val connectivity: ConnectivityManager) : DefaultNetwork {

    override fun current(): NetworkState? {
        val network = connectivity.activeNetwork ?: return null
        return state(network, connectivity.getNetworkCapabilities(network))
    }

    override val reports: Flow<NetworkReport> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkReport.Available(network.networkHandle))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(NetworkReport.Capabilities(state(network, capabilities)))
            }

            override fun onLost(network: Network) {
                trySend(NetworkReport.Lost(network.networkHandle))
            }
        }
        connectivity.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.buffer(Channel.UNLIMITED)

    private fun state(network: Network, capabilities: NetworkCapabilities?) = NetworkState(
        id = network.networkHandle,
        internet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
    )
}
