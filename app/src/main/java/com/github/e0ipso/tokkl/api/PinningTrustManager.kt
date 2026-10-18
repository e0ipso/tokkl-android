package com.github.e0ipso.tokkl.api

import android.annotation.SuppressLint
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * A server's leaf certificate as the trust prompt shows it, and as a pin
 * stores it: the TLS server's [host] and [port], the SHA-256 of the
 * certificate's DER encoding as lowercase hex, and its subject. Public data
 * only.
 */
data class ServerCertificate(val host: String, val port: Int, val sha256: String, val subject: String) {

    /** The fingerprint as `AB:CD:…`, the form `openssl x509 -noout -fingerprint -sha256` prints. */
    val displaySha256: String
        get() = sha256.uppercase().chunked(2).joinToString(":")

    companion object {
        fun of(certificate: X509Certificate, host: String, port: Int): ServerCertificate =
            ServerCertificate(
                host = host,
                port = port,
                sha256 = sha256(certificate),
                subject = certificate.subjectX500Principal.getName(X500Principal.RFC2253),
            )

        /** The SHA-256 of [certificate]'s DER encoding, as lowercase hex. */
        fun sha256(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
    }
}

/**
 * No system or user CA trusts the server's certificate, and the user has
 * not pinned it. [pinChanged] is true when the user pinned another
 * certificate for this host and port. The TLS handshake fails on this, before
 * any HTTP request, so no credential has left the phone. The message names
 * the host and port only.
 */
class UntrustedCertificateException(
    val certificate: ServerCertificate,
    val pinChanged: Boolean,
    cause: CertificateException,
) : CertificateException("No trusted CA or pin covers the certificate of ${certificate.host}:${certificate.port}", cause)

/**
 * The trust manager of [SharedHttpClient]. The [platform] trust manager
 * decides first, so a certificate that a system or user CA signed is trusted
 * as before and the network security config still applies. When the platform
 * refuses the chain, the leaf is trusted only if its SHA-256 matches the pin
 * the user stored for that host and port, read through [pinnedSha256].
 * Anything else fails with [UntrustedCertificateException], which carries
 * the leaf for the trust prompt.
 *
 * The pin covers the exact leaf certificate. The handshake proves the server
 * holds its private key, so a matching leaf is the certificate the user
 * trusted. OkHttp still checks the host name against the leaf afterwards.
 *
 * Lint warns about any custom trust manager, since many skip the check. This
 * one calls the platform's first and adds only the exact pinned leaf.
 */
@SuppressLint("CustomX509TrustManager")
internal class PinningTrustManager(
    private val platform: X509TrustManager,
    private val pinnedSha256: (host: String, port: Int) -> String?,
) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        val session = (socket as? SSLSocket)?.handshakeSession
        verify(chain, session?.peerHost, session?.peerPort ?: -1) {
            if (platform is X509ExtendedTrustManager) {
                platform.checkServerTrusted(chain, authType, socket)
            } else {
                platform.checkServerTrusted(chain, authType)
            }
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        verify(chain, engine?.peerHost, engine?.peerPort ?: -1) {
            if (platform is X509ExtendedTrustManager) {
                platform.checkServerTrusted(chain, authType, engine)
            } else {
                platform.checkServerTrusted(chain, authType)
            }
        }
    }

    /** Without the server's host and port no pin applies, so the platform alone decides. */
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        verify(chain, host = null, port = -1) { platform.checkServerTrusted(chain, authType) }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        throw CertificateException("The app does not accept client connections")
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        throw CertificateException("The app does not accept client connections")
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("The app does not accept client connections")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    /**
     * Returns when [platformCheck] trusts [chain], or when its leaf matches the
     * pin for [host] and [port]. Throws otherwise.
     */
    internal fun verify(chain: Array<out X509Certificate>, host: String?, port: Int, platformCheck: () -> Unit) {
        val refusal = try {
            platformCheck()
            return
        } catch (e: CertificateException) {
            e
        }
        val leaf = chain.firstOrNull()
        if (leaf == null || host == null || port !in 1..MAX_PORT) throw refusal
        val seen = ServerCertificate.of(leaf, host, port)
        val pin = pinnedSha256(host, port)
        if (pin != null && MessageDigest.isEqual(pin.toByteArray(), seen.sha256.toByteArray())) return
        throw UntrustedCertificateException(seen, pinChanged = pin != null, cause = refusal)
    }

    private companion object {
        const val MAX_PORT = 65_535
    }
}
