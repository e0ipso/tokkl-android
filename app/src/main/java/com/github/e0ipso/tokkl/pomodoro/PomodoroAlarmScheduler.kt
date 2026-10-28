package com.github.e0ipso.tokkl.pomodoro

import java.time.Instant

/**
 * One exact alarm for the held session's phase, or none.
 *
 * [permissionDenied] is what the Pomodoro page reads when a phase end will not wake the phone.
 */
interface PomodoroAlarmScheduler {
    val permissionDenied: Boolean

    /**
     * Arms [triggerAt] for [phase], replacing the alarm already armed. A time already past rings
     * at once. The phase travels with the delivery, so a late alarm cannot ring a newer phase.
     * Returns false when [permissionDenied].
     */
    fun schedule(triggerAt: Instant, phase: PomodoroPhaseKey): Boolean

    /** Drops the armed alarm. Does nothing when none is armed. */
    fun cancel()
}
