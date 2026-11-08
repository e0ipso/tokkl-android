package com.github.e0ipso.tokkl.timerstate

import java.io.IOException

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
