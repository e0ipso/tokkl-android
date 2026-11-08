package com.github.e0ipso.tokkl.session

import android.content.Context
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.storage.PreferencesFile
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
 * Every call reads or writes the file on the calling thread, since [ServerTrust] does not suspend:
 * OkHttp's trust checks read it on OkHttp's threads, and the decisions are written on the I/O
 * dispatcher. Never call it on the main thread.
 */
class ServerTrustStore(context: Context) :
    ServerTrust,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = clear()

    private val file = PreferencesFile(context, FILE_NAME)
    private val preferences get() = file.preferences

    override fun isCleartextConfirmed(server: ServerUrl): Boolean =
        server.isCleartext && preferences.getString(KEY_CLEARTEXT_URL, null) == server.toString()

    @Throws(IOException::class)
    override fun confirmCleartext(server: ServerUrl) {
        file.commitHere { putString(KEY_CLEARTEXT_URL, server.toString()) }
    }

    override fun pinnedSha256(host: String, port: Int): String? =
        preferences.takeIf { it.getString(KEY_PIN_HOST, null) == host && it.getInt(KEY_PIN_PORT, -1) == port }
            ?.getString(KEY_PIN_SHA256, null)

    @Throws(IOException::class)
    override fun pinCertificate(certificate: ServerCertificate) {
        file.commitHere {
            putString(KEY_PIN_HOST, certificate.host)
            putInt(KEY_PIN_PORT, certificate.port)
            putString(KEY_PIN_SHA256, certificate.sha256)
        }
    }

    /** Forgets every decision, so each server asks again. */
    @Throws(IOException::class)
    fun clear() {
        file.commitHere { clear() }
    }

    internal companion object {
        const val FILE_NAME = "server_trust"
        private const val KEY_CLEARTEXT_URL = "cleartext_url"
        private const val KEY_PIN_HOST = "pin_host"
        private const val KEY_PIN_PORT = "pin_port"
        private const val KEY_PIN_SHA256 = "pin_sha256"
    }
}
