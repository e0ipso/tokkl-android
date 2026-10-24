package com.github.e0ipso.tokkl.pomodoro

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** An immutable intent for one phase; re-posting cannot retarget an older alarm's actions. */
internal fun pomodoroAlarmIntent(context: Context, phase: PomodoroPhaseKey): Intent =
    Intent(context, PomodoroAlarmActivity::class.java)
        .setAction("com.github.e0ipso.tokkl.action.SHOW_POMODORO_ALARM")
        .putPhase(phase, unique = true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

internal fun pomodoroAlarmPendingIntent(context: Context, phase: PomodoroPhaseKey): PendingIntent =
    PendingIntent.getActivity(
        context, 0, pomodoroAlarmIntent(context, phase),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
