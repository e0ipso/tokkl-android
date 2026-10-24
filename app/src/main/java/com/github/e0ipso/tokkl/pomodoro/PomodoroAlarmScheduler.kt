package com.github.e0ipso.tokkl.pomodoro

import java.time.Instant

/**
 * One exact alarm for the running Pomodoro phase, or none.
 *
 * [permissionDenied] is what the Pomodoro page reads when a phase end will not wake the phone.
 */
interface PomodoroAlarmScheduler {
    val permissionDenied: Boolean

    /** Arms [triggerAt], replacing the alarm already armed. Returns false when [permissionDenied]. */
    fun schedule(triggerAt: Instant): Boolean

    /** Drops the armed alarm. Does nothing when none is armed. */
    fun cancel()

    /**
     * Keeps boot, update and clock-change delivery on while a session exists, and off when none
     * does. A session in overtime still needs that delivery, so cancelling the alarm leaves it on.
     */
    fun watchSession(running: Boolean)
}

/** Schedules nothing. Unit tests that are not about the alarm use this. */
internal object IdlePomodoroAlarms : PomodoroAlarmScheduler {
    override val permissionDenied: Boolean = false

    override fun schedule(triggerAt: Instant): Boolean = true

    override fun cancel() = Unit

    override fun watchSession(running: Boolean) = Unit
}
