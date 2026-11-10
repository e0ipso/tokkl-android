package com.github.e0ipso.tokkl.pomodoro

import android.content.Intent
import android.net.Uri
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import java.time.Instant

/** The extra that holds the start of the phase an intent is about, in epoch milliseconds. */
const val EXTRA_PHASE_START = "pomodoro_phase_start"

const val EXTRA_REVISION = "pomodoro_revision"

/**
 * Puts [phase], and the [revision] a button was shown at, on this intent. With [unique], they also
 * go in its data, which PendingIntent identity includes, so an old button never becomes a new one.
 */
fun Intent.putPhase(phase: PomodoroPhaseKey, revision: Long? = null, unique: Boolean = false): Intent = apply {
    putExtra(EXTRA_PHASE_START, phase.start.toEpochMilli())
    if (revision != null) putExtra(EXTRA_REVISION, revision)
    if (unique) data = phaseUri(phase, revision)
}

/** The data URI that keeps one phase's, and one revision's, PendingIntents apart. */
internal fun phaseUri(phase: PomodoroPhaseKey, revision: Long?): Uri {
    val uri = Uri.Builder().scheme(
        "tokkl-pomodoro",
    ).authority("phase").appendPath(phase.start.toEpochMilli().toString())
    if (revision != null) uri.appendPath(revision.toString())
    return uri.build()
}

fun Intent.phaseKey(): PomodoroPhaseKey? = if (hasExtra(
        EXTRA_PHASE_START,
    )
) {
    PomodoroPhaseKey(Instant.ofEpochMilli(getLongExtra(EXTRA_PHASE_START, 0)))
} else {
    null
}

internal fun Intent.shownRevision(): Long? =
    if (hasExtra(EXTRA_REVISION)) getLongExtra(EXTRA_REVISION, 0).takeIf { it >= 0 } else null
