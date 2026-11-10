package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.session.CountingRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The sign-in form over a [SignIn] that answers in memory: what the form sends, how it shows each
 * answer, its 429 countdown and the user's answers to its warnings. What one attempt sends to the
 * server and what each answer becomes is ServerSignInTest's.
 */
class SignInViewModelTest {

    private var now = 5_000_000L
    private val cooldowns = InMemoryCooldowns()
    private val refreshes = CountingRefresh()
    private val signIn = AnsweringSignIn()
    private lateinit var trust: InMemoryServerTrust
    private lateinit var model: SignInViewModel

    @Before
    fun setUp() {
        // Most tests sign in to plain http URLs, which the user has confirmed here.
        trust = InMemoryServerTrust(SERVER, ServerUrl.parse(TOKKL)!!)
        model = model(trust)
    }

    /** Unconfined runs each attempt to the end inside submit(). */
    private fun model(trust: ServerTrust): SignInViewModel = SignInViewModel(
        signIn,
        trust,
        cooldowns,
        CoroutineScope(Dispatchers.Unconfined),
        Dispatchers.Unconfined,
        clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = this

            override fun instant(): Instant = Instant.ofEpochMilli(now)
        },
        refreshes = refreshes,
    )

    /** One attempt the form made, with its credentials as text, since they print no secret. */
    private data class Attempt(val server: ServerUrl, val credentials: String, val again: SignInAgain?)

    /**
     * A sign-in that answers each attempt with [answer], which by default asks for the user's
     * answer to a plain http URL or an unpinned certificate as the shared client does, and
     * otherwise signs in.
     */
    private inner class AnsweringSignIn : SignIn {
        val attempts = mutableListOf<Attempt>()
        var answer: (ServerUrl) -> SignInResult = { SignInResult.SignedIn }

        /** The certificate the server presents, which the user has to pin before anything is sent. */
        var certificate: ServerCertificate? = null

        override suspend fun attempt(
            server: ServerUrl,
            credentials: SignInCredentials,
            again: SignInAgain?,
        ): SignInResult {
            attempts += Attempt(server, credentials.text(), again)
            val presented = certificate
            return when {
                server.toString().startsWith("http:") && !trust.isCleartextConfirmed(server) ->
                    SignInResult.CleartextNotConfirmed(server)

                presented != null && trust.pinnedSha256(presented.host, presented.port) != presented.sha256 ->
                    SignInResult.UntrustedCertificate(
                        presented,
                        changed = trust.pinnedSha256(presented.host, presented.port) != null,
                    )

                else -> answer(server)
            }
        }
    }

    private fun SignInCredentials.text(): String = when (this) {
        is SignInCredentials.ApiToken -> "token $token"
        is SignInCredentials.EmailPassword -> "email $email password $password"
    }

    private class InMemoryCooldowns : SignInCooldowns {
        val stored = mutableMapOf<String, Long>()

        override suspend fun load(): Map<String, Long> = stored.toMap()

        override suspend fun save(origin: String, deadline: Long) {
            stored[origin] = deadline
        }

        override suspend fun clear(origin: String) {
            stored.remove(origin)
        }
    }

    @Test
    fun apiTokenSignInSendsTheTrimmedServerAndTokenAndKeepsNoSecret() {
        model.onServerUrlChange(" $TOKKL/ ")
        model.onApiTokenChange(" $TOKEN\n")

        model.submit()

        assertEquals(listOf(Attempt(ServerUrl.parse(TOKKL)!!, "token $TOKEN", null)), signIn.attempts)
        assertEquals("the form keeps no secret after sign-in", SignInState(signedIn = true), model.state.value)
        assertEquals("the account loads once signed in", 1, refreshes.refreshes)
    }

    /** A URL copied from the browser's address bar signs in to the server it names. */
    @Test
    fun aQueryAndAFragmentInTheServerUrlAreDropped() {
        model.onServerUrlChange("$TOKKL/?from=2026-10-01#week")
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(ServerUrl.parse(TOKKL)!!, signIn.attempts.single().server)
        assertTrue(model.state.value.signedIn)
    }

    /** The app stores the URL in plain text, so a user name or password in it stays refused. */
    @Test
    fun aUserNameOrPasswordInTheServerUrlIsRefused() {
        for (url in listOf("https://ana@example.com/tokkl", "https://ana:secret@example.com/tokkl?from=2026-10-01")) {
            model.onServerUrlChange(url)
            model.onApiTokenChange(TOKEN)

            model.submit()

            assertEquals(url, setOf(InputError.SERVER_URL), model.state.value.inputErrors)
        }
        assertEquals("nothing sent", emptyList<Attempt>(), signIn.attempts)
    }

    @Test
    fun emailAndPasswordGoAsTypedWithTheEmailTrimmed() {
        model.onServerUrlChange(SERVER.toString())
        model.onMethodChange(SignInMethod.EMAIL_PASSWORD)
        model.onEmailChange(" ana@example.com ")
        model.onPasswordChange(" $PASSWORD ")

        model.submit()

        assertEquals("email ana@example.com password  $PASSWORD ", signIn.attempts.single().credentials)
        assertEquals(SignInState(signedIn = true), model.state.value)
    }

    @Test
    fun signingInAgainSendsTheAccountAndShowsAnotherAccountsRefusal() {
        val again = SignInAgain(SERVER, 7)
        signIn.answer = { SignInResult.OtherAccount }
        model.signInAgain(again)
        assertEquals("the server is filled in", SERVER.toString(), model.state.value.serverUrl)
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(again, signIn.attempts.single().again)
        assertEquals(SignInResult.OtherAccount, model.state.value.failure)
        assertFalse(model.state.value.signedIn)
    }

    @Test
    fun aFailureShowsAndTheFormCanSendAgain() {
        signIn.answer = { SignInResult.BadCredentials }
        fillApiToken()

        model.submit()

        val state = model.state.value
        assertEquals(SignInResult.BadCredentials, state.failure)
        assertFalse(state.submitting)
        assertFalse(state.signedIn)
        assertEquals("nothing loads without a sign-in", 0, refreshes.refreshes)
        signIn.answer = { SignInResult.SignedIn }
        model.submit()
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun aStorageFailureShows() {
        signIn.answer = { SignInResult.StorageFailed }
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.StorageFailed, model.state.value.failure)
        assertFalse(model.state.value.signedIn)
    }

    @Test
    fun tooManyRequestsBlocksSubmitUntilRetryAfterPassesAndNeverRetriesAlone() {
        signIn.answer = { SignInResult.RateLimited(30) }
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.RateLimited(30), model.state.value.failure)
        assertEquals(30, model.secondsUntilRetry())
        now += 29_001
        assertEquals(1, model.secondsUntilRetry())
        model.submit()
        assertEquals("submit is refused while the countdown runs", 1, signIn.attempts.size)

        now += 999
        assertEquals(0, model.secondsUntilRetry())
        assertEquals("nothing retries when the countdown ends", 1, signIn.attempts.size)
        assertFalse(model.state.value.signedIn)

        signIn.answer = { SignInResult.SignedIn }
        model.submit()
        assertEquals(2, signIn.attempts.size)
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun recreatedModelWithinTheCooldownStillBlocksSignIn() {
        signIn.answer = { SignInResult.RateLimited(60) }
        fillApiToken()
        model.submit()
        assertEquals(60, model.secondsUntilRetry())

        now += 10_000
        val recreated = model(trust)
        recreated.onServerUrlChange(SERVER.toString())
        recreated.onApiTokenChange(TOKEN)

        assertEquals(50, recreated.secondsUntilRetry())
        recreated.submit()
        assertEquals("no attempt during the cooldown", 1, signIn.attempts.size)
    }

    @Test
    fun onlyTheServerOriginAndTheDeadlineAreStored() {
        signIn.answer = { SignInResult.RateLimited(30) }
        model.onServerUrlChange("$TOKKL/")
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(mapOf(SERVER.origin to now + 30_000), cooldowns.stored)
    }

    @Test
    fun aPassedDeadlineIsGoneFromThePhone() {
        signIn.answer = { SignInResult.RateLimited(30) }
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
        cooldowns.stored[SERVER.origin] = now - 1

        model(trust)

        assertTrue(cooldowns.stored.isEmpty())
    }

    @Test
    fun aSuccessfulSignInForgetsTheDeadline() {
        signIn.answer = { SignInResult.RateLimited(30) }
        fillApiToken()
        model.submit()
        assertEquals(1, cooldowns.stored.size)

        now += 30_000
        signIn.answer = { SignInResult.SignedIn }
        model.submit()

        assertTrue(model.state.value.signedIn)
        assertTrue(cooldowns.stored.isEmpty())
    }

    @Test
    fun aDeadlineBlocksOnlyItsOwnServer() {
        signIn.answer = { SignInResult.RateLimited(30) }
        fillApiToken()
        model.submit()

        model.onServerUrlChange("https://other.example/tokkl")

        assertEquals(0, model.secondsUntilRetry())
        model.onServerUrlChange(TOKKL)
        assertEquals(30, model.secondsUntilRetry())
    }

    @Test
    fun tooManyRequestsWithoutRetryAfterWaitsAMinute() {
        signIn.answer = { SignInResult.RateLimited(null) }
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
        assertEquals(
            "editing a field clears its errors",
            setOf(InputError.SERVER_URL, InputError.PASSWORD),
            model.state.value.inputErrors,
        )
        assertEquals(emptyList<Attempt>(), signIn.attempts)
    }

    @Test
    fun untrustedCertificatePromptsAndTrustingItPinsThatCertificateThenSends() {
        signIn.certificate = CERTIFICATE
        fillApiToken()

        model.submit()

        assertEquals(SignInResult.UntrustedCertificate(CERTIFICATE, changed = false), model.state.value.failure)

        model.trustCertificate()

        assertEquals(mapOf("${CERTIFICATE.host}:${CERTIFICATE.port}" to CERTIFICATE.sha256), trust.pins)
        assertEquals("the form sends again once pinned", 2, signIn.attempts.size)
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun changedCertificatePromptsAgainAndCancelKeepsTheOldPin() {
        val old = "cd".repeat(32)
        trust.pins["${CERTIFICATE.host}:${CERTIFICATE.port}"] = old
        signIn.certificate = CERTIFICATE
        fillApiToken()

        model.submit()
        assertEquals(SignInResult.UntrustedCertificate(CERTIFICATE, changed = true), model.state.value.failure)

        model.dismissPrompt()

        assertNull(model.state.value.failure)
        assertEquals(old, trust.pinnedSha256(CERTIFICATE.host, CERTIFICATE.port))
        assertEquals(1, signIn.attempts.size)
    }

    @Test
    fun plainHttpServerShowsTheWarningAndSendsAgainOnceTheUserConfirms() {
        val fresh = ServerUrl.parse("$HTTP/fresh")!!
        model.onServerUrlChange(fresh.toString())
        model.onApiTokenChange(TOKEN)

        model.submit()

        assertEquals(SignInResult.CleartextNotConfirmed(fresh), model.state.value.failure)
        assertFalse(model.state.value.submitting)

        model.confirmCleartext()

        assertTrue(trust.isCleartextConfirmed(fresh))
        assertEquals(2, signIn.attempts.size)
        assertTrue(model.state.value.signedIn)
    }

    @Test
    fun dismissingTheWarningStoresNothingAndTheNextSubmitAsksAgain() {
        val fresh = ServerUrl.parse("$HTTP/fresh")!!
        model.onServerUrlChange(fresh.toString())
        model.onApiTokenChange(TOKEN)
        model.submit()

        model.dismissPrompt()

        assertNull(model.state.value.failure)
        assertFalse(trust.isCleartextConfirmed(fresh))
        model.submit()
        assertEquals(SignInResult.CleartextNotConfirmed(fresh), model.state.value.failure)
    }

    @Test
    fun confirmationThatCannotBeStoredSendsNothing() {
        val failing = object : ServerTrust {
            override fun isCleartextConfirmed(server: ServerUrl) = false

            override fun confirmCleartext(server: ServerUrl) = throw IOException("disk full")

            override fun pinnedSha256(host: String, port: Int): String? = null

            override fun pinCertificate(certificate: ServerCertificate) = throw IOException("disk full")
        }
        model = model(failing)
        signIn.answer = { SignInResult.CleartextNotConfirmed(it) }
        fillApiToken()
        model.submit()

        model.confirmCleartext()

        assertEquals(SignInResult.AnswerNotStored, model.state.value.failure)
        assertEquals("nothing sent again", 1, signIn.attempts.size)
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
        model.onServerUrlChange(SERVER.toString())
        model.onApiTokenChange(TOKEN)
    }

    private companion object {
        val CERTIFICATE = ServerCertificate("tokkl.test", 443, "ab".repeat(32), "CN=tokkl.test")

        const val HTTP = "http://tokkl.test:8080"

        /** A plain http server the user has confirmed. */
        val SERVER: ServerUrl = ServerUrl.parse("$HTTP/")!!

        /** The same server under a path prefix, which the user has confirmed too. */
        const val TOKKL = "$HTTP/tokkl"

        const val TOKEN = "0123456789abcdef0123456789abcdef"
        const val PASSWORD = "correct horse:battery"
    }
}
