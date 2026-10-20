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
        if (callback == null) {
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    changed(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                }

                override fun onLost(network: Network) {
                    changed(false)
                }
            }.also { connectivity.registerDefaultNetworkCallback(it, main) }
        }
        return connectivity.reachesInternet()
    }

    /** Stops watching. Call it on the main thread. */
    fun stop() {
        callback?.let(connectivity::unregisterNetworkCallback)
        callback = null
    }
}

/** True while the default network reaches the internet. */
internal fun ConnectivityManager.reachesInternet(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
