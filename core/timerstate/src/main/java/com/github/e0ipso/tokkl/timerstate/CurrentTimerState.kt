package com.github.e0ipso.tokkl.timerstate

import kotlinx.coroutines.flow.Flow

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
 * What every timer surface shows and acts on, the signed-in account's timer controls as they
 * change, for a reader that needs nothing else of the timer state.
 */
interface TimerControlsSource {
    val controls: Flow<TimerControls>
}

/**
 * The signed-in account's held timer state as it changes, and a read of it together with the
 * account's name under the holder's lock, for the readers that draw a Pomodoro session.
 */
interface HeldTimerStates {

    /** The signed-in account's held timer state, or null without one. */
    val held: Flow<HeldTimerState?>

    /**
     * Calls [read] with the signed-in account's name and its held state, while no accepted state
     * is between publishing and writing its running entry to the cache. Keep [read] short.
     */
    fun <T> withHeld(read: (account: String, held: HeldTimerState?) -> T): T
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
