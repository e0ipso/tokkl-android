package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import android.content.Intent
import com.github.e0ipso.tokkl.platform.mainActivityIntent
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey

/** The action of a session notification's tap, which opens MainActivity on the Pomodoro page. */
const val SHOW_POMODORO = "com.github.e0ipso.tokkl.action.SHOW_POMODORO_SESSION"

/** The extra that holds the account a session notification was shown for. */
const val POMODORO_NOTIFICATION_ACCOUNT = "pomodoro_notification_account"

/**
 * Include the account in PendingIntent identity as well as in the receiver's validation. A button
 * also carries the [revision] its notification showed.
 */
internal fun Intent.putNotificationIdentity(account: String, phase: PomodoroPhaseKey, revision: Long? = null): Intent =
    putPhase(phase, revision).apply {
        data = phaseUri(phase, revision).buildUpon().appendQueryParameter("account", account).build()
        putExtra(POMODORO_NOTIFICATION_ACCOUNT, account)
    }

fun pomodoroSessionOpenIntent(context: Context, account: String, phase: PomodoroPhaseKey) =
    mainActivityIntent(context).setAction(SHOW_POMODORO)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putNotificationIdentity(account, phase)

data class PomodoroNotificationTap(val account: String, val phase: PomodoroPhaseKey)
