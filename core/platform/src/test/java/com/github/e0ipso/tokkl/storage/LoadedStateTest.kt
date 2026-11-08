package com.github.e0ipso.tokkl.storage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** A store's value as it loads at process start: what readers that run before the load ends get. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadedStateTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler))

    @Test
    fun aReaderBeforeTheLoadEndsWaitsForTheStoredValue() {
        val disk = CompletableDeferred<String>()
        val state = LoadedState(scope, "default") { disk.await() }
        val current = scope.async { state.current() }
        val loaded = scope.async { state.loaded.first() }
        scheduler.runCurrent()

        assertFalse(state.isReady)
        assertEquals("the state holds the default meanwhile", "default", state.state.value)
        assertFalse(current.isCompleted)
        assertFalse(loaded.isCompleted)

        disk.complete("stored")
        scheduler.runCurrent()

        assertTrue(state.isReady)
        assertEquals("stored", current.getCompleted())
        assertEquals("stored", loaded.getCompleted())
    }

    @Test
    fun aFailedLoadKeepsTheDefaultAndEnds() {
        val state = LoadedState<String>(scope, "default") { throw IOException("unreadable") }
        scheduler.runCurrent()

        assertTrue(state.isReady)
        assertEquals("default", state.state.value)
    }

    @Test
    fun theLoadsAreReadyOnceEachHasEnded() {
        val first = CompletableDeferred<Int>()
        val loads = StartupLoads(listOf(LoadedState(scope, 0) { first.await() }, LoadedState(scope, 0) { 2 }))
        scheduler.runCurrent()
        assertFalse(loads.isReady)

        first.complete(1)
        scheduler.runCurrent()

        assertTrue(loads.isReady)
    }
}
