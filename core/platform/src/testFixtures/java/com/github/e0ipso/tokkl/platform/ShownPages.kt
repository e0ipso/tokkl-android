package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * The app as the screens' view models see it in a test: in the foreground until a [trip], and the
 * network's returns as [reconnect] sends them. [scheduler] runs what each change wakes.
 */
class TestApp(private val scheduler: TestCoroutineScheduler) {
    val foreground = MutableStateFlow(true)
    private val reconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val app = AppForeground(foreground, reconnects)

    /** A trip to the background and back. */
    fun trip() {
        foreground.value = false
        scheduler.runCurrent()
        foreground.value = true
        scheduler.runCurrent()
    }

    /** The network reaches the internet anew. */
    fun reconnect() {
        reconnects.tryEmit(Unit)
        scheduler.runCurrent()
    }
}

/**
 * A screen that reads [page] in [scope] from [show] to [leave], as a page's route does while the
 * page shows. [scope] should run on [scheduler], so the five seconds after [leave] pass at once.
 */
class TestScreen(
    private val page: Flow<*>,
    private val scope: CoroutineScope,
    private val scheduler: TestCoroutineScheduler,
) {
    private var reading: Job? = null

    fun show() {
        if (reading == null) reading = scope.launch { page.collect {} }
        scheduler.runCurrent()
    }

    /** The screen stops reading, as when the activity stops or turns, but less than five seconds pass. */
    fun hide() {
        reading?.cancel()
        reading = null
        scheduler.runCurrent()
    }

    /**
     * The screen stops reading, and more than five seconds pass, as when another activity covers
     * it or its route leaves the back stack. A route that leaves also clears its view model, which
     * this does not.
     */
    fun leave() {
        hide()
        scheduler.advanceTimeBy(LEAVE_MILLIS)
        scheduler.runCurrent()
    }

    private companion object {
        const val LEAVE_MILLIS = 6_000L
    }
}
