package com.github.e0ipso.tokkl.api

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pin check of [PinningTrustManager], with two self-signed certificates
 * that share a subject (`CN=tokkl.test,O=Tokkl test fixture`) but not a key.
 * They were made once with
 *
 *     openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 36500 \
 *       -subj '/CN=tokkl.test/O=Tokkl test fixture' -addext 'subjectAltName=DNS:tokkl.test' \
 *       -keyout <discarded> -out self-signed-<a|b>.crt
 *
 * and their private keys were deleted, so the repository holds certificates
 * only. They end in .crt, since .gitignore keeps every .pem out in case it
 * holds a key. The platform here is the JDK's default trust manager, which trusts
 * neither.
 */
class PinningTrustManagerTest {

    private val a = certificate("self-signed-a.crt")
    private val b = certificate("self-signed-b.crt")
    private val pins = mutableMapOf<String, String>()
    private var pinLookups = 0
    private val jdk = trustManager(keyStore = null)
    private val manager = PinningTrustManager(jdk) { host, port ->
        pinLookups++
        pins["$host:$port"]
    }

    @Test
    fun aDifferentCertificateWithTheSameSubjectIsRejected() {
        assertEquals(a.subjectX500Principal, b.subjectX500Principal)
        assertNotEquals(ServerCertificate.sha256(a), ServerCertificate.sha256(b))
        pins["tokkl.test:443"] = ServerCertificate.sha256(a)

        val error = assertThrows(UntrustedCertificateException::class.java) { check(b, "tokkl.test", 443) }

        assertTrue("the prompt says the certificate changed", error.pinChanged)
        assertEquals(ServerCertificate.of(b, "tokkl.test", 443), error.certificate)
        assertEquals(B_SHA256, error.certificate.displaySha256)
    }

    @Test
    fun thePinnedCertificateIsAccepted() {
        pins["tokkl.test:443"] = ServerCertificate.sha256(a)

        check(a, "tokkl.test", 443)
    }

    @Test
    fun anUnpinnedCertificateFailsWithTheFingerprintAndSubjectForThePrompt() {
        val error = assertThrows(UntrustedCertificateException::class.java) { check(a, "tokkl.test", 443) }

        assertFalse(error.pinChanged)
        assertEquals(A_SHA256, error.certificate.displaySha256)
        assertEquals("O=Tokkl test fixture,CN=tokkl.test", error.certificate.subject)
        assertEquals("tokkl.test" to 443, error.certificate.host to error.certificate.port)
        assertTrue("the platform's refusal is the cause", error.cause is CertificateException)
    }

    @Test
    fun aPinCoversOnlyItsHostAndPort() {
        pins["tokkl.test:443"] = ServerCertificate.sha256(a)

        assertThrows(UntrustedCertificateException::class.java) { check(a, "tokkl.test", 8443) }
        assertThrows(UntrustedCertificateException::class.java) { check(a, "other.test", 443) }
    }

    @Test
    fun withoutTheServersHostThePlatformAloneDecides() {
        pins["tokkl.test:443"] = ServerCertificate.sha256(a)

        val error = assertThrows(CertificateException::class.java) { manager.checkServerTrusted(arrayOf(a), AUTH_TYPE) }

        assertFalse("no pin applies", error is UntrustedCertificateException)
        assertEquals(0, pinLookups)
    }

    @Test
    fun aCertificateThePlatformTrustsNeverReachesThePin() {
        // As if the user had installed the certificate as a CA in Android.
        val platform = trustManager(KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("a", a)
        })
        val manager = PinningTrustManager(platform) { _, _ -> error("the pin was read") }

        manager.verify(arrayOf(a), "tokkl.test", 443) { platform.checkServerTrusted(arrayOf(a), AUTH_TYPE) }
    }

    @Test
    fun theFingerprintIsTheOneOpensslPrints() {
        assertEquals(A_SHA256, ServerCertificate.of(a, "tokkl.test", 443).displaySha256)
        assertEquals(A_SHA256.replace(":", "").lowercase(), ServerCertificate.sha256(a))
    }

    @Test
    fun fixturesHoldNoPrivateKey() {
        for (name in listOf("self-signed-a.crt", "self-signed-b.crt")) {
            val text = javaClass.getResourceAsStream("/certificates/$name")!!.use { it.readBytes().decodeToString() }
            assertFalse("$name holds a private key", "PRIVATE KEY" in text)
        }
    }

    /** The JDK's trust manager checks without a host, as Android's does inside a handshake. */
    private fun check(leaf: X509Certificate, host: String, port: Int) {
        manager.verify(arrayOf(leaf), host, port) { jdk.checkServerTrusted(arrayOf(leaf), AUTH_TYPE) }
    }

    private fun certificate(name: String): X509Certificate =
        javaClass.getResourceAsStream("/certificates/$name")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

    private fun trustManager(keyStore: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .single()

    private companion object {
        const val AUTH_TYPE = "ECDHE_ECDSA"

        /** `openssl x509 -in self-signed-a.crt -noout -fingerprint -sha256` */
        const val A_SHA256 = "21:D3:4B:8A:10:BD:DA:F4:D3:E1:D8:D5:2E:97:CD:44:8F:98:C9:F6:4D:F7:FD:61:33:4B:FB:9D:E5:6E:2A:16"
        const val B_SHA256 = "AD:89:02:A2:77:CD:C1:D0:36:1B:A5:70:4C:89:5F:5A:1E:3F:57:33:17:AC:D3:94:FE:85:54:17:52:F3:20:B4"
    }
}
