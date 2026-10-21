package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SignInMethod { API_TOKEN, EMAIL_PASSWORD }

/** A field the user has to fix before the app sends anything. */
enum class InputError { SERVER_URL, API_TOKEN, EMAIL, EMAIL_COLON, PASSWORD }

/**
 * What the sign-in screen shows. The secrets live in memory only, never in
 * saved instance state, and `toString` leaves them out.
 *
 * [retryAt] is the [SignInModel] clock time at which a 429 from the server in
 * [serverUrl] stops blocking sign-in, or null when nothing blocks it. [again] is the signed-in
 * account while the user signs in again after the server refused its token, and null for a
 * first sign-in.
 */
data class SignInState(
    val serverUrl: String = "",
    val method: SignInMethod = SignInMethod.API_TOKEN,
    val apiToken: String = "",
    val email: String = "",
    val password: String = "",
    val inputErrors: Set<InputError> = emptySet(),
    val submitting: Boolean = false,
    val failure: SignInResult? = null,
    val retryAt: Long? = null,
    val signedIn: Boolean = false,
    val again: SignInAgain? = null,
) {
    override fun toString(): String =
        "SignInState(serverUrl=$serverUrl, method=$method, inputErrors=$inputErrors, submitting=$submitting, " +
            "failure=$failure, retryAt=$retryAt, signedIn=$signedIn, again=$again)"
}

/**
 * The sign-in form and its attempts. Each submit sends one request, and only
 * when the user asks: after a 429 the model refuses to submit to that server
 * until `Retry-After` has passed, and it never retries on its own. The
 * deadline persists in [cooldowns] by server origin, so a model in a new
 * process keeps the button disabled too. It goes once it passes or a sign-in
 * to that server succeeds.
 *
 * A plain `http://` server gets nothing until the user confirms the warning
 * that the token or password travels unencrypted. The shared client refuses
 * the request, the attempt ends with [SignInResult.CleartextNotConfirmed], and
 * [confirmCleartext] records the confirmation in [trust] and sends it again.
 * A certificate that no system or user CA trusts ends the attempt with
 * [SignInResult.UntrustedCertificate] before any credential is sent, and
 * [trustCertificate] pins the certificate the prompt showed and sends it again.
 *
 * @param trust where the user's answers go. The shared client checks it
 *   before every request and in every TLS handshake.
 * @param cooldowns where the 429 deadlines persist.
 * @param clock wall-clock milliseconds, such as `System.currentTimeMillis`,
 *   since a deadline has to outlast the process and a reboot.
 * @param scope runs the model's coroutines on one thread, the main thread in
 *   the app.
 * @param io where the blocking [SignIn.attempt] and the writes to [trust] and
 *   [cooldowns] run.
 */
