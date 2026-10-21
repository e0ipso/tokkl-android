package com.github.e0ipso.tokkl.cache

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forced interleavings between registration and a stale waiting-work cancellation. */
class BackgroundSendRunsTest {
    @Test
    fun aWorkerJoiningAfterTheWaitingReadCannotBeCancelled() {
        val runs = BackgroundSendRuns()
        val id = UUID.randomUUID()
        val readWaiting = CountDownLatch(1)
        val returnWaiting = CountDownLatch(1)
        val cancelled = AtomicBoolean()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val cancellation = executor.submit {
                runs.cancelWaiting(
                    waiting = {
                        readWaiting.countDown()
                        await(returnWaiting)
                        true
                    },
                    cancel = { cancelled.set(true) },
                    queueEmpty = { true },
                    schedule = { throw AssertionError("an empty queue schedules nothing") },
                )
            }
            await(readWaiting)
            assertTrue(runs.join(id) { true })
            returnWaiting.countDown()
            cancellation.get(10, TimeUnit.SECONDS)
            assertFalse("the stale waiting read cannot cancel a registered worker", cancelled.get())
            assertTrue(runs.hasJoined(id))
        } finally {
            returnWaiting.countDown()
            runs.leave(id)
            executor.shutdownNow()
        }
    }

    @Test
    fun aWorkerCancelledBeforeRegistrationNeverJoins() {
        val runs = BackgroundSendRuns()
        val id = UUID.randomUUID()
        val cancelling = CountDownLatch(1)
        val finishCancel = CountDownLatch(1)
        val registering = CountDownLatch(1)
        val cancelled = AtomicBoolean()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val cancellation = executor.submit {
                runs.cancelWaiting(
                    waiting = { true },
                    cancel = {
                        cancelling.countDown()
                        await(finishCancel)
                        cancelled.set(true)
                    },
                    queueEmpty = { true },
                    schedule = { throw AssertionError("an empty queue schedules nothing") },
                )
            }
            await(cancelling)
            val joined = executor.submit<Boolean> {
                registering.countDown()
                runs.join(id) { !cancelled.get() }
            }
            await(registering)
            finishCancel.countDown()
            cancellation.get(10, TimeUnit.SECONDS)
            assertFalse("registration reads the completed cancellation", joined.get(10, TimeUnit.SECONDS))
            assertFalse(runs.hasJoined(id))
        } finally {
            finishCancel.countDown()
            runs.leave(id)
            executor.shutdownNow()
        }
    }

    @Test
    fun aChangeQueuedDuringCancellationIsCoveredAgain() {
        val runs = BackgroundSendRuns()
        val queued = AtomicBoolean()
        val scheduled = AtomicInteger()
        runs.cancelWaiting(
            waiting = { true },
            cancel = { queued.set(true) },
            queueEmpty = { !queued.get() },
            schedule = { scheduled.incrementAndGet() },
        )
        assertEquals(1, scheduled.get())
    }

    @Test
    fun aFailedWorkManagerOperationStillCoversANewChange() {
        val scheduled = AtomicInteger()
        BackgroundSendRuns().cancelWaiting(
            waiting = { true },
            cancel = { throw ExecutionException(IllegalStateException("write failed")) },
            queueEmpty = { false },
            schedule = { scheduled.incrementAndGet() },
        )
        assertEquals(1, scheduled.get())
    }

    private fun await(latch: CountDownLatch) {
        assertTrue("the other operation reached its barrier", latch.await(10, TimeUnit.SECONDS))
    }
}
