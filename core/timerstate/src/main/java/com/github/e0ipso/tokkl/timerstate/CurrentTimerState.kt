package com.github.e0ipso.tokkl.timerstate

/**
 * The signed-in account's timer state as the timer's surfaces and the phase alarm read it. It
 * changes nothing: the server's answers go in through [TimerStateIntakes], and only
 * [AccountTimerStates] retires an account's state. The unit tests of those readers answer for it
 * in memory.
 */
interface CurrentTimerState {

    /** The signed-in account's held state now, or null without one. Loads the stored state on first use. */
    fun heldNow(): HeldTimerState?

    /** The name the signed-in account's timer controls carry in their targets. */
    fun account(): String

    /** What the timer controls show now. */
    suspend fun currentControls(): TimerControls
}

/**
 * Where the signed-in account's timer state takes the server's answers: the timer actions, the
 * event stream and the account refresh's probe. [AccountTimerStates] gives the account's own
 * intake; a test gives one it holds.
 */
fun interface TimerStateIntakes {

    /**
     * The intake of the account signed in now. Take it once per request or stream and give the
     * answer to that intake: once its account signs out, it takes nothing more.
     */
    fun intake(): TimerStateIntake
}
