package com.github.e0ipso.tokkl.session

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True while the default network reaches the internet, read as the app's DefaultNetwork reads it. */
fun ConnectivityManager.reachesInternet(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
