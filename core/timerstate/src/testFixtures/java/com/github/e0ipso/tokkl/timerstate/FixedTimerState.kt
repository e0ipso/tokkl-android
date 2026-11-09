package com.github.e0ipso.tokkl.timerstate

/**
 * The signed-in account's timer state as a test holds it: the held state [held] gives, the
 * controls [controls] gives at each read, and [account]'s name. A test that reads no held state
 * or no controls leaves them out.
 */
class FixedTimerState(
    private val controls: suspend () -> TimerControls = { error("this test reads no timer controls") },
    private val account: String = ServerTimerStates.ACCOUNT,
    private val held: () -> HeldTimerState? = { error("this test reads no held timer state") },
) : CurrentTimerState {

    override fun heldNow(): HeldTimerState? = held()

    override fun account(): String = account

    override suspend fun currentControls(): TimerControls = controls()
}
