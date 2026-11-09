package com.github.e0ipso.tokkl.notification

import android.content.Context
import android.widget.Toast
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timernotice.timerNoticeText
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Says a failed timer action in a toast, which Android shows from the background too. The toast
 * goes up on the main thread, from whichever thread calls [show].
 */
class ToastTimerFailureNotice(private val context: Context) : TimerFailureNotice {
    override suspend fun show(result: TimerActionResult) {
        if (result == TimerActionResult.Done) return
        val text = timerNoticeText(context.resources, TimerNotice.ActionFailed(result, 0))
        withContext(Dispatchers.Main) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }
}
