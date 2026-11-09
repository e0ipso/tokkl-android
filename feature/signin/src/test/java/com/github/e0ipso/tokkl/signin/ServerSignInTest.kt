package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.api.UntrustedCertificateException
import com.github.e0ipso.tokkl.api.instance
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.security.cert.CertificateException
import java.time.DayOfWeek
import javax.net.ssl.SSLHandshakeException

/**
 * One sign-in attempt against a MockWebServer, which shows the exact requests and can answer 429
 * with any Retry-After: what goes to the server, what each answer becomes and what is stored. The
 * form around it is SignInViewModelTest's. On the device, SignInUnderPathPrefixTest signs in to
 * the Tokkl container under a path prefix, and SignInRateLimitTest trips the container's own 429
 * with a throwaway account, so the shared account is never locked.
 */
class ServerSignInTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()
    private val saved = mutableListOf<Pair<ServerUrl, String>>()
    private val savedSettings = mutableListOf<AccountSettings>()
    private var saveFailure: IOException? = null
    private lateinit var trust: InMemoryServerTrust

    @Before
    fun setUp() {
        server.start()
        // Most tests sign in to the MockWebServer's plain http URLs, which the user has confirmed here.
        trust = InMemoryServerTrust(url("/"), url("/tokkl"))
        testApi.trust = trust
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Unconfined runs each attempt to the end inside [attempt]. */
    private fun signIn(
        httpClient: OkHttpClient = testApi.http.instance,
        signedInAccount: SignedInAccount = SignedInAccount { null },
    ) = ServerSignIn(
        session = { url, token, settings ->
            saveFailure?.let { throw it }
            saved += url to token
            savedSettings += settings
        },
        connector = testApi.connector(httpClient),
        configured = testApi.configured,
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        signedInAccount = signedInAccount,
    )

    private fun attempt(
        url: ServerUrl = url("/"),
        credentials: SignInCredentials = SignInCredentials.ApiToken(TOKEN),
        again: SignInAgain? = null,
        signIn: ServerSignIn = signIn(),
    ): SignInResult = runBlocking { signIn.attempt(url, credentials, again) }

    @Test
    fun apiTokenSignInKeepsTheBasePathAndStoresServerAndToken() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))

        assertEquals(SignInResult.SignedIn, attempt(url("/tokkl/")))

        val request = server.takeRequest()
        assertEquals("/tokkl/api/v9/me", request.url.encodedPath)
        assertEquals(ApiCredentials.ApiToken(TOKEN).authorizationHeader, request.headers["Authorization"])
        assertEquals(listOf(url("/tokkl") to TOKEN), saved)
        assertEquals(listOf(AccountSettings("Europe/Madrid", DayOfWeek.SUNDAY, 15.0, userId = 7)), savedSettings)
    }

    @Test
    fun meWithoutTimeResolutionSignsInWithNoSnapping() {
        server.enqueue(me("""{"id":7,"api_token":"$TOKEN","timezone":"Europe/Madrid","beginning_of_week":1}"""))

        assertEquals(SignInResult.SignedIn, attempt())

        assertEquals(listOf(AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, null, userId = 7)), savedSettings)
    }

    @Test
    fun emailAndPasswordSignInSendsThemAsGivenAndStoresOnlyTheTokenFromMe() {
        server.enqueue(me("""{"id":7,$SETTINGS,"email":"ana@example.com","api_token":"$TOKEN"}"""))

        val result = attempt(credentials = SignInCredentials.EmailPassword("ana@example.com", " $PASSWORD "))

        assertEquals(SignInResult.SignedIn, result)
        assertEquals(
            ApiCredentials.EmailPassword("ana@example.com", " $PASSWORD ").authorizationHeader,
            server.takeRequest().headers["Authorization"],
        )
        assertEquals(listOf(url("/") to TOKEN), saved)
    }

    @Test
    fun aFormOpenedBeforeSettingsLoadStillChecksThePersistedIdentity() {
        server.enqueue(me("""{"id":8,$SETTINGS,"api_token":"$TOKEN"}"""))
        val signIn = signIn(signedInAccount = { SignInAgain(url("/"), 7) })

        assertEquals(SignInResult.OtherAccount, attempt(again = SignInAgain(url("/"), null), signIn = signIn))
        assertTrue(saved.isEmpty())
    }

    @Test
    fun aRestoredFormStillChecksThePersistedAccountBeforeStoringCredentials() {
        server.enqueue(me("""{"id":8,$SETTINGS,"api_token":"$TOKEN"}"""))
        val signIn = signIn(signedInAccount = { SignInAgain(url("/"), 7) })

        assertEquals(SignInResult.OtherAccount, attempt(signIn = signIn))
        assertTrue(saved.isEmpty())
    }

    @Test
    fun signingInAgainRefusesAnotherServerWithoutSendingTheToken() {
        val other = ServerUrl.parse("https://other.example.com/")!!

        assertEquals(SignInResult.OtherAccount, attempt(other, again = SignInAgain(url("/"), 7)))

        assertEquals(0, server.requestCount)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun signingInAgainRefusesAnotherAccountAndKeepsTheExistingSession() {
        server.enqueue(me("""{"id":8,$SETTINGS,"api_token":"$TOKEN"}"""))

        assertEquals(SignInResult.OtherAccount, attempt(again = SignInAgain(url("/"), 7)))

        assertTrue(saved.isEmpty())
        assertTrue(savedSettings.isEmpty())
    }

    @Test
    fun signingInAgainAcceptsTheSameAccountWithItsNewToken() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))

        assertEquals(SignInResult.SignedIn, attempt(again = SignInAgain(url("/"), 7)))

        assertEquals(TOKEN, saved.single().second)
        assertEquals(7L, savedSettings.single().userId)
    }

    @Test
    fun forbiddenIsABadCredentialsFailureAndStoresNothing() {
        server.enqueue(MockResponse.Builder().code(403).body("\"Incorrect username and/or password\"").build())

        assertEquals(SignInResult.BadCredentials, attempt())

        assertTrue(saved.isEmpty())
    }

    @Test
    fun tooManyRequestsCarriesRetryAfterOrNone() {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        server.enqueue(MockResponse.Builder().code(429).build())

        assertEquals(SignInResult.RateLimited(30), attempt())
        assertEquals(SignInResult.RateLimited(null), attempt())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun meWithoutAnApiTokenFailsEmailAndPasswordSignIn() {
        server.enqueue(me("""{"id":7,$SETTINGS,"email":"ana@example.com"}"""))

        val result = attempt(credentials = SignInCredentials.EmailPassword("ana@example.com", PASSWORD))

        assertEquals(SignInResult.NoApiToken, result)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun answerThatIsNotAUserIsNotTokkl() {
        server.enqueue(MockResponse.Builder().code(200).body("<html>router login</html>").build())

        assertEquals(SignInResult.NotTokkl, attempt())
    }

    /** Toggl Track and Tokkl always send both fields, and the week starts on a day from 0 to 6. */
    @Test
    fun meWithoutTimezoneOrWeekStartIsNotTokklAndStoresNothing() {
        val bodies = listOf(
            """{"id":7,"api_token":"$TOKEN","beginning_of_week":1}""",
            """{"id":7,"api_token":"$TOKEN","timezone":"Europe/Madrid"}""",
            """{"id":7,"api_token":"$TOKEN","timezone":"Europe/Madrid","beginning_of_week":7}""",
        )

        for (body in bodies) {
            server.enqueue(me(body))
            assertEquals(body, SignInResult.NotTokkl, attempt())
        }
        assertEquals(bodies.size, server.requestCount)
        assertTrue(saved.isEmpty())
        assertTrue(savedSettings.isEmpty())
    }

    @Test
    fun notFoundKeepsItsStatus() {
        server.enqueue(MockResponse.Builder().code(404).build())

        assertEquals(SignInResult.UnexpectedStatus(404), attempt())
    }

    @Test
    fun redirectToAnotherHostIsRefusedAndStoresNothing() {
        val elsewhere = MockWebServer().apply { start() }
        try {
            val target = elsewhere.url("/api/v9/me")
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.toString()).build())

            assertEquals(SignInResult.HostNotAllowed("http://${target.host}:${target.port}"), attempt())

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

        assertEquals(SignInResult.StorageFailed, attempt())
    }

    @Test
    fun anUntrustedCertificateSendsNothingUntilItIsPinned() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        val signIn = signIn(handshakeAsIfServing(CERTIFICATE))

        assertEquals(SignInResult.UntrustedCertificate(CERTIFICATE, changed = false), attempt(signIn = signIn))
        assertEquals("no request before the user trusts the certificate", 0, server.requestCount)

        trust.pinCertificate(CERTIFICATE)

        assertEquals(SignInResult.SignedIn, attempt(signIn = signIn))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aChangedCertificateSaysSo() {
        trust.pins["${CERTIFICATE.host}:${CERTIFICATE.port}"] = "cd".repeat(32)

        val result = attempt(signIn = signIn(handshakeAsIfServing(CERTIFICATE)))

        assertEquals(SignInResult.UntrustedCertificate(CERTIFICATE, changed = true), result)
        assertEquals(0, server.requestCount)
    }

    /**
     * The shared client with a step that stands in for its trust manager: the
     * handshake with a server presenting [certificate] fails unless the user
     * pinned it, as PinningTrustManager makes it fail.
     */
    private fun handshakeAsIfServing(certificate: ServerCertificate): OkHttpClient = testApi.http.instance.newBuilder()
        .addInterceptor { chain ->
            val pin = trust.pinnedSha256(certificate.host, certificate.port)
            if (pin != certificate.sha256) {
                val refusal = UntrustedCertificateException(
                    certificate,
                    pinChanged = pin != null,
                    cause = CertificateException("no trust anchor"),
                )
                throw SSLHandshakeException("handshake failed").apply { initCause(refusal) }
            }
            chain.proceed(chain.request())
        }
        .build()

    @Test
    fun aPlainHttpServerGetsNothingUntilTheUserConfirmsIt() {
        server.enqueue(me("""{"id":7,$SETTINGS,"api_token":"$TOKEN"}"""))
        val fresh = url("/fresh")

        assertEquals(SignInResult.CleartextNotConfirmed(fresh), attempt(fresh))
        assertEquals("no request before the confirmation", 0, server.requestCount)

        trust.confirmCleartext(fresh)

        assertEquals(SignInResult.SignedIn, attempt(fresh))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun anotherUrlAsksAgain() {
        // url("/tokkl") is confirmed; the same host under another path is a different URL.
        val other = url("/tokkl/other")

        assertEquals(SignInResult.CleartextNotConfirmed(other), attempt(other))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun httpsServerNeverShowsTheWarning() {
        val nothingConfirmed = InMemoryServerTrust()
        testApi.trust = nothingConfirmed
        // The MockWebServer speaks plain HTTP, so the TLS handshake fails.
        val https = ServerUrl.parse(server.url("/").newBuilder().scheme("https").port(server.port).build().toString())!!

        assertEquals(SignInResult.Unreachable, attempt(https))
        assertTrue(nothingConfirmed.cleartextConfirmed.isEmpty())
    }

    private fun url(path: String): ServerUrl = ServerUrl.parse(server.url(path).toString())!!

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
