package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.github.e0ipso.tokkl.timer.TimerNotice
import com.github.e0ipso.tokkl.timer.timerNoticeText
import com.github.e0ipso.tokkl.timerstate.TimerActionResult

/**
 * Says in a toast that a notification's Next phase or End session did not go through, in the
 * words the app's pages use: another device changed the timer first, no connection, or the
 * server's reason. A notification button has no screen of its own, and Android shows a text toast
 * from the background. The held state, the alarm and any ring stay as they were.
 */
internal fun showPomodoroActionFailure(context: Context, result: TimerActionResult) {
    if (result == TimerActionResult.Done) return
    val app = context.applicationContext
    val text = timerNoticeText(app.resources, TimerNotice.ActionFailed(result, 0))
    Handler(Looper.getMainLooper()).post { Toast.makeText(app, text, Toast.LENGTH_LONG).show() }
}
