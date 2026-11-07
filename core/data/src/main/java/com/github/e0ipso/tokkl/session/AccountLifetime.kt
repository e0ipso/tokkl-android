package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where one account's time on the phone begins and ends, for what keeps that account's state in
 * memory. Each sign-in begins an account, also a sign-in again over a refused token, which can be
 * another account. Each sign-out whose every step succeeded ends one.
 */
class AccountLifetime {

    private val begun = MutableStateFlow(0)
    private val ended = MutableStateFlow(0)

    /**
     * Counts the sign-ins of this process. The account's timer state follows it: each sign-in
     * gets a new one, so an answer for the account before changes nothing.
     */
    val accounts: StateFlow<Int> = begun.asStateFlow()

    /**
     * Counts the sign-outs of this process whose every step succeeded. The navigation starts over
     * at sign-in after each and drops its view models, and with them what each screen holds in
     * memory.
     */
    val signOuts: StateFlow<Int> = ended.asStateFlow()

    /**
     * An account is signing in. Call it before its session is stored, so no answer to the new
     * session reaches what the account before held.
     */
    fun begin() {
        begun.update { it + 1 }
    }

    /** Sign-out has cleared the phone. */
    internal fun end() {
        ended.update { it + 1 }
    }
}
