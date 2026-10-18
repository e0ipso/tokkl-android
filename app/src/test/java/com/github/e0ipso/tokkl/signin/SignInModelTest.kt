package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.UntrustedCertificateException
import com.github.e0ipso.tokkl.api.untrustedCertificate
import com.github.e0ipso.tokkl.session.AccountSettings
import java.io.IOException
import java.time.DayOfWeek
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The sign-in form against a MockWebServer. The Tokkl container in the
 * instrumented tests serves at the root and must not lock its account, so the
 * base path and 429 cases live here.
 */
class SignInModelTest {

    private val server = MockWebServer()
    private val saved = mutableListOf<Pair<ServerUrl, String>>()
    private val savedSettings = mutableListOf<AccountSettings>()
    private var saveFailure: IOException? = null
    private var now = 5_000_000L
    private val cooldowns = InMemoryCooldowns()
    private lateinit var trust: InMemoryServerTrust
    private lateinit var model: SignInModel

    @Before
    fun setUp() {
        server.start()
        // Most tests sign in to the MockWebServer's plain http URLs, which the user has confirmed here.
        trust = InMemoryServerTrust(ServerUrl.parse(baseUrl("/"))!!, ServerUrl.parse(baseUrl("/tokkl"))!!)
        ConfiguredTrust.readFrom(trust)
        model = model(trust)
    }

    @After
    fun tearDown() {
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    private fun model(trust: ServerTrust, httpClient: OkHttpClient = SharedHttpClient.instance): SignInModel {
        val signIn = SignIn(
            session = { url, token, settings ->
                saveFailure?.let { throw it }
                saved += url to token
                savedSettings += settings
            },
            httpClient = httpClient,
        )
        // Unconfined runs each attempt to the end inside submit().
        return SignInModel(signIn, trust, cooldowns, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, clock = { now })
    }

    /** The deadlines a [SignInCooldownStore] would hold, in memory. */
    private class InMemoryCooldowns : SignInCooldowns {
        val stored = mutableMapOf<String, Long>()

        override fun load(): Map<String, Long> = stored.toMap()

        override fun save(origin: String, deadline: Long) {
            stored[origin] = deadline
        }

        override fun clear(origin: String) {
            stored.remove(origin)
        }
    }

    @Test
    fun apiTokenSignInKeepsTheBasePathAndStoresServerAndToken() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        model.onServerUrlChange(" ${baseUrl("/tokkl/")} ")
        model.onApiTokenChange(" $TOKEN\n")

        model.submit()

        val request = server.takeRequest()
        assertEquals("/tokkl/api/v9/me", request.url.encodedPath)
        assertEquals(ApiCredentials.ApiToken(TOKEN).authorizationHeader, request.headers["Authorization"])
        assertEquals(listOf(ServerUrl.parse(baseUrl("/tokkl"))!! to TOKEN), saved)
        assertEquals(listOf(AccountSettings("Europe/Madrid", DayOfWeek.SUNDAY, 15.0)), savedSettings)
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun meWithoutTimeResolutionSignsInWithNoSnapping() {
        server.enqueue(me("""{"id":7,"api_token":"$TOKEN","timezone":"Europe/Madrid","beginning_of_week":1}"""))
        fillApiToken()

        model.submit()

        assertNull(model.state.value.failure)
        assertTrue(model.state.value.signedIn)
        assertEquals(listOf(AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, null)), savedSettings)
    }

    @Test
    fun emailAndPasswordSignInStoresOnlyTheTokenFromMe() {
        server.enqueue(me("""{"id":7,$SETTINGS,"email":"ana@example.com","api_token":"$TOKEN"}"""))
        model.onServerUrlChange(baseUrl("/"))
        model.onMethodChange(SignInMethod.EMAIL_PASSWORD)
        model.onEmailChange("ana@example.com")
        model.onPasswordChange(PASSWORD)

        model.submit()

        assertEquals(
            ApiCredentials.EmailPassword("ana@example.com", PASSWORD).authorizationHeader,
            server.takeRequest().headers["Authorization"],
        )
        assertEquals(listOf(ServerUrl.parse(baseUrl("/"))!! to TOKEN), saved)
        val state = model.state.value
        assertTrue(state.signedIn)
        assertEquals("the form keeps no secret after sign-in", SignInState(signedIn = true), state)
    }

