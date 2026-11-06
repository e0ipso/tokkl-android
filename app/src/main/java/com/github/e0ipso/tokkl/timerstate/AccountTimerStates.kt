package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.SignOutOrder
import com.github.e0ipso.tokkl.widget.PomodoroWidgetFieldsStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The account's [TimerStateSource], and the name its timer controls carry in their targets. */
class AccountTimerState(val source: TimerStateSource, val account: String)

/**
 * The account's timer state from a Tokkl server with the timer API, which every timer request,
 * action and event feeds. Loaded on first use from the stored state, a small file. Sign-out
 * retires it and sign-in replaces it, so take it once per request or stream and give its
 * answer to the same source: an answer kept for a retired source changes nothing.
 */
class AccountTimerStates(
    private val store: TimerStateStore,
    private val lastFields: PomodoroWidgetFieldsStore,
    private val cache: AccountCache,
    private val refreshes: AccountRefreshRequests,
) : AccountDataOwner {

    override val signOutOrder = SignOutOrder.TIMER_STATE

    override suspend fun forgetAccount() = source.retire()

    private val lock = Any()
    private var forAccount = newTimerState(restore = true)
    private val renewalCount = MutableStateFlow(0)

    /** Counts the replacements of [source], so flows of its state follow the new account's source. */
    val renewals: StateFlow<Int> = renewalCount.asStateFlow()

    /** The signed-in account's source. */
    val source: TimerStateSource
        get() = current().source

    /** The signed-in account's held state now, or null without one. Loads the stored state on first use. */
    fun held(): HeldTimerState? = source.state.value

    /** The name the signed-in account's timer controls carry in their targets. */
    fun account(): String = current().account

    /** The source and the account name, read together under the holder's lock. */
    fun current(): AccountTimerState = synchronized(lock) { forAccount.value }

    /**
     * Retires the timer state source and clears its store, then installs a new, empty source for
     * the account signing in. Blocks on disk I/O.
     */
    @Throws(IOException::class)
    fun renew() {
        source.retire()
        lastFields.clear()
        store.clear()
        synchronized(lock) { forAccount = newTimerState(restore = false) }
        renewalCount.update { it + 1 }
    }

    private fun newTimerState(restore: Boolean): Lazy<AccountTimerState> = lazy {
        val account = store.account()
        val initial = if (restore) store.load() else null
        // A pre-widget held session is already sufficient to seed its idle Start fields.
        try {
            initial?.let { lastFields.remember(account, it.held) }
        } catch (_: IOException) {
            // A later accepted state retries, as the timer state's own store does.
        }
        AccountTimerState(
            TimerStateSource(
                initial = initial,
                save = { stored ->
                    store.save(stored)
                    // Under the holder's acceptance lock, for actions and SSE from any surface.
                    lastFields.remember(account, stored.held)
                },
                cache = cache,
                // The state does not carry a stopped entry's stop; the history read does.
                onRunningEnded = refreshes::refreshAfterChange,
            ),
            // Made anew after each sign-in and sign-out, which clear the store.
            account = account,
        )
    }
}
