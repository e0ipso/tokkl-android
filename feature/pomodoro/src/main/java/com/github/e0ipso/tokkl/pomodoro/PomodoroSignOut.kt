package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase

/**
 * The Pomodoro feature's part of sign-out, once the held timer state is retired: the phase alarm
 * drops its alarm and ring, so nothing arms or rings the account's phase again, the session
 * notification stops following the held state and erases what it showed, and notification ID 3
 * goes. That ID is shared by the ring and the session notification, a non-ringing form included,
 * so it is cancelled on its own too: a failed stop of the ring or the session notification still
 * removes it. A part that fails does not stop the ones after it.
 */
internal class PomodoroSignOut(
    private val alarm: PomodoroPhaseAlarm,
    private val notification: PomodoroSessionNotificationSync,
    private val sharedId: () -> Unit,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.STOP_WORK

    override suspend fun forgetAccount() {
        try {
            alarm.release()
        } finally {
            try {
                notification.forget()
            } finally {
                sharedId()
            }
        }
    }
}
