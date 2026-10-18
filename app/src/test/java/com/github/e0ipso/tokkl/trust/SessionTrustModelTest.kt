package com.github.e0ipso.tokkl.trust

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.UntrustedCertificateException
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A signed-in session's requests against a MockWebServer, with the session's
 * client reporting to the model as the app's Session does.
 */
class SessionTrustModelTest {

    private val server = MockWebServer()
    private val trust = InMemoryServerTrust()
    private lateinit var url: ServerUrl
    private var retries = 0
    private lateinit var model: SessionTrustModel

    @Before
    fun setUp() {
        server.start()
        url = ServerUrl.parse(server.url("/").toString())!!
        // A stored http session whose confirmation is missing.
        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(trust)
        model = model(trust)
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun unconfirmedHttpSessionPromptsAndSendsNothingUntilTheUserConfirms() {
        server.enqueue(me())
        val client = client()

        val refused = client.get(url.api("me"), JsonObject.serializer())

        assertEquals(ApiResult.Failure(ApiError.CleartextNotConfirmed(url)), refused)
        assertEquals("no request before the confirmation", 0, server.requestCount)
        assertEquals(SessionTrustModel.State(ApiError.CleartextNotConfirmed(url), promptOpen = true), model.state.value)

        model.accept()

        assertTrue(trust.isCleartextConfirmed(url))
        assertEquals(1, retries)
        assertEquals(SessionTrustModel.State(), model.state.value)
        assertTrue(client.get(url.api("me"), JsonObject.serializer()) is ApiResult.Success)
        assertEquals(ApiCredentials.ApiToken(TOKEN).authorizationHeader, server.takeRequest().headers["Authorization"])
    }

    @Test
    fun cancellingKeepsRequestsRefusedAndTheSameRefusalLeavesThePromptClosed() {
        val client = client()
        client.get(url.api("me"), JsonObject.serializer())

        model.cancel()
        val again = client.get(url.api("me"), JsonObject.serializer())

        assertEquals(ApiResult.Failure(ApiError.CleartextNotConfirmed(url)), again)
        assertEquals(0, server.requestCount)
        assertEquals(SessionTrustModel.State(ApiError.CleartextNotConfirmed(url), promptOpen = false), model.state.value)

        model.reopen()

        assertTrue(model.state.value.promptOpen)
    }

    @Test
    fun changedCertificatePromptsWithTheNewOneAndAcceptingPinsIt() {
        trust.confirmCleartext(url)
        val old = CERTIFICATE.copy(sha256 = "cd".repeat(32))
        trust.pinCertificate(old)
        server.enqueue(me())
        val client = client(handshakeAsIfServing(CERTIFICATE))

        val refused = client.get(url.api("me"), JsonObject.serializer())

        val refusal = ApiError.UntrustedCertificate(CERTIFICATE, changed = true)
        assertEquals(ApiResult.Failure(refusal), refused)
        assertEquals(0, server.requestCount)
        assertEquals(SessionTrustModel.State(refusal, promptOpen = true), model.state.value)

        model.accept()

        assertEquals(CERTIFICATE.sha256, trust.pinnedSha256(CERTIFICATE.host, CERTIFICATE.port))
        assertTrue(client.get(url.api("me"), JsonObject.serializer()) is ApiResult.Success)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun anotherRefusalOpensThePromptAgain() {
        model.refused(ApiError.CleartextNotConfirmed(url))
        model.cancel()

        model.refused(ApiError.UntrustedCertificate(CERTIFICATE, changed = false))

        assertEquals(SessionTrustModel.State(ApiError.UntrustedCertificate(CERTIFICATE, changed = false), promptOpen = true), model.state.value)
    }

    @Test
    fun otherNetworkFailuresShowNoPrompt() {
        trust.confirmCleartext(url)
        server.close()

        val result = client().get(url.api("me"), JsonObject.serializer())

        assertTrue(result.toString(), (result as ApiResult.Failure).error is ApiError.Network)
        assertNull(model.state.value.refusal)
    }

    @Test
    fun anAnswerFromTheServerClearsTheRefusal() {
        model.refused(ApiError.CleartextNotConfirmed(url))
        model.cancel()
        trust.confirmCleartext(url)
        server.enqueue(MockResponse.Builder().code(403).build())

        client().get(url.api("me"), JsonObject.serializer())

        assertEquals(SessionTrustModel.State(), model.state.value)
    }

    @Test
    fun aFailedWriteKeepsTheRefusalAndTheWayBack() {
        val failing = object : ServerTrust by trust {
            override fun confirmCleartext(server: ServerUrl) = throw IOException("disk full")
        }
        model = model(failing)
        model.refused(ApiError.CleartextNotConfirmed(url))

        model.accept()

        assertEquals(SessionTrustModel.State(ApiError.CleartextNotConfirmed(url), promptOpen = false), model.state.value)
        assertEquals(0, retries)
    }

    private fun model(trust: ServerTrust) =
        SessionTrustModel(trust, retry = { retries++ }, scope = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)

    /** The signed-in session's client, as Session builds it. */
    private fun client(httpClient: OkHttpClient = SharedHttpClient.instance) =
        TogglApiClient(url, ApiCredentials.ApiToken(TOKEN), httpClient, trustListener = model)

    /**
     * The shared client with a step that stands in for its trust manager: the
     * handshake with a server presenting [certificate] fails unless the user
     * pinned it, as PinningTrustManager makes it fail.
     */
    private fun handshakeAsIfServing(certificate: ServerCertificate): OkHttpClient =
        SharedHttpClient.instance.newBuilder()
            .addInterceptor { chain ->
                val pin = trust.pinnedSha256(certificate.host, certificate.port)
                if (pin != certificate.sha256) {
                    val refusal = UntrustedCertificateException(certificate, pinChanged = pin != null, cause = CertificateException("no trust anchor"))
                    throw SSLHandshakeException("handshake failed").apply { initCause(refusal) }
                }
                chain.proceed(chain.request())
            }
            .build()

    private fun me() = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body("""{"id":7}""")
        .build()

    private companion object {
        const val TOKEN = "0123456789abcdef0123456789abcdef"
        val CERTIFICATE = ServerCertificate("tokkl.test", 443, "ab".repeat(32), "CN=tokkl.test")
    }
}
