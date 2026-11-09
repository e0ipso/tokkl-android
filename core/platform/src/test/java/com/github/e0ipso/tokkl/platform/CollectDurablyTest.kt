package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** How a long-lived observer outlives the failures of its flow and of its own work. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectDurablyTest {

    private val reported = mutableListOf<Exception>()
    private val failures = ObserverFailures { _, failure -> reported += failure }

    @Test
    fun aFailedReadWaitsThenCollectsAgainWithoutReportingIt() = runTest {
        var reads = 0
        val seen = mutableListOf<String>()
        val values = flow {
            reads++
            if (reads <= 2) throw IOException("cache unreadable")
            emit("running")
            awaitCancellation()
        }

        val observing = launch { values.collectDurably("test", failures) { seen += it } }
        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("the first retry waits one second", 2, reads)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals("the second waits two", 2, reads)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("running"), seen)
        assertTrue("an expected failure is not reported", reported.isEmpty())
        observing.cancel()
    }

    @Test
    fun anUnexpectedFailureIsReportedAndTheSameValueGoesAgain() = runTest {
        var attempts = 0
        val seen = mutableListOf<String>()

        val observing = launch {
            flowOf("work").collectDurably("test", failures) {
                attempts++
                check(attempts > 1) { "a bug" }
                seen += it
            }
        }
        runCurrent()
        assertEquals(1, reported.size)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("work"), seen)
        assertTrue("the flow ended, and the observer with it", observing.isCompleted)
    }

    @Test
    fun theWaitsDoubleUpToTwoMinutes() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 64_000L, 120_000L, 120_000L),
            listOf(0, 1, 2, 6, 7, 30).map(::observerRetryDelay),
        )
    }
}
