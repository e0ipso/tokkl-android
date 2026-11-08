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
 * The ringing notification's buttons and its swipe. This reads the button's phase and revision
 * and hands them to [PomodoroRingingButtons].
 */
class PomodoroRingingActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val buttons: PomodoroRingingButtons by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(NEXT, END, REPOST)) return
        val phase = intent.phaseKey() ?: return
        val revision = intent.shownRevision()
        if (intent.action != REPOST && revision == null) return
        val pending = goAsync()
        scope.launch(io) {
            if (intent.action == REPOST) {
                try {
                    buttons.repost(phase)
                } finally {
                    pending.finish()
                }
            } else {
                // The broadcast ends before the request, so a server slow to answer cannot hold
                // it past its time limit. The ringing service keeps the process alive.
                pending.finish()
                buttons.press(phase, checkNotNull(revision), end = intent.action == END)
            }
        }
    }

    companion object {
        const val NEXT = "com.github.e0ipso.tokkl.action.POMODORO_NEXT"
        const val END = "com.github.e0ipso.tokkl.action.POMODORO_END"
        const val REPOST = "com.github.e0ipso.tokkl.action.POMODORO_REPOST"
    }
}
