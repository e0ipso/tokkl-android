package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.CurrentAccount
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

/**
 * The signed-in account's timer state as the timer's surfaces and the phase alarm read it. It
 * changes nothing: the server's answers go in through [AccountTimerStates.intake], and only
 * [AccountTimerStates] retires an account's state. The unit tests of those readers answer for it
 * in memory.
 */
interface CurrentTimerState {

    /** The signed-in account's held state now, or null without one. Loads the stored state on first use. */
    fun heldNow(): HeldTimerState?

    /** The name the signed-in account's timer controls carry in their targets. */
    fun account(): String

    suspend fun currentControls(): TimerControls
}

/**
 * Keeps the fields of each held state the account's timer state accepts, for a Start that
 * repeats the last session. The Pomodoro widget's store implements it.
 */
interface HeldFieldsMemory {

    /** Called under the holder's acceptance lock, before the state is published. */
    @Throws(IOException::class)
    fun remember(account: String, held: HeldTimerState)

    @Throws(IOException::class)
    fun clear()
}

/** The too-short pomodoro notice of the signed-in account, such as [AccountTimerStates] keeps. */
interface DiscardNotices {

    val discardNotice: Flow<DiscardedPomodoro?>

    /** Takes the [discardNotice] on show, or returns null when none is left to show. */
    suspend fun takeDiscardNotice(): DiscardedPomodoro?
}

private class AccountTimerState(val source: TimerStateSource, val account: String)

/**
 * The account's timer state from a Tokkl server with the timer API, which every timer request,
 * action and event feeds, and what every timer surface reads from it. Each account of the
 * [account] gets its own, made on first use: the first one of the process from the stored state,
 * a small file, and each one after a sign-in from nothing. Sign-out retires it. Take it once per
 * request or stream and give its answer to the same source: an answer kept for a retired source
 * changes nothing.
 *
 * The flows follow the signed-in account across sign-ins and load the stored state off the main
 * thread, on [io].
 *
 * @param entriesOrQueueChanged emits when the cache's entries or the queue change, which the
 *   [controls] read again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountTimerStates(
    private val store: TimerStateStore,
    private val lastFields: HeldFieldsMemory,
    entriesOrQueueChanged: Flow<Unit>,
    private val entries: CurrentEntryCache,
    private val capabilities: Flow<Capabilities>,
    private val refreshes: AccountRefreshRequests,
    private val account: CurrentAccount,
    private val clock: Clock,
    private val io: CoroutineContext,
) : AccountDataOwner,
    DiscardNotices,
    CurrentTimerState {

    override val signOutPhase = SignOutPhase.RETIRE_STATE

    override suspend fun forgetAccount() = current().source.retire()

    private class ForAccount(val account: Int, val state: Lazy<AccountTimerState>)

    // A JVM lock, not a Mutex: current() is read from any thread without suspending.
    private val lock = Any()
    private var forAccount = ForAccount(account.context.value.number, lazy { newTimerState(restore = true) })

    /**
     * The intake of the account signed in now, where the timer actions, the event stream and the
     * account refresh's probe give the server's answers. Take it once per request or stream and
     * give the answer to that intake: once its account signs out, it takes nothing more.
     */
    fun intake(): TimerStateSource = current().source

    override fun heldNow(): HeldTimerState? = current().source.state.value

    override fun account(): String = current().account

    /**
     * Calls [read] with the signed-in account's name and its held state, while no accepted state
     * is between publishing and writing its running entry to the cache, as
     * [TimerStateSource.withState] does. Keep [read] short. Blocks on disk I/O when [read] reads
     * the cache.
     */
    fun <T> withHeld(read: (account: String, held: HeldTimerState?) -> T): T {
        val owner = current()
        return owner.source.withState { held -> read(owner.account, held) }
    }

    private fun current(): AccountTimerState {
        val account = account.context.value.number
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
    private val states: Flow<AccountTimerState> = account.context.map { current() }

    val held: Flow<HeldTimerState?> = states.flatMapLatest { it.source.state }.flowOn(io)

    /**
     * [held] while the account's server offers the timer API, and null otherwise: what the
     * full-screen alarm shows.
     */
    val session: Flow<HeldTimerState?> =
        combine(held, capabilities) { held, flags -> held.takeIf { flags.timerState } }
            .flowOn(io)

    override val discardNotice: Flow<DiscardedPomodoro?> =
        states.flatMapLatest { it.source.discardNotice }.flowOn(io)

    /** Records the notice as shown in the account's timer state store, on [io]. */
    override suspend fun takeDiscardNotice(): DiscardedPomodoro? =
        withContext(io) { current().source.takeDiscardNotice() }

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
        entriesOrQueueChanged,
        states.flatMapLatest { it.source.state },
        capabilities,
    ) { _, _, flags ->
        val account = current()
        account.source.withState { held ->
            serverTimerControls(entries.latestRunning(), held, flags.timerState, account.account)
        }
    }.flowOn(io).distinctUntilChanged()

    override suspend fun currentControls(): TimerControls = controls.first()

    /**
     * Retires the source and clears its store and the remembered Start fields, so the account
     * signing in starts from nothing. Sign-in calls it before it begins the account. Blocks on
     * disk I/O.
     */
    @Throws(IOException::class)
    fun clearForSignIn() {
        current().source.retire()
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
                cache = entries,
                clock = clock,
                // The state does not carry a stopped entry's stop; the history read does.
                runningEnded = refreshes::refreshAfterChange,
            ),
            // Made anew after each sign-in and sign-out, which clear the store.
            account = account,
        )
    }
}
