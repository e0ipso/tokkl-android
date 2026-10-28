package com.github.e0ipso.tokkl.pomodoro

import android.content.Intent
import android.net.Uri
import java.time.Instant

private const val EXTRA_PHASE_START = "pomodoro_phase_start"
private const val EXTRA_REVISION = "pomodoro_revision"

/**
 * Puts [phase], and the [revision] a button was shown at, on this intent. With [unique], they also
 * go in its data, which PendingIntent identity includes, so an old button never becomes a new one.
 */
internal fun Intent.putPhase(phase: PomodoroPhaseKey, revision: Long? = null, unique: Boolean = false): Intent = apply {
    putExtra(EXTRA_PHASE_START, phase.start.toEpochMilli())
    if (revision != null) putExtra(EXTRA_REVISION, revision)
    if (unique) {
        val uri = Uri.Builder().scheme("tokkl-pomodoro").authority("phase").appendPath(phase.start.toEpochMilli().toString())
        if (revision != null) uri.appendPath(revision.toString())
        data = uri.build()
    }
}

/** The phase this intent carries, or null without one. */
internal fun Intent.phaseKey(): PomodoroPhaseKey? =
    if (hasExtra(EXTRA_PHASE_START)) PomodoroPhaseKey(Instant.ofEpochMilli(getLongExtra(EXTRA_PHASE_START, 0))) else null

/** The revision this intent's button was shown at, or null without one. */
internal fun Intent.shownRevision(): Long? =
    if (hasExtra(EXTRA_REVISION)) getLongExtra(EXTRA_REVISION, 0).takeIf { it >= 0 } else null

/** Removes what [putPhase] added to the extras. The action and data stay. */
internal fun Intent.removePhase() {
    removeExtra(EXTRA_PHASE_START)
    removeExtra(EXTRA_REVISION)
}