class SignInModel(
    private val signIn: SignIn,
    private val trust: ServerTrust,
    private val cooldowns: SignInCooldowns,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: () -> Long,
) {

    private val mutableState = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = mutableState.asStateFlow()

    /** The 429 deadlines by server origin. Used on [scope]'s thread only. */
    private val deadlines = mutableMapOf<String, Long>()

    /** Keeps the writes to [cooldowns] in the order the model makes them. */
    private val cooldownWrites = Mutex()

    /** Reads the stored deadlines and drops the ones that have passed. Submit waits for it. */
    private val restoring: Job = scope.launch {
        val stored = withContext(io) { cooldowns.load() }
        val now = clock()
        for ((origin, deadline) in stored) {
            if (deadline > now) deadlines[origin] = deadline else forget(origin)
        }
        mutableState.update { it.copy(retryAt = deadlineFor(it.serverUrl)) }
    }

    fun onServerUrlChange(value: String) = edit(InputError.SERVER_URL) { copy(serverUrl = value, retryAt = deadlineFor(value)) }

    fun onMethodChange(value: SignInMethod) = mutableState.update { it.copy(method = value, inputErrors = emptySet()) }

    fun onApiTokenChange(value: String) = edit(InputError.API_TOKEN) { copy(apiToken = value) }

    fun onEmailChange(value: String) = edit(InputError.EMAIL, InputError.EMAIL_COLON) { copy(email = value) }

    fun onPasswordChange(value: String) = edit(InputError.PASSWORD) { copy(password = value) }

    /** Whole seconds until sign-in opens again after a 429, or 0 when it is open. */
    fun secondsUntilRetry(): Long {
        val retryAt = mutableState.value.retryAt ?: return 0
        val left = retryAt - clock()
        return if (left <= 0) 0 else (left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    }

    /** Validates the form and, when it is complete and nothing blocks it, sends one sign-in request. */
    fun submit() {
        val current = mutableState.value
        if (current.submitting || current.signedIn || secondsUntilRetry() > 0) return
        val server = ServerUrl.parse(current.serverUrl.trim())
        val credentials = credentials(current)
        val errors = inputErrors(current, server)
        if (errors.isNotEmpty() || server == null || credentials == null) {
            mutableState.update { it.copy(inputErrors = errors) }
            return
        }
        mutableState.update { it.copy(inputErrors = emptySet(), submitting = true, failure = null, retryAt = null) }
        scope.launch {
            // A deadline from an earlier process may still be loading.
            restoring.join()
            val stored = deadlines[server.origin]
            if (stored != null && stored > clock()) {
                mutableState.update { it.copy(submitting = false, retryAt = stored) }
                return@launch
            }
            if (stored != null) forget(server.origin)
            val result = withContext(io) { signIn.attempt(server, credentials, current.again) }
            val retryAt = when (result) {
                is SignInResult.RateLimited ->
                    (clock() + (result.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS) * MILLIS_PER_SECOND)
                        .also { remember(server.origin, it) }
                SignInResult.SignedIn -> null.also { forget(server.origin) }
                else -> null
            }
            mutableState.update { it.after(result, retryAt) }
        }
    }

    /**
     * The countdown reached zero. Forgets every deadline that has passed, on
     * the phone too.
     */
    fun cooldownEnded() {
        val now = clock()
        deadlines.filterValues { it <= now }.keys.forEach(::forget)
        mutableState.update { it.copy(retryAt = deadlineFor(it.serverUrl)) }
    }

    /**
     * The user confirmed the warning about a plain `http://` server. Records
     * the confirmation for that URL, then sends the attempt.
     */
    fun confirmCleartext() {
        val warning = mutableState.value.failure as? SignInResult.CleartextNotConfirmed ?: return
        storeAnswerThenSubmit { trust.confirmCleartext(warning.server) }
    }

    /**
     * The user trusts the certificate that the prompt showed. Pins exactly
     * that certificate, by its SHA-256, then sends the attempt. A server that
     * now presents another certificate fails the handshake and prompts again.
     */
    fun trustCertificate() {
        val prompt = mutableState.value.failure as? SignInResult.UntrustedCertificate ?: return
        storeAnswerThenSubmit { trust.pinCertificate(prompt.certificate) }
    }

    /** Closes a warning or prompt without sending or storing anything. */
    fun dismissPrompt() {
        mutableState.update { if (it.failure.isPrompt()) it.copy(failure = null) else it }
    }

    private fun storeAnswerThenSubmit(store: () -> Unit) {
        if (mutableState.value.submitting) return
        mutableState.update { it.copy(submitting = true, failure = null) }
        scope.launch {
            val stored = withContext(io) {
                try {
                    store()
                    true
                } catch (e: IOException) {
                    false
                }
            }
            mutableState.update { it.copy(submitting = false, failure = SignInResult.AnswerNotStored.takeUnless { stored }) }
            if (stored) submit()
        }
    }

    private fun SignInResult?.isPrompt(): Boolean =
        this is SignInResult.CleartextNotConfirmed || this is SignInResult.UntrustedCertificate

    /**
     * Starts the form over to sign in again to [account], which the server stopped accepting
     * (docs/PRD.md O6), with its server filled in. A sign-in to another server or account fails
     * with [SignInResult.OtherAccount].
     */
    fun signInAgain(account: SignInAgain) {
        val url = account.server.toString()
        mutableState.value = SignInState(serverUrl = url, again = account, retryAt = deadlineFor(url))
    }

    /** Clears the form after the app has moved on from a successful sign-in. */
    fun reset() {
        mutableState.value = SignInState()
    }

    private fun SignInState.after(result: SignInResult, retryAt: Long?): SignInState =
        when (result) {
            // Drops every secret the form held.
            SignInResult.SignedIn -> SignInState(signedIn = true)
            else -> copy(submitting = false, failure = result, retryAt = retryAt)
        }

    /** The deadline for the server in the form, or null when none blocks it. */
    private fun deadlineFor(serverUrl: String): Long? =
        ServerUrl.parse(serverUrl.trim())?.let { deadlines[it.origin] }

    private fun remember(origin: String, deadline: Long) {
        deadlines[origin] = deadline
        writeCooldowns { save(origin, deadline) }
    }

    private fun forget(origin: String) {
        deadlines.remove(origin)
        writeCooldowns { clear(origin) }
    }

    /**
     * Writes to [cooldowns] off the main thread, one at a time and in call
     * order: each call takes the lock before it first suspends. A failed
     * write leaves the deadline in memory, so this process still waits.
     */
    private fun writeCooldowns(write: SignInCooldowns.() -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cooldownWrites.withLock {
                withContext(io) {
                    try {
                        cooldowns.write()
                    } catch (e: IOException) {
                        // Nothing to show: the deadline still holds until the process ends.
                    }
                }
            }
        }
    }

    private fun edit(vararg clears: InputError, change: SignInState.() -> SignInState) =
        mutableState.update { it.change().copy(inputErrors = it.inputErrors - clears.toSet()) }

    private fun inputErrors(state: SignInState, server: ServerUrl?): Set<InputError> = buildSet {
        if (server == null) add(InputError.SERVER_URL)
        when (state.method) {
            SignInMethod.API_TOKEN -> if (state.apiToken.isBlank()) add(InputError.API_TOKEN)
            SignInMethod.EMAIL_PASSWORD -> {
                val email = state.email.trim()
                if (email.isEmpty()) add(InputError.EMAIL)
                // Basic auth ends the user name at the first colon.
                if (':' in email) add(InputError.EMAIL_COLON)
                if (state.password.isEmpty()) add(InputError.PASSWORD)
            }
        }
    }

    /** Trims what a paste may wrap around the token or email. A password is sent exactly as typed. */
    private fun credentials(state: SignInState): SignInCredentials? =
        when (state.method) {
            SignInMethod.API_TOKEN -> state.apiToken.trim().takeIf { it.isNotEmpty() }?.let(SignInCredentials::ApiToken)
            SignInMethod.EMAIL_PASSWORD -> {
                val email = state.email.trim()
                if (email.isEmpty() || ':' in email || state.password.isEmpty()) {
                    null
                } else {
                    SignInCredentials.EmailPassword(email, state.password)
                }
            }
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L

        /** Tokkl always sends `Retry-After`. Without one, wait a minute before the next attempt. */
        const val DEFAULT_RETRY_AFTER_SECONDS = 60L
    }
}
