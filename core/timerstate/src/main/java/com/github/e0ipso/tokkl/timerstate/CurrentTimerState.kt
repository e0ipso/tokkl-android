package com.github.e0ipso.tokkl.timerstate

/**
 * The signed-in account's timer state as the timer's actions, its surfaces, the timer sync and the
 * phase alarm read it. [AccountTimerStates] holds it over the cache and the disk; the unit tests
 * of those readers hold one source in memory.
 */
interface CurrentTimerState {

    /** The signed-in account's source. */
    val source: TimerStateSource

    /** The signed-in account's held state now, or null without one. Loads the stored state on first use. */
    fun heldNow(): HeldTimerState? = source.state.value

    /** The name the signed-in account's timer controls carry in their targets. */
    fun account(): String

    /** What the timer controls show now. */
    suspend fun currentControls(): TimerControls
}