    @Test
    fun passwordIsSentExactlyAsTyped() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        model.onServerUrlChange(baseUrl("/"))
        model.onMethodChange(SignInMethod.EMAIL_PASSWORD)
        model.onEmailChange(" ana@example.com ")
        model.onPasswordChange(" $PASSWORD ")

        model.submit()

        assertEquals(
            ApiCredentials.EmailPassword("ana@example.com", " $PASSWORD ").authorizationHeader,
            server.takeRequest().headers["Authorization"],
        )
    }

    @Test
    fun forbiddenIsABadCredentialsFailureAndStoresNothing() {
        server.enqueue(MockResponse.Builder().code(403).body("\"Incorrect username and/or password\"").build())
        fillApiToken()

        model.submit()

        val state = model.state.value
        assertEquals(SignInResult.BadCredentials, state.failure)
        assertFalse(state.submitting)
        assertFalse(state.signedIn)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun tooManyRequestsBlocksSubmitUntilRetryAfterPassesAndNeverRetriesAlone() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.RateLimited(30), model.state.value.failure)
        assertEquals(30, model.secondsUntilRetry())
        now += 29_001
        assertEquals(1, model.secondsUntilRetry())
        model.submit()
        assertEquals("submit is refused while the countdown runs", 1, server.requestCount)

        now += 999
        assertEquals(0, model.secondsUntilRetry())
        assertEquals("nothing retries when the countdown ends", 1, server.requestCount)
        assertFalse(model.state.value.signedIn)

        model.submit()
        assertEquals(2, server.requestCount)
        assertTrue(model.state.value.signedIn)
    }

    /** A new model, as after process death, for the same server still waits out the 429. */
    @Test
    fun recreatedModelWithinTheCooldownStillBlocksSignIn() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "60").build())
        fillApiToken()
        model.submit()
        assertEquals(60, model.secondsUntilRetry())

        now += 10_000
        val recreated = model(trust)
        recreated.onServerUrlChange(baseUrl("/"))
        recreated.onApiTokenChange(TOKEN)

        assertEquals(50, recreated.secondsUntilRetry())
        recreated.submit()
        assertEquals("no request during the cooldown", 1, server.requestCount)
    }

    @Test
    fun onlyTheServerOriginAndTheDeadlineAreStored() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        model.onServerUrlChange(baseUrl("/tokkl/"))
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(mapOf("http://${server.hostName}:${server.port}" to now + 30_000), cooldowns.stored)
    }

    @Test
    fun aPassedDeadlineIsGoneFromThePhone() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        fillApiToken()
        model.submit()

        now += 30_000
        model.cooldownEnded()

        assertEquals(0, model.secondsUntilRetry())
        assertNull(model.state.value.retryAt)
        assertTrue(cooldowns.stored.isEmpty())
    }

    @Test
    fun aStoredDeadlineThatHasPassedIsDroppedWhenTheModelStarts() {
        cooldowns.stored["http://${server.hostName}:${server.port}"] = now - 1

        model(trust)

        assertTrue(cooldowns.stored.isEmpty())
    }

    @Test
    fun aSuccessfulSignInForgetsTheDeadline() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        fillApiToken()
        model.submit()
        assertEquals(1, cooldowns.stored.size)

        now += 30_000
        model.submit()

        assertTrue(model.state.value.signedIn)
        assertTrue(cooldowns.stored.isEmpty())
    }

    @Test
    fun aDeadlineBlocksOnlyItsOwnServer() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        fillApiToken()
        model.submit()

        model.onServerUrlChange("https://other.example/tokkl")

        assertEquals(0, model.secondsUntilRetry())
        model.onServerUrlChange(baseUrl("/tokkl"))
        assertEquals(30, model.secondsUntilRetry())
    }

    @Test
    fun tooManyRequestsWithoutRetryAfterWaitsAMinute() {
        server.enqueue(MockResponse.Builder().code(429).build())
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.RateLimited(null), model.state.value.failure)
        assertEquals(60, model.secondsUntilRetry())
    }

    @Test
    fun incompleteFormSendsNothing() {
        model.submit()
        assertEquals(setOf(InputError.SERVER_URL, InputError.API_TOKEN), model.state.value.inputErrors)

        model.onServerUrlChange("https://ana:secret@example.com/tokkl")
        model.onMethodChange(SignInMethod.EMAIL_PASSWORD)
        model.onEmailChange("ana:x@example.com")
        model.submit()
        assertEquals(
            setOf(InputError.SERVER_URL, InputError.EMAIL_COLON, InputError.PASSWORD),
            model.state.value.inputErrors,
        )

        model.onEmailChange("ana@example.com")
        assertEquals("editing a field clears its errors", setOf(InputError.SERVER_URL, InputError.PASSWORD), model.state.value.inputErrors)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun meWithoutAnApiTokenFailsEmailAndPasswordSignIn() {
        server.enqueue(me("""{"id":7,$SETTINGS,"email":"ana@example.com"}"""))
        model.onServerUrlChange(baseUrl("/"))
        model.onMethodChange(SignInMethod.EMAIL_PASSWORD)
        model.onEmailChange("ana@example.com")
        model.onPasswordChange(PASSWORD)

        model.submit()

        assertEquals(SignInResult.NoApiToken, model.state.value.failure)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun answerThatIsNotAUserIsNotTokkl() {
        server.enqueue(MockResponse.Builder().code(200).body("<html>router login</html>").build())
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.NotTokkl, model.state.value.failure)
    }

    @Test
    fun notFoundKeepsItsStatus() {
        server.enqueue(MockResponse.Builder().code(404).build())
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.UnexpectedStatus(404), model.state.value.failure)
    }

    @Test
    fun redirectToAnotherHostIsRefusedAndStoresNothing() {
        val elsewhere = MockWebServer().apply { start() }
        try {
            val target = elsewhere.url("/api/v9/me")
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.toString()).build())
            fillApiToken()

            model.submit()

            assertEquals(SignInResult.HostNotAllowed("http://${target.host}:${target.port}"), model.state.value.failure)
            assertEquals(0, elsewhere.requestCount)
            assertTrue(saved.isEmpty())
        } finally {
            elsewhere.close()
        }
    }

    @Test
    fun storageFailureIsReported() {
        saveFailure = IOException("disk full")
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.StorageFailed, model.state.value.failure)
        assertFalse(model.state.value.signedIn)
    }

    @Test
    fun handshakeFailureFromThePinningTrustManagerCarriesTheCertificate() {
        val refusal = UntrustedCertificateException(CERTIFICATE, pinChanged = false, cause = CertificateException("no trust anchor"))
        val untrusted = SSLHandshakeException("handshake failed").apply { initCause(refusal) }
        val platformOnly = SSLHandshakeException("handshake failed").apply {
            initCause(CertificateException("Trust anchor for certification path not found."))
        }

        assertEquals(refusal, untrusted.untrustedCertificate())
        assertNull(platformOnly.untrustedCertificate())
        assertNull(IOException("refused").untrustedCertificate())
    }

    @Test
    fun untrustedCertificatePromptsAndTrustingItPinsThatCertificateThenSends() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        model = model(trust, handshakeAsIfServing(CERTIFICATE))
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.UntrustedCertificate(CERTIFICATE, changed = false), model.state.value.failure)
        assertEquals("no request before the user trusts the certificate", 0, server.requestCount)

        model.trustCertificate()

        assertEquals(mapOf("${CERTIFICATE.host}:${CERTIFICATE.port}" to CERTIFICATE.sha256), trust.pins)
        assertEquals(1, server.requestCount)
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun changedCertificatePromptsAgainAndCancelKeepsTheOldPin() {
        val old = "cd".repeat(32)
        trust.pins["${CERTIFICATE.host}:${CERTIFICATE.port}"] = old
        model = model(trust, handshakeAsIfServing(CERTIFICATE))
        fillApiToken()

        model.submit()
        assertEquals(SignInResult.UntrustedCertificate(CERTIFICATE, changed = true), model.state.value.failure)

        model.dismissPrompt()

        assertNull(model.state.value.failure)
        assertEquals(old, trust.pinnedSha256(CERTIFICATE.host, CERTIFICATE.port))
        assertEquals(0, server.requestCount)
    }

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

    @Test
    fun plainHttpServerShowsTheWarningAndSendsNothingUntilTheUserConfirms() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        val fresh = ServerUrl.parse(baseUrl("/fresh"))!!
        model.onServerUrlChange(fresh.toString())
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(SignInResult.CleartextNotConfirmed(fresh), model.state.value.failure)
        assertFalse(model.state.value.submitting)
        assertEquals("no request before the confirmation", 0, server.requestCount)

        model.confirmCleartext()

        assertTrue(trust.isCleartextConfirmed(fresh))
        assertEquals(1, server.requestCount)
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun dismissingTheWarningSendsAndStoresNothingAndTheNextSubmitAsksAgain() {
        val fresh = ServerUrl.parse(baseUrl("/fresh"))!!
        model.onServerUrlChange(fresh.toString())
        model.onApiTokenChange(TOKEN)
        model.submit()

        model.dismissPrompt()

        assertNull(model.state.value.failure)
        assertFalse(trust.isCleartextConfirmed(fresh))
        model.submit()
        assertEquals(SignInResult.CleartextNotConfirmed(fresh), model.state.value.failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anotherUrlAsksAgain() {
        // baseUrl("/tokkl") is confirmed; the same host under another path is a different URL.
        model.onServerUrlChange(baseUrl("/tokkl/other"))
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(SignInResult.CleartextNotConfirmed(ServerUrl.parse(baseUrl("/tokkl/other"))!!), model.state.value.failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun httpsServerNeverShowsTheWarning() {
        val nothingConfirmed = InMemoryServerTrust()
        ConfiguredTrust.readFrom(nothingConfirmed)
        model = model(nothingConfirmed)
        // The MockWebServer speaks plain HTTP, so the TLS handshake fails.
        model.onServerUrlChange(server.url("/").newBuilder().scheme("https").port(server.port).build().toString())
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(SignInResult.Unreachable, model.state.value.failure)
        assertTrue(nothingConfirmed.cleartextConfirmed.isEmpty())
    }

    @Test
    fun confirmationThatCannotBeStoredSendsNothing() {
        val failing = object : ServerTrust {
            override fun isCleartextConfirmed(server: ServerUrl) = false

            override fun confirmCleartext(server: ServerUrl) = throw IOException("disk full")

            override fun pinnedSha256(host: String, port: Int): String? = null

            override fun pinCertificate(certificate: ServerCertificate) = throw IOException("disk full")
        }
        ConfiguredTrust.readFrom(failing)
        model = model(failing)
        fillApiToken()
        model.submit()

        model.confirmCleartext()

        assertEquals(SignInResult.AnswerNotStored, model.state.value.failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun stateAndCredentialsNeverPrintSecrets() {
        model.onApiTokenChange(TOKEN)
        model.onPasswordChange(PASSWORD)

        val printed = listOf(
            model.state.value.toString(),
            SignInCredentials.ApiToken(TOKEN).toString(),
            SignInCredentials.EmailPassword("ana@example.com", PASSWORD).toString(),
        )

        for (text in printed) {
            assertFalse("a printed value holds the token", TOKEN in text)
            assertFalse("a printed value holds the password", PASSWORD in text)
        }
        assertNull(model.state.value.failure)
    }

    private fun fillApiToken() {
        model.onServerUrlChange(baseUrl("/"))
        model.onApiTokenChange(TOKEN)
    }

    private fun baseUrl(path: String): String = server.url(path).toString()

    private fun me(json: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(json)
        .build()

    private companion object {
        val CERTIFICATE = ServerCertificate("tokkl.test", 443, "ab".repeat(32), "CN=tokkl.test")

        const val TOKEN = "0123456789abcdef0123456789abcdef"
        const val PASSWORD = "correct horse:battery"

        /** The settings fields of `GET /api/v9/me`, which Toggl Track and Tokkl always send. */
        const val SETTINGS = """"timezone":"Europe/Madrid","beginning_of_week":0,"time_resolution_minutes":15"""
    }
}
