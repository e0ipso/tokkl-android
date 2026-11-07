package com.github.e0ipso.tokkl.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.timernotice.showTimerActionFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.CountDownLatch

/**
 * The session notification's Next phase and End session. No activity is needed: the action goes
 * to the server with the revision the notification showed, and the state it answers with reaches
 * the notification and the alarm. One that fails, offline included, changes nothing on the phone
 * and says why.
 */
class PomodoroSessionActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val notification: PomodoroSessionNotificationSync by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NEXT && intent.action != END) return
        val account = intent.getStringExtra(POMODORO_NOTIFICATION_ACCOUNT) ?: return
        val phase = intent.phaseKey() ?: return
        val revision = intent.shownRevision() ?: return
        val app = context.applicationContext
        // A button of an account signed out since sends nothing: its identity is gone.
        val done = completed
        // The broadcast ends before the request, so a server slow to answer cannot hold it past
        // its time limit. The timer sync service keeps the process alive while a session runs.
        scope.launch(io) {
            try {
                val result = notification.act(account, phase, revision, end = intent.action == END)
                result?.let { showTimerActionFailure(app, it) }
            } finally {
                done?.countDown()
            }
        }
    }

    companion object {
        @Volatile
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        var completed: CountDownLatch? = null
        const val NEXT = "com.github.e0ipso.tokkl.action.SESSION_NEXT"
        const val END = "com.github.e0ipso.tokkl.action.SESSION_END"
    }
}
