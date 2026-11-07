package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.TogglApiClient
import org.koin.core.context.GlobalContext

/** The app's shared HTTP client, from its Koin graph, with its guard and trust checks. */
val appHttp: SharedHttpClient get() = GlobalContext.get().get()

/** The app's configured server, which sign-in sets to the URL it tries. */
val appConfiguredServer: ConfiguredServer get() = GlobalContext.get().get()

/** An API client that sends through [appHttp], as the app's own clients do. */
fun appApiClient(server: ServerUrl, credentials: ApiCredentials): TogglApiClient =
    TogglApiClient(server, credentials, appHttp.instance)
