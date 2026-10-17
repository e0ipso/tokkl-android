package com.github.e0ipso.tokkl.api

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * The app's only [OkHttpClient]. Every request goes through it, so its
 * connection pool, dispatcher and security settings apply everywhere.
 *
 * Later security settings go into [build], never into a second client: the
 * trust manager for a pinned self-signed certificate (TKA-0021) and the
 * interceptor that blocks hosts other than the configured server (TKA-0017).
 */
object SharedHttpClient {

    val instance: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient = OkHttpClient.Builder().build()
}

/** JSON settings for both APIs. Models declare only the fields the app reads. */
internal val ApiJson: Json = Json { ignoreUnknownKeys = true }
