package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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
    private var saveFailure: IOException? = null
    private var now = 5_000_000L
    private lateinit var model: SignInModel

    @Before
    fun setUp() {
        server.start()
        val signIn = SignIn(session = { url, token -> saveFailure?.let { throw it }; saved += url to token })
        // Unconfined runs each attempt to the end inside submit().
        model = SignInModel(signIn, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, clock = { now })
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun apiTokenSignInKeepsTheBasePathAndStoresServerAndToken() {
        server.enqueue(me("""{"id":7,"api_token":"$TOKEN"}"""))
        model.onServerUrlChange(" ${baseUrl("/tokkl/")} ")
        model.onApiTokenChange(" $TOKEN\n")

        model.submit()

        val request = server.takeRequest()
        assertEquals("/tokkl/api/v9/me", request.url.encodedPath)
        assertEquals(ApiCredentials.ApiToken(TOKEN).authorizationHeader, request.headers["Authorization"])
        assertEquals(listOf(ServerUrl.parse(baseUrl("/tokkl"))!! to TOKEN), saved)
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun emailAndPasswordSignInStoresOnlyTheTokenFromMe() {
        server.enqueue(me("""{"id":7,"email":"ana@example.com","api_token":"$TOKEN"}"""))
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
        server.enqueue(me("""{"id":7,"api_token":"$TOKEN"}"""))
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
        server.enqueue(me("""{"id":7,"api_token":"$TOKEN"}"""))
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
        server.enqueue(me("""{"id":7,"email":"ana@example.com"}"""))
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
        server.enqueue(me("""{"id":7,"api_token":"$TOKEN"}"""))
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.StorageFailed, model.state.value.failure)
        assertFalse(model.state.value.signedIn)
    }

    @Test
    fun handshakeFailureFromAnUntrustedChainIsAnUntrustedCertificate() {
        val untrusted = SSLHandshakeException("handshake failed").apply {
            initCause(CertificateException("Trust anchor for certification path not found."))
        }

        assertTrue(untrusted.isUntrustedCertificate())
        assertFalse(SSLHandshakeException("protocol version").isUntrustedCertificate())
        assertFalse(IOException("refused").isUntrustedCertificate())
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
        const val TOKEN = "0123456789abcdef0123456789abcdef"
        const val PASSWORD = "correct horse:battery"
    }
}
