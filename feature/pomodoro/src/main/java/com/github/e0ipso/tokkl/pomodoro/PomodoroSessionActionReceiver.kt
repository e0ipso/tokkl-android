package com.github.e0ipso.tokkl.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.e0ipso.tokkl.platform.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The session notification's Next phase and End session. No activity is needed: this reads the
 * button's account, phase and revision and hands them to [PomodoroSessionButtons].
 */
class PomodoroSessionActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val buttons: PomodoroSessionButtons by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NEXT && intent.action != END) return
        val account = intent.getStringExtra(POMODORO_NOTIFICATION_ACCOUNT) ?: return
        val phase = intent.phaseKey() ?: return
        val revision = intent.shownRevision() ?: return
        // The broadcast ends before the request, so a server slow to answer cannot hold it past
        // its time limit. The timer sync service keeps the process alive while a session runs.
        scope.launch(io) { buttons.press(account, phase, revision, end = intent.action == END) }
    }

    companion object {
        const val NEXT = "com.github.e0ipso.tokkl.action.SESSION_NEXT"
        const val END = "com.github.e0ipso.tokkl.action.SESSION_END"
    }
}
