package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.DiscardedPomodoro
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** The too-short pomodoro notice of the signed-in account, such as [HeldTimerFlows] keeps. */
interface DiscardNotices {

    /** The too-short pomodoro of the account's timer state that no screen has shown yet. */
    val discardNotice: Flow<DiscardedPomodoro?>

    /** Takes the [discardNotice] on show, or returns null when none is left to show. */
    suspend fun takeDiscardNotice(): DiscardedPomodoro?
}

/**
 * The signed-in account's held timer state, as flows that follow [timerStates] across sign-ins.
 * Each loads the stored state off the main thread, on [io]. [session] loads the capabilities
 * there too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeldTimerFlows(
    private val timerStates: AccountTimerStates,
    private val capabilities: CapabilitiesSource,
    private val io: CoroutineContext,
) : DiscardNotices {

    /** The signed-in account's held timer state, or null without one. */
    val held: Flow<HeldTimerState?> =
        timerStates.states.flatMapLatest { it.source.state }.flowOn(io)

    /**
     * [held] while the account's server offers the timer API, and null otherwise: what the
     * full-screen alarm shows.
     */
    val session: Flow<HeldTimerState?> =
        combine(held, flow { emitAll(capabilities.state) }) { held, flags -> held.takeIf { flags.timerState } }
            .flowOn(io)

    /** Records the notice as shown in the account's timer state store, on [io]. */
    override suspend fun takeDiscardNotice(): DiscardedPomodoro? =
        withContext(io) { timerStates.source.takeDiscardNotice() }

    override val discardNotice: Flow<DiscardedPomodoro?> =
        timerStates.states.flatMapLatest { it.source.discardNotice }.flowOn(io)
}
