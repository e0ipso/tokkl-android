package com.github.e0ipso.tokkl.timernotice

import android.content.Context
import android.widget.Toast
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Says in a toast that a timer action sent from outside the app's screens did not go through, in
 * the words the app's pages use: another device changed the timer first, no connection, or the
 * server's reason. The running notification, the widget, the Quick Settings tile and its long
 * press, the reminder, and the Pomodoro session and ringing notifications send their Pause,
 * Resume, Stop while paused, Next phase and End session this way. None of them has a screen of its
 * own, and Android shows a text toast from the background. What the surface shows stays as it was.
 * The toast goes up on the main thread, from whichever thread calls this.
 */
suspend fun showTimerActionFailure(context: Context, result: TimerActionResult) {
    if (result == TimerActionResult.Done) return
    val app = context.applicationContext
    val text = timerNoticeText(app.resources, TimerNotice.ActionFailed(result, 0))
    withContext(Dispatchers.Main) { Toast.makeText(app, text, Toast.LENGTH_LONG).show() }
}
