package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Clock

/** The signed-in account's [TimerStateSource], read anew for each call, such as [AccountTimerStates]. */
fun interface SignedInTimerState {
    fun timerState(): TimerStateSource
}

/** The signed-in account's held timer state now, or null without one, such as [AccountTimerStates.held]. */
fun interface CurrentHeldState {
    fun held(): HeldTimerState?
}

/** The account's [TimerStateSource], and the name its timer controls carry in their targets. */
class AccountTimerState(val source: TimerStateSource, val account: String)

/**
 * The account's timer state from a Tokkl server with the timer API, which every timer request,
 * action and event feeds. Each account of the [lifetime] gets its own, made on first use: the
 * first one of the process from the stored state, a small file, and each one after a sign-in
 * from nothing. Sign-out retires it. Take it once per request or stream and give its answer to
 * the same source: an answer kept for a retired source changes nothing.
 */
class AccountTimerStates(
    private val store: TimerStateStore,
    private val lastFields: HeldFieldsMemory,
    private val cache: AccountCache,
    private val refreshes: AccountRefreshRequests,
    private val lifetime: AccountLifetime,
    private val clock: Clock,
) : AccountDataOwner,
    SignedInTimerState,
    SignedInAccountName,
    CurrentHeldState {

    override val signOutPhase = SignOutPhase.RETIRE_STATE

    override suspend fun forgetAccount() = source.retire()

    /** The timer state of one account of the [lifetime], made on first use. */
    private class ForAccount(val account: Int, val state: Lazy<AccountTimerState>)

    // A JVM lock, not a Mutex: current() is read from any thread without suspending.
    private val lock = Any()
    private var forAccount = ForAccount(lifetime.accounts.value, lazy { newTimerState(restore = true) })

    /** The signed-in account's source. */
    val source: TimerStateSource
        get() = current().source

    override fun timerState(): TimerStateSource = source

    /** The signed-in account's held state now, or null without one. Loads the stored state on first use. */
    override fun held(): HeldTimerState? = source.state.value

    /** The name the signed-in account's timer controls carry in their targets. */
    fun account(): String = current().account

    override fun name(): String = account()

    /** The source and the account name of the account signed in now, read together. */
    fun current(): AccountTimerState {
        val account = lifetime.accounts.value
        val state = synchronized(lock) {
            if (forAccount.account != account) forAccount = ForAccount(account, lazy { newTimerState(restore = false) })
            forAccount.state
        }
        return state.value
    }

    /**
     * The signed-in account's timer state, and a new one after each sign-in. The first value
     * loads the stored state, so collect it off the main thread.
     */
    val states: Flow<AccountTimerState> = lifetime.accounts.map { current() }

    /**
     * Retires the source and clears its store and the remembered Start fields, so the account
     * signing in starts from nothing. Sign-in calls it before it begins the account. Blocks on
     * disk I/O.
     */
    @Throws(IOException::class)
    fun clearForSignIn() {
        source.retire()
        lastFields.clear()
        store.clear()
    }

    private fun newTimerState(restore: Boolean): AccountTimerState {
        val account = store.account()
        val initial = if (restore) store.load() else null
        // A pre-widget held session is already sufficient to seed its idle Start fields.
        try {
            initial?.let { lastFields.remember(account, it.held) }
        } catch (_: IOException) {
            // A later accepted state retries, as the timer state's own store does.
        }
        return AccountTimerState(
            TimerStateSource(
                initial = initial,
                store = { stored ->
                    store.save(stored)
                    // Under the holder's acceptance lock, for actions and SSE from any surface.
                    lastFields.remember(account, stored.held)
                },
                cache = cache,
                clock = clock,
                // The state does not carry a stopped entry's stop; the history read does.
                runningEnded = refreshes::refreshAfterChange,
            ),
            // Made anew after each sign-in and sign-out, which clear the store.
            account = account,
        )
    }
}
