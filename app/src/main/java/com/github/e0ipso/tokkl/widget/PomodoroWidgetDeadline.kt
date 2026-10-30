package com.github.e0ipso.tokkl.widget

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * A single redraw at the held phase's corrected end, independent of alarm permission. The
 * live sync process needs no alarm, polling or wake lock for this wait. A changed phase or
 * session end cancels it; idle schedules nothing. A restored overdue phase draws immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun pomodoroWidgetDeadlineChanges(
    ends: Flow<Instant?>,
    clock: Clock = Clock.systemUTC(),
    wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
): Flow<Unit> = ends.distinctUntilChanged().flatMapLatest { end ->
    flow {
        emit(Unit)
        val now = clock.instant()
        val remaining = end?.let {
            val milliseconds = Duration.between(now, it).toMillis()
            // delay() takes whole milliseconds. Rounding down could redraw just before the
            // boundary, leaving ended=false with no later event when exact alarms are denied.
            milliseconds + if (now.plusMillis(milliseconds).isBefore(it)) 1L else 0L
        } ?: 0L
        if (remaining > 0L) {
            wait(remaining)
            emit(Unit)
        }
    }
}
