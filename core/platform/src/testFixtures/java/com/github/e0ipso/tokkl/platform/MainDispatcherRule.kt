package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Runs the main thread on one [StandardTestDispatcher] for the length of a test, with virtual
 * time, so a test that stands [scope] in for a view model's main thread decides when its work
 * runs and how much time passes, and never sleeps. [Dispatchers.Main] is that dispatcher too.
 *
 * [settle] lets a given time pass, and [awaitUntil] lets time pass until a condition holds,
 * failing after [timeoutMillis] of virtual time. Both run what is due on the way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {

    /** The view model's main thread: [dispatcher], where a failed child leaves the others running. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        scope.cancel()
        Dispatchers.resetMain()
    }

    /** Runs what is due now, as the main thread would after the call that queued it. */
    fun runCurrent() = dispatcher.scheduler.runCurrent()

    /** Lets [millis] of virtual time pass, running what falls due meanwhile. */
    fun settle(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    /**
     * Lets virtual time pass in steps of [stepMillis] until [condition] holds, and fails with
     * [what] once [timeoutMillis] have passed without it.
     */
    fun awaitUntil(
        what: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        stepMillis: Long = DEFAULT_STEP_MILLIS,
        condition: () -> Boolean,
    ) {
        runCurrent()
        var waited = 0L
        while (!condition()) {
            if (waited >= timeoutMillis) throw AssertionError("timed out waiting until $what")
            settle(stepMillis)
            waited += stepMillis
        }
    }

    /**
     * Runs [block] on the main thread, after what is queued there already, to its end, letting
     * virtual time pass as it waits.
     */
    fun <T> onMain(block: suspend () -> T): T {
        val result = scope.async { block() }
        awaitUntil("the main thread's call returned", ON_MAIN_TIMEOUT_MILLIS) { result.isCompleted }
        @OptIn(ExperimentalCoroutinesApi::class)
        return result.getCompleted()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_STEP_MILLIS = 10L
        const val ON_MAIN_TIMEOUT_MILLIS = 60_000L
    }
}
