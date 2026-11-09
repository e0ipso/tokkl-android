package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.SessionActions

/**
 * What the session notification's Next phase and End session do once
 * [PomodoroSessionActionReceiver] has read their broadcast. The action goes to the server with the
 * revision the notification showed, and the state it answers with reaches the notification and the
 * alarm. One that fails, offline included, changes nothing on the phone and says why.
 */
interface PomodoroSessionButtons {

    /** Next phase, or End session when [end], of [phase] at [revision], from [account]'s notification. */
    suspend fun press(account: String, phase: PomodoroPhaseKey, revision: Long, end: Boolean)
}

/**
 * What the ringing notification's buttons do once [PomodoroRingingActionReceiver] has read their
 * broadcast. Next phase and End session go to the server with the revision the notification
 * showed, and the state it answers with ends the ring here and on every other device. One that
 * fails, offline included, leaves the ring going and says why. A swipe posts the notification again.
 */
interface PomodoroRingingButtons {

    /** Next phase, or End session when [end], of [phase] at [revision]. */
    suspend fun press(phase: PomodoroPhaseKey, revision: Long, end: Boolean)

    /** Posts the ringing notification of [phase] again after a swipe, while it still rings. */
    suspend fun repost(phase: PomodoroPhaseKey)
}

/** The session notification's buttons, through [notification], which knows the account it showed. */
internal class SessionNotificationButtons(
    private val failures: TimerFailureNotice,
    private val notification: PomodoroSessionNotificationSync,
) : PomodoroSessionButtons {
    override suspend fun press(account: String, phase: PomodoroPhaseKey, revision: Long, end: Boolean) {
        // A button of an account signed out since sends nothing: its identity is gone.
        notification.act(account, phase, revision, end)?.let { failures.show(it) }
    }
}

/** The ringing notification's buttons, through the phase [alarm] and the server's session [actions]. */
internal class AlarmRingingButtons(
    private val failures: TimerFailureNotice,
    private val alarm: PomodoroPhaseAlarm,
    private val actions: SessionActions,
) : PomodoroRingingButtons {
    override suspend fun press(phase: PomodoroPhaseKey, revision: Long, end: Boolean) {
        alarm.sendPhaseAction(actions, phase, revision, end)?.let { failures.show(it) }
    }

    override suspend fun repost(phase: PomodoroPhaseKey) = alarm.repost(phase)
}
