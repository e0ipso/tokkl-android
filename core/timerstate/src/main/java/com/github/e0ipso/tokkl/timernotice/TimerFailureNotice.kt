package com.github.e0ipso.tokkl.timernotice

import com.github.e0ipso.tokkl.timerstate.TimerActionResult

/**
 * Tells the user that a timer action sent from outside the app's screens did not go through, in
 * the words the app's pages use ([timerNoticeText]): another device changed the timer first, no
 * connection, or the server's reason. The running notification, the widget, the Quick Settings
 * tile and its long press, the reminder, and the Pomodoro session and ringing notifications send
 * their Pause, Resume, Stop while paused, Next phase and End session this way. None of them has a
 * screen of its own. The notification feature says it in a toast; what the surface shows stays
 * as it was. A [TimerActionResult.Done] says nothing.
 */
fun interface TimerFailureNotice {
    suspend fun show(result: TimerActionResult)
}
