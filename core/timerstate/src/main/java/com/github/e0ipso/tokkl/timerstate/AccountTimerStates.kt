package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.DiscardedPomodoro
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/** The too-short pomodoro notice of the signed-in account, such as [AccountTimerStates] keeps. */
interface DiscardNotices {

    /** The too-short pomodoro of the account's timer state that no screen has shown yet. */
    val discardNotice: Flow<DiscardedPomodoro?>

    /** Takes the [discardNotice] on show, or returns null when none is left to show. */
    suspend fun takeDiscardNotice(): DiscardedPomodoro?
}

/** The account's [TimerStateSource], and the name its timer controls carry in their targets. */
class AccountTimerState(val source: TimerStateSource, val account: String)

/**
 * The account's timer state from a Tokkl server with the timer API, which every timer request,
 * action and event feeds, and what every timer surface reads from it. Each account of the
 * [lifetime] gets its own, made on first use: the first one of the process from the stored state,
 * a small file, and each one after a sign-in from nothing. Sign-out retires it. Take it once per
 * request or stream and give its answer to the same source: an answer kept for a retired source
 * changes nothing.
 *
 * The flows follow the signed-in account across sign-ins and load the stored state off the main
 * thread, on [io].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountTimerStates(
    private val store: TimerStateStore,
    private val lastFields: HeldFieldsMemory,
    private val cache: AccountCache,
    private val capabilities: CapabilitiesSource,
    private val refreshes: AccountRefreshRequests,
    private val lifetime: AccountLifetime,
    private val clock: Clock,
    private val io: CoroutineContext,
) : AccountDataOwner,
    DiscardNotices {

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

    /** The signed-in account's held state now, or null without one. Loads the stored state on first use. */
    fun heldNow(): HeldTimerState? = source.state.value

    /** The name the signed-in account's timer controls carry in their targets. */
    fun account(): String = current().account

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

    /** The signed-in account's held timer state, or null without one. */
    val held: Flow<HeldTimerState?> = states.flatMapLatest { it.source.state }.flowOn(io)

    /**
     * [held] while the account's server offers the timer API, and null otherwise: what the
     * full-screen alarm shows.
     */
    val session: Flow<HeldTimerState?> =
        combine(held, capabilities.loaded) { held, flags -> held.takeIf { flags.timerState } }
            .flowOn(io)

    override val discardNotice: Flow<DiscardedPomodoro?> =
        states.flatMapLatest { it.source.discardNotice }.flowOn(io)

    /** Records the notice as shown in the account's timer state store, on [io]. */
    override suspend fun takeDiscardNotice(): DiscardedPomodoro? = withContext(io) { source.takeDiscardNotice() }

    /**
     * What every timer surface shows and acts on (docs/PRD.md T1, N1, N3, N4, N7): the Timer page,
     * the full-screen timer, the running notification, the widget, the Quick Settings tile and the
     * reminder. It is the cache's running entry, with its queued Starts, Stops and edits, and the
     * pause chain of the held state, as [serverTimerControls] puts them together, while the server
     * offers the timer API, with targets that name the account. Each value reads the cache and the
     * held state under the holder's lock, so a state never shows apart from the running entry it
     * wrote to the cache.
     */
    val controls: Flow<TimerControls> = combine(
        cache.entriesOrQueueChanged,
        states.flatMapLatest { it.source.state },
        capabilities.loaded,
    ) { _, _, flags ->
        val account = current()
        account.source.withState { held ->
            serverTimerControls(cache.runningNow(), held, flags.timerState, account.account)
        }
    }.flowOn(io).distinctUntilChanged()

    /** What [controls] holds now. */
    suspend fun currentControls(): TimerControls = controls.first()

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
