package com.github.e0ipso.tokkl.timerstate

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The signed-in account's timer state as a test holds it: the held state [held] gives, the
 * controls [controls] gives at each read, and [account]'s name. A test that reads no held state
 * or no controls leaves them out.
 */
class FixedTimerState(
    private val controls: suspend () -> TimerControls = { error("this test reads no timer controls") },
    private val account: String = ServerTimerStates.ACCOUNT,
    held: () -> HeldTimerState? = { error("this test reads no held timer state") },
) : CurrentTimerState {

    private val heldState = held

    /**
     * What [held] gives when collected, and nothing after it, as a state no answer or event
     * replaces while it is collected. Each collection reads [held] again.
     */
    override val held: Flow<HeldTimerState?> = flow {
        emit(heldState())
        awaitCancellation()
    }

    override fun heldNow(): HeldTimerState? = heldState()

    override fun account(): String = account

    override suspend fun currentControls(): TimerControls = controls()
}
