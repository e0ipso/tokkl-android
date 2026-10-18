package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ServerUrl
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SignInMethod { API_TOKEN, EMAIL_PASSWORD }

/** A field the user has to fix before the app sends anything. */
enum class InputError { SERVER_URL, API_TOKEN, EMAIL, EMAIL_COLON, PASSWORD }

/**
 * What the sign-in screen shows. The secrets live in memory only, never in
 * saved instance state, and `toString` leaves them out.
 *
 * [retryAt] is the [SignInModel] clock time at which a 429 stops blocking
 * sign-in, or null when nothing blocks it.
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
) {
    override fun toString(): String =
        "SignInState(serverUrl=$serverUrl, method=$method, inputErrors=$inputErrors, submitting=$submitting, " +
            "failure=$failure, retryAt=$retryAt, signedIn=$signedIn)"
}

/**
 * The sign-in form and its attempts. Each submit sends one request, and only
 * when the user asks: after a 429 the model refuses to submit until
 * `Retry-After` has passed, and it never retries on its own.
 *
 * @param clock monotonic milliseconds, such as `SystemClock.elapsedRealtime`.
 * @param io where the blocking [SignIn.attempt] runs.
 */
class SignInModel(
    private val signIn: SignIn,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: () -> Long,
) {

    private val mutableState = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = mutableState.asStateFlow()

    fun onServerUrlChange(value: String) = edit(InputError.SERVER_URL) { copy(serverUrl = value) }

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
            // TKA-0022 goes here: an http:// server shows the unencrypted
            // connection warning, and nothing is sent until the user confirms.
            val result = withContext(io) { signIn.attempt(server, credentials) }
            mutableState.update { it.after(result) }
        }
    }

    /** Clears the form after the app has moved on from a successful sign-in. */
    fun reset() {
        mutableState.value = SignInState()
    }

    private fun SignInState.after(result: SignInResult): SignInState =
        when (result) {
            // Drops every secret the form held.
            SignInResult.SignedIn -> SignInState(signedIn = true)
            is SignInResult.RateLimited -> copy(
                submitting = false,
                failure = result,
                retryAt = clock() + (result.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS) * MILLIS_PER_SECOND,
            )
            else -> copy(submitting = false, failure = result)
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
