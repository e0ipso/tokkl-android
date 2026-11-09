package com.github.e0ipso.tokkl.notification

import android.content.Context
import android.widget.Toast
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timernotice.timerNotStoredText
import com.github.e0ipso.tokkl.timernotice.timerNoticeText
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Says a failed timer action in a toast, which Android shows from the background too. The toast
 * goes up on [main], the main thread, from whichever thread calls [show].
 */
class ToastTimerFailureNotice(private val context: Context, private val main: CoroutineContext) : TimerFailureNotice {
    override suspend fun show(result: TimerActionResult) {
        if (result == TimerActionResult.Done) return
        val text = timerNoticeText(context.resources, TimerNotice.ActionFailed(result, 0))
        toast(text)
    }

    override suspend fun showNotStored() = toast(timerNotStoredText(context.resources))

    private suspend fun toast(text: String) {
        withContext(main) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }
}
