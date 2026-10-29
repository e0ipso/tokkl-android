package com.github.e0ipso.tokkl.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/**
 * Follows whether the phone's default network reaches the internet, from [start] to [stop], and
 * reports each change to [changed] on the main thread. The activity watches only while it is
 * started, so in the background no network change reaches the app. A network that has no
 * internet but reaches a Tokkl server on the local network counts as offline, as in the picker.
 */
class NetworkWatch(context: Context, private val changed: (online: Boolean) -> Unit) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Starts watching, and returns whether the phone is online now. Call it on the main thread. */
    fun start(): Boolean {
        val initial = connectivity.activeNetwork
        val capabilities = connectivity.getNetworkCapabilities(initial)
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (callback == null) {
            val state = NetworkWatchState(
                initial,
                online,
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                changed,
            )
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (callback !== this) return
                    state.available(network)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (callback !== this) return
                    state.capabilities(
                        network,
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    )
                }

                override fun onLost(network: Network) {
                    if (callback !== this) return
                    state.lost(network)
                }
            }.also { connectivity.registerDefaultNetworkCallback(it, main) }
        }
        return online
    }

    /** Stops watching. Call it on the main thread. */
    fun stop() {
        callback?.let(connectivity::unregisterNetworkCallback)
        callback = null
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
    private val changed: (Boolean) -> Unit,
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
        changed(online)
    }

    fun lost(network: N) {
        if (network != current) return
        current = null
        last = null
        changed(false)
    }
}

/** True while the default network reaches the internet. */
internal fun ConnectivityManager.reachesInternet(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
