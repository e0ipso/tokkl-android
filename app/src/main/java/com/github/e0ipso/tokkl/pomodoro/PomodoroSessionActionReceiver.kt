package com.github.e0ipso.tokkl.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.TokklApplication
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** No activity or network is needed: the shared store commits the phase and queued save. */
class PomodoroSessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NEXT && intent.action != END) return
        val account = intent.getStringExtra(POMODORO_NOTIFICATION_ACCOUNT) ?: return
        val phase = intent.phaseKey() ?: return
        val app = context.applicationContext as TokklApplication
        // Capture this account's observer before launching. Sign-out retires this instance.
        val sync = app.pomodoroSessionNotification
        val done = completed
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sync.act(account, phase, end = intent.action == END)
                sync.refresh()
            } catch (_: SQLiteException) {
                // A failed local transaction keeps its old phase and can be attempted again.
            } catch (_: IOException) {
                // A failed alarm/display write must still finish the broadcast.
            } catch (_: IllegalArgumentException) {
                // A clock moved behind the phase start; no earlier span can be saved.
            } finally {
                pending.finish()
                done?.countDown()
            }
        }
    }

    companion object {
        @Volatile internal var completed: CountDownLatch? = null
        const val NEXT = "com.github.e0ipso.tokkl.action.SESSION_NEXT"
        const val END = "com.github.e0ipso.tokkl.action.SESSION_END"
    }
}
