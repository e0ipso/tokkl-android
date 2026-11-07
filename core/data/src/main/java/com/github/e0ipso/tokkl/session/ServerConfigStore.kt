package com.github.e0ipso.tokkl.session

import android.content.Context
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.StoredServer
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import java.io.IOException

/**
 * The configured server's base URL, in the app's private preferences. The URL
 * is not a secret: [ServerUrl.parse] refuses one with a user name or password.
 * The token lives in [com.github.e0ipso.tokkl.credentials.ApiTokenStore].
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class ServerConfigStore(context: Context) :
    StoredServer,
    ClearableStore<ServerUrl> {

    private val file = PreferencesFile(context, FILE_NAME)
    private val preferences get() = file.preferences

    /** The stored server, or null when there is none or it no longer parses. */
    override fun load(): ServerUrl? = preferences.getString(KEY_BASE_URL, null)?.let(ServerUrl::parse)

    @Throws(IOException::class)
    override fun save(value: ServerUrl) {
        file.commit { putString(KEY_BASE_URL, value.toString()) }
    }

    @Throws(IOException::class)
    override fun clear() {
        file.commit { remove(KEY_BASE_URL) }
    }

    internal companion object {
        const val FILE_NAME = "server_config"
        private const val KEY_BASE_URL = "base_url"
    }
}
