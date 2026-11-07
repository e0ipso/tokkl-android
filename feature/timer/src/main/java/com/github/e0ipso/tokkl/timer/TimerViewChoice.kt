package com.github.e0ipso.tokkl.timer

import kotlinx.coroutines.flow.StateFlow

/** The Timer page's List or Calendar choice, as [TimerViewSetting] keeps it. */
interface TimerViewChoice {

    /** The view the Timer page shows. */
    val view: StateFlow<TimerView>

    /** Shows [view] from now on. Call it on the main thread. */
    fun choose(view: TimerView)
}
