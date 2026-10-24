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

/** Local Room/queue actions; neither needs a server answer or an activity. */
class PomodoroRingingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(NEXT, END, REPOST)) return
        val phase = intent.phaseKey() ?: return
        val done = completed
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessions = (context.applicationContext as TokklApplication).pomodoroSessions
                when (intent.action) {
                    NEXT -> sessions.nextPhaseIfCurrent(phase)
                    END -> sessions.endIfCurrent(phase)
                    REPOST -> sessions.repostRinging(phase)
                }
            } catch (failure: SQLiteException) {
                // Keep ringing if the local change could not commit; the actions can be retried.
            } catch (failure: IOException) {
                // The persisted phase-end record could not be changed.
            } finally {
                pending.finish()
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
