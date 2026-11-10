package com.github.e0ipso.tokkl.timerstate

import java.time.Instant

/**
 * The server's clock at [deviceNow], the phone's own clock reading, corrected by the state's
 * offset. Nothing here applies a Pomodoro or pause rule: the server decides the phase, its planned
 * end, the transition and the pomodoro number.
 */
fun HeldTimerState.serverNow(deviceNow: Instant): Instant = deviceNow.plus(clockOffset)
