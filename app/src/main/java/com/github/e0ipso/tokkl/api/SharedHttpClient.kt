package com.github.e0ipso.tokkl.api

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * The app's only [OkHttpClient]. Every request goes through it, so its
 * connection pool, dispatcher and security settings apply everywhere.
 * SingleHttpClientTest fails when other app code builds an HTTP client.
 *
 * Every request and redirect must stay on [ConfiguredServer.current]; see
 * [ConfiguredServerGuard]. Later security settings go into [build], never into
 * a second client, such as the trust manager for a pinned self-signed
 * certificate (TKA-0021).
 */
object SharedHttpClient {

    val instance: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient = OkHttpClient.Builder()
        // The guard follows same-origin redirects itself.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(ConfiguredServerGuard { ConfiguredServer.current })
        .build()
}

/** JSON settings for both APIs. Models declare only the fields the app reads. */
internal val ApiJson: Json = Json { ignoreUnknownKeys = true }
