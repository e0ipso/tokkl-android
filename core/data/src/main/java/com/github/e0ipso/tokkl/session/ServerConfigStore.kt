package com.github.e0ipso.tokkl.session

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.IOException

/**
 * The configured server's base URL, in the app's private preferences. The URL
 * is not a secret: [ServerUrl.parse] refuses one with a user name or password.
 * The token lives in [com.github.e0ipso.tokkl.credentials.ApiTokenStore].
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class ServerConfigStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /** The stored server, or null when there is none or it no longer parses. */
    fun load(): ServerUrl? = preferences.getString(KEY_BASE_URL, null)?.let(ServerUrl::parse)

    @Throws(IOException::class)
    fun save(server: ServerUrl) {
        commit { putString(KEY_BASE_URL, server.toString()) }
    }

    @Throws(IOException::class)
    fun clear() {
        commit { remove(KEY_BASE_URL) }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    internal companion object {
        const val FILE_NAME = "server_config"
        private const val KEY_BASE_URL = "base_url"
    }
}
