package com.github.e0ipso.tokkl.pomodoro

import android.content.Intent
import android.net.Uri
import java.time.Instant

/** PendingIntent identity includes the phase, so an old button never becomes a new button. */
internal fun Intent.putPhase(phase: PomodoroPhaseKey, unique: Boolean = false): Intent = apply {
    putExtra("workspace", phase.workspaceId)
    putExtra("kind", phase.kind)
    putExtra("started", phase.startedAt.toEpochMilli())
    putExtra("pomodoro", phase.pomodoroStartedAt.toEpochMilli())
    putExtra("completed", phase.completed)
    if (unique) {
        data = Uri.Builder().scheme("tokkl-pomodoro").authority("phase")
            .appendPath(phase.workspaceId.toString()).appendPath(phase.kind)
            .appendPath(phase.startedAt.toEpochMilli().toString())
            .appendPath(phase.pomodoroStartedAt.toEpochMilli().toString())
            .appendPath(phase.completed.toString()).build()
    }
}

internal fun Intent.phaseKey(): PomodoroPhaseKey? {
    if (!listOf("workspace", "kind", "started", "pomodoro", "completed").all(::hasExtra)) return null
    val kind = getStringExtra("kind")?.takeIf { it in listOf("work", "shortRest", "longRest") } ?: return null
    val completed = getDoubleExtra("completed", -1.0)
    if (!completed.isFinite() || completed < 0 || completed % 1.0 != 0.0) return null
    return PomodoroPhaseKey(
        getLongExtra("workspace", 0), kind, Instant.ofEpochMilli(getLongExtra("started", 0)),
        Instant.ofEpochMilli(getLongExtra("pomodoro", 0)), completed,
    )
}
