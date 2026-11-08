package com.github.e0ipso.tokkl.timerstate

import java.time.Instant

/**
 * A phase of the server's session, by its `phase.start`. The phase alarm, the ring, the alarm
 * screen and every notification button carry it, so a delivery or a tap meant for an older phase
 * changes nothing once another device or the phone has moved the session on.
 */
data class PomodoroPhaseKey(val start: Instant)
