package com.github.e0ipso.tokkl.session

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.StoredServer
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * The configured server's base URL, in the app's private preferences. The URL
 * is not a secret: [ServerUrl.parse] refuses one with a user name or password.
 * The token lives in [com.github.e0ipso.tokkl.credentials.ApiTokenStore].
 *
 * The suspending calls read and write the file on the I/O dispatcher. [storedNow] reads it on the
 * calling thread, which is OkHttp's.
 */
class ServerConfigStore(context: Context, private val io: CoroutineContext) :
    StoredServer,
    ClearableStore<ServerUrl> {

    private val file = PreferencesFile(context, FILE_NAME, io)

    override suspend fun load(): ServerUrl? = file.read { storedServer() }

    /** [load] on OkHttp's thread, which every request's guard reads it from without suspending. */
    override fun storedNow(): ServerUrl? = file.preferences.storedServer()

    @Throws(IOException::class)
    override suspend fun save(value: ServerUrl) {
        file.commit { putString(KEY_BASE_URL, value.toString()) }
    }

    @Throws(IOException::class)
    override suspend fun clear() {
        file.commit { remove(KEY_BASE_URL) }
    }

    private fun SharedPreferences.storedServer(): ServerUrl? = getString(KEY_BASE_URL, null)?.let(ServerUrl::parse)

    internal companion object {
        const val FILE_NAME = "server_config"
        private const val KEY_BASE_URL = "base_url"
    }
}
