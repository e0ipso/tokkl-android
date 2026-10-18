package com.github.e0ipso.tokkl.session

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.IOException

/**
 * The user's trust decisions, in the app's private preferences file
 * `server_trust`, next to the server configuration: the one plain `http://`
 * server URL the user confirmed, and the one pinned certificate, as the TLS
 * server's host and port and the SHA-256 of its leaf certificate. A new
 * confirmation or pin replaces the old one, so another URL or certificate
 * asks again. The file holds no credential and no private key, and the backup
 * rules exclude it with every other file. Sign-out removes it with [clear].
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

    override fun pinnedSha256(host: String, port: Int): String? =
        preferences.takeIf { it.getString(KEY_PIN_HOST, null) == host && it.getInt(KEY_PIN_PORT, -1) == port }
            ?.getString(KEY_PIN_SHA256, null)

    @Throws(IOException::class)
    override fun pinCertificate(certificate: ServerCertificate) {
        commit {
            putString(KEY_PIN_HOST, certificate.host)
            putInt(KEY_PIN_PORT, certificate.port)
            putString(KEY_PIN_SHA256, certificate.sha256)
        }
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
        private const val KEY_PIN_HOST = "pin_host"
        private const val KEY_PIN_PORT = "pin_port"
        private const val KEY_PIN_SHA256 = "pin_sha256"
    }
}
