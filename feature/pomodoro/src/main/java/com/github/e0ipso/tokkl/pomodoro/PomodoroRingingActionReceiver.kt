package com.github.e0ipso.tokkl.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.timernotice.showTimerActionFailure
import com.github.e0ipso.tokkl.timerstate.TimerActions
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The ringing notification's buttons. Next phase and End session go to the server with the
 * revision the notification showed, and the state it answers with ends the ring here and on every
 * other device. One that fails, offline included, leaves the ring going and says why. A swipe
 * posts the notification again.
 */
class PomodoroRingingActionReceiver : BroadcastReceiver(), KoinComponent {
    private val pomodoroAlarm: PomodoroPhaseAlarm by inject()
    private val timerActions: TimerActions by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(NEXT, END, REPOST)) return
        val phase = intent.phaseKey() ?: return
        val revision = intent.shownRevision()
        if (intent.action != REPOST && revision == null) return
        val done = completed
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch(io) {
            try {
                if (intent.action == REPOST) {
                    try {
                        pomodoroAlarm.repost(phase)
                    } finally {
                        pending.finish()
                    }
                } else {
                    // The broadcast ends before the request, so a server slow to answer cannot
                    // hold it past its time limit. The ringing service keeps the process alive.
                    pending.finish()
                    pomodoroAlarm.sendPhaseAction(timerActions, phase, checkNotNull(revision), end = intent.action == END)
                        ?.let { showTimerActionFailure(app, it) }
                }
            } finally {
                done?.countDown()
            }
        }
    }

    companion object {
        /** Device tests await async action completion. Production leaves this null. */
        @Volatile
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        var completed: CountDownLatch? = null

        const val NEXT = "com.github.e0ipso.tokkl.action.POMODORO_NEXT"
        const val END = "com.github.e0ipso.tokkl.action.POMODORO_END"
        const val REPOST = "com.github.e0ipso.tokkl.action.POMODORO_REPOST"
    }
}
