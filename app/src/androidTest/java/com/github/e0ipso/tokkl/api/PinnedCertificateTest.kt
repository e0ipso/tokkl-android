package com.github.e0ipso.tokkl.api

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The shared client against the real Tokkl container behind the TLS proxy
 * that scripts/instrumented-tests.sh starts with a self-signed certificate.
 * No system or user CA trusts that certificate, so only a pin lets a request
 * through, and Android's own TLS stack runs the handshake.
 */
class PinnedCertificateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val servers = ServerConfigStore(context)
    private val trust = ServerTrustStore(context)
    private val server = TokklTestServer.tlsUrl
    private val me = server.api("me")
    private val client = TogglApiClient(server, ApiCredentials.ApiToken(TokklTestServer.apiToken))

    @Before
    fun configureTheServer() {
        servers.save(server)
        trust.clear()
    }

    @After
    fun forgetTheServer() {
        trust.clear()
        servers.clear()
    }

    @Test
    fun unpinnedCertificateFailsTheHandshakeWithItsFingerprint() {
        val refusal = refusal(client.get(me, JsonObject.serializer()))

        assertEquals(TokklTestServer.tlsFingerprint, refusal.certificate.displaySha256)
        assertEquals(me.host to me.port, refusal.certificate.host to refusal.certificate.port)
        assertFalse(refusal.changed)
    }

    @Test
    fun pinnedCertificateReachesTokkl() {
        trust.pinCertificate(pin(TokklTestServer.tlsFingerprint.replace(":", "").lowercase()))

        val result = client.get(me, JsonObject.serializer())

        val body = (result as? ApiResult.Success)?.value ?: throw AssertionError("GET /api/v9/me failed: ${(result as ApiResult.Failure).error.javaClass.simpleName}")
        assertEquals(TokklTestServer.email, body.getValue("email").jsonPrimitive.content)
    }

    @Test
    fun anotherPinnedCertificateFailsAsAChangedCertificate() {
        trust.pinCertificate(pin("00".repeat(32)))

        val refusal = refusal(client.get(me, JsonObject.serializer()))

        assertTrue(refusal.changed)
        assertEquals(TokklTestServer.tlsFingerprint, refusal.certificate.displaySha256)
    }

    private fun pin(sha256: String) = ServerCertificate(me.host, me.port, sha256, subject = "")

    /** The pinning trust manager's refusal, which the client reports as its own error. */
    private fun refusal(result: ApiResult<*>): ApiError.UntrustedCertificate {
        val error = (result as? ApiResult.Failure)?.error
        return error as? ApiError.UntrustedCertificate ?: throw AssertionError("expected a refused certificate, got ${error ?: "a success"}")
    }
}
