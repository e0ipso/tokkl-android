package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightTest {

    private val flight = SingleFlight(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun aCallWhileAJobRunsGetsThatJob() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var runs = 0

        val first = flight.launch {
            runs++
            release.await()
        }
        val second = flight.launch { runs++ }

        assertSame(first, second)
        assertSame(first, flight.running())
        release.complete(Unit)
        first.join()
        assertEquals(1, runs)
        assertNull(flight.running())
    }

    @Test
    fun aCallAfterTheJobEndedStartsAnother() = runBlocking {
        var runs = 0

        val first = flight.launch { runs++ }
        first.join()
        val second = flight.launch { runs++ }
        second.join()

        assertNotSame(first, second)
        assertEquals(2, runs)
    }

    @Test
    fun aJobThatCallsLaunchGetsItself() = runBlocking {
        var inner: Job? = null

        val outer = flight.launch { inner = flight.launch { error("must not start a second job") } }
        outer.join()

        assertSame(outer, inner)
    }

    @Test
    fun cancelEndsTheJobAndTheNextCallStartsAnother() = runBlocking {
        val first = flight.launch { CompletableDeferred<Unit>().await() }

        flight.cancel()

        assertTrue(first.isCancelled)
        assertNull(flight.running())
        val second = flight.launch { }
        assertNotSame(first, second)
    }
}
