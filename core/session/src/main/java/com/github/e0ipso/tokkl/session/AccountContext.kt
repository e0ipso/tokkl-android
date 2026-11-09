package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One account's time on the phone, from the sign-in that begins it to the start of the sign-out
 * that ends it, or the next sign-in, which can be another account. Work started for an account
 * takes its context when it starts, and writes what it got only through [ifCurrent], so nothing
 * an account started lands after the account has gone, even when the next one has the same
 * server, user and credentials. A process starts in the context of whoever is signed in then, or
 * of nobody.
 *
 * @param number tells the contexts of one process apart, counting from 0.
 */
class AccountContext internal constructor(val number: Int) {

    /** The parent of the account's work, cancelled when the account ends. */
    private val job = SupervisorJob()

    @Volatile
    private var ended = false

    /** False once the account has ended. */
    val isCurrent: Boolean get() = !ended

    /**
     * Runs [write] and returns what it gave, unless the account has ended, which then waits until
     * [write] returns: null when the account has ended. Keep [write] short. It may block, as a
     * write to the disk does, so call this off the main thread then.
     */
    fun <T> ifCurrent(write: () -> T): T? = synchronized(this) { if (ended) null else write() }

    /**
     * Launches [block] in [scope], on its dispatcher, as work of this account: the end of the
     * account cancels it, and once the account has ended it does not start.
     */
    fun launchIn(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job = scope.launch(job, block = block)

    /** Calls [action] once when the account ends, at once when it has. Dispose of the handle to drop it. */
    fun onEnd(action: () -> Unit): DisposableHandle = job.invokeOnCompletion { action() }

    /** Ends the account, once every [ifCurrent] on its way has returned. */
    internal fun end() {
        synchronized(this) { ended = true }
        job.cancel()
    }
}

/**
 * The account signed in now, as an [AccountContext], and the count of the sign-outs that finished.
 * Each sign-in begins a context, also a sign-in again over a refused token, and sign-out ends it as
 * it starts, before its first step.
 */
class CurrentAccount {

    private val mutableContext = MutableStateFlow(AccountContext(0))
    private val signedOut = MutableStateFlow(0)

    /**
     * The context of the account signed in now. It changes at each sign-in. After a sign-out it
     * holds the ended context until the next sign-in.
     */
    val context: StateFlow<AccountContext> = mutableContext.asStateFlow()

    /**
     * Counts the sign-outs of this process whose every step succeeded. The navigation starts over
     * at sign-in after each and drops its view models, and with them what each screen holds in
     * memory.
     */
    val signOuts: StateFlow<Int> = signedOut.asStateFlow()

    /**
     * An account is signing in: ends the context before and begins a new one. Call it before the
     * session is stored, so no answer to the new session reaches what the account before held.
     */
    @Synchronized
    fun begin() {
        val before = mutableContext.value
        before.end()
        mutableContext.value = AccountContext(before.number + 1)
    }

    /** Sign-out has started: ends the account's context. */
    internal fun end() {
        context.value.end()
    }

    /** Sign-out has cleared the phone. */
    internal fun signedOut() {
        signedOut.update { it + 1 }
    }
}
