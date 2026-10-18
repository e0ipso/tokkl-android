package com.github.e0ipso.tokkl.session

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.IOException

/**
 * The user's trust decisions, in the app's private preferences file
 * `server_trust`: the one plain `http://` server URL the user confirmed.
 * Confirming another URL replaces it, so a changed URL asks again. The file
 * holds no credential, and the backup rules exclude it with every other file.
 * Sign-out removes it with [clear].
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class ServerTrustStore(context: Context) : ServerTrust {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    override fun isCleartextConfirmed(server: ServerUrl): Boolean =
        server.isCleartext && preferences.getString(KEY_CLEARTEXT_URL, null) == server.toString()

    @Throws(IOException::class)
    override fun confirmCleartext(server: ServerUrl) {
        commit { putString(KEY_CLEARTEXT_URL, server.toString()) }
    }

    /** Forgets every decision, so each server asks again. */
    @Throws(IOException::class)
    fun clear() {
        commit { clear() }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    internal companion object {
        const val FILE_NAME = "server_trust"
        private const val KEY_CLEARTEXT_URL = "cleartext_url"
    }
}
