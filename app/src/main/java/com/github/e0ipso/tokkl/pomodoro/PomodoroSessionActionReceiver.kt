package com.github.e0ipso.tokkl.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.timer.showTimerActionFailure
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The session notification's Next phase and End session. No activity is needed: the action goes
 * to the server with the revision the notification showed, and the state it answers with reaches
 * the notification and the alarm. One that fails, offline included, changes nothing on the phone
 * and says why.
 */
class PomodoroSessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NEXT && intent.action != END) return
        val account = intent.getStringExtra(POMODORO_NOTIFICATION_ACCOUNT) ?: return
        val phase = intent.phaseKey() ?: return
        val revision = intent.shownRevision() ?: return
        val app = context.applicationContext as TokklApplication
        // Capture this account's observer before launching. Sign-out retires this instance.
        val sync = app.pomodoroSessionNotification
        val done = completed
        // The broadcast ends before the request, so a server slow to answer cannot hold it past
        // its time limit. The timer sync service keeps the process alive while a session runs.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sync.act(account, phase, revision, end = intent.action == END)?.let { showTimerActionFailure(app, it) }
            } finally {
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
