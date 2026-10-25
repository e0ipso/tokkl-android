package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import android.content.Intent
import com.github.e0ipso.tokkl.MainActivity

internal const val SHOW_POMODORO = "com.github.e0ipso.tokkl.action.SHOW_POMODORO_SESSION"
internal const val POMODORO_NOTIFICATION_ACCOUNT = "pomodoro_notification_account"

/** Include the account in PendingIntent identity as well as in the receiver's validation. */
internal fun Intent.putNotificationIdentity(account: String, phase: PomodoroPhaseKey): Intent =
    putPhase(phase, unique = true).apply {
        data = data!!.buildUpon().appendQueryParameter("account", account).build()
        putExtra(POMODORO_NOTIFICATION_ACCOUNT, account)
    }

internal fun pomodoroSessionOpenIntent(context: Context, account: String, phase: PomodoroPhaseKey) =
    Intent(context, MainActivity::class.java).setAction(SHOW_POMODORO)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putNotificationIdentity(account, phase)

data class PomodoroNotificationTap(val account: String, val phase: PomodoroPhaseKey)

/**
 * Consuming the intent prevents rotation or later sign-in from replaying an old route. Only the
 * extras go, as with the reminder's tap: the action and data stay, so the activity keeps the
 * identity it was started with.
 */
internal fun Intent.takePomodoroNotificationTap(): PomodoroNotificationTap? {
    if (action != SHOW_POMODORO) return null
    val account = getStringExtra(POMODORO_NOTIFICATION_ACCOUNT)
    val phase = phaseKey()
    for (extra in listOf(POMODORO_NOTIFICATION_ACCOUNT, "workspace", "kind", "started", "pomodoro", "completed")) removeExtra(extra)
    return if (account != null && phase != null) PomodoroNotificationTap(account, phase) else null
}
