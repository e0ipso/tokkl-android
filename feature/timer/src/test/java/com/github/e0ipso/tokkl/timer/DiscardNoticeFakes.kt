package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.api.DiscardedPomodoro
import com.github.e0ipso.tokkl.timerstate.DiscardNotices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A too-short pomodoro [notice], which [take] hands over when a screen shows it. */
internal fun discardNotices(
    notice: Flow<DiscardedPomodoro?> = flowOf(null),
    take: suspend () -> DiscardedPomodoro? = { null },
): DiscardNotices = object : DiscardNotices {
    override val discardNotice = notice

    override suspend fun takeDiscardNotice() = take()
}
