package com.github.e0ipso.tokkl.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.e0ipso.tokkl.TokklApplication
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The ringing notification's buttons. Next phase and End session go to the server with the
 * revision the notification showed, and the state it answers with ends the ring here and on every
 * other device. One that fails, offline included, leaves the ring going and says why. A swipe
 * posts the notification again.
 */
class PomodoroRingingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(NEXT, END, REPOST)) return
        val phase = intent.phaseKey() ?: return
        val revision = intent.shownRevision()
        if (intent.action != REPOST && revision == null) return
        val done = completed
        val pending = goAsync()
        val app = context.applicationContext as TokklApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == REPOST) {
                    try {
                        app.pomodoroAlarm.repost(phase)
                    } finally {
                        pending.finish()
                    }
                } else {
                    // The broadcast ends before the request, so a server slow to answer cannot
                    // hold it past its time limit. The ringing service keeps the process alive.
                    pending.finish()
                    app.pomodoroAlarm.sendPhaseAction(app.timerActions, phase, checkNotNull(revision), end = intent.action == END)
                        ?.let { showPomodoroActionFailure(app, it) }
                }
            } finally {
                done?.countDown()
            }
        }
    }

    companion object {
        /** Device tests await async action completion. Production leaves this null. */
        @Volatile internal var completed: CountDownLatch? = null

        const val NEXT = "com.github.e0ipso.tokkl.action.POMODORO_NEXT"
        const val END = "com.github.e0ipso.tokkl.action.POMODORO_END"
        const val REPOST = "com.github.e0ipso.tokkl.action.POMODORO_REPOST"
    }
}
