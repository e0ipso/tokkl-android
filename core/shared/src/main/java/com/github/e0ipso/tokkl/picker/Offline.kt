package com.github.e0ipso.tokkl.picker

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.e0ipso.tokkl.session.reachesInternet

/**
 * True while the phone has no network that reaches the internet, as in airplane mode. Follows the
 * default network while in the composition. A network that has no internet but reaches a Tokkl
 * server on the local network counts as offline here, which only changes a hint.
 */
@Composable
fun rememberOffline(): Boolean {
    val context = LocalContext.current
    val connectivity = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    var offline by remember(connectivity) { mutableStateOf(!connectivity.reachesInternet()) }
    DisposableEffect(connectivity) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                offline = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                offline = true
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }
    return offline
}
