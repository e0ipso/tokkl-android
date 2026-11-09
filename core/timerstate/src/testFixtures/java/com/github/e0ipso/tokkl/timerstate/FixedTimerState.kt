package com.github.e0ipso.tokkl.timerstate

/**
 * The signed-in account's timer state as a test holds it: the source [sourceOf] gives, the
 * controls [controls] gives at each read, and [account]'s name. A test that reads no source or
 * no controls leaves them out.
 */
class FixedTimerState(
    private val sourceOf: () -> TimerStateSource = { error("this test reads no timer state source") },
    private val controls: suspend () -> TimerControls = { error("this test reads no timer controls") },
    private val account: String = ServerTimerStates.ACCOUNT,
    private val held: () -> HeldTimerState? = { sourceOf().state.value },
) : CurrentTimerState {
    override val source: TimerStateSource get() = sourceOf()

    override fun heldNow(): HeldTimerState? = held()

    override fun account(): String = account

    override suspend fun currentControls(): TimerControls = controls()
}
