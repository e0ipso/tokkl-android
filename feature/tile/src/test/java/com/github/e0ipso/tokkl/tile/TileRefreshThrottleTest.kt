package com.github.e0ipso.tokkl.tile

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileRefreshThrottleTest {
    private val throttle = TileRefreshThrottle()

    @Test
    fun anOfflineOpeningDoesNotSpendTheNextOnlineAttempt() {
        assertFalse(throttle.tryStart(0, online = false, now = 0))
        assertTrue(throttle.tryStart(0, online = true, now = 1))
    }

    @Test
    fun successfulReadsWaitAFullMinuteAfterTheAnswer() {
        assertTrue(throttle.tryStart(0, online = true, now = 0))
        throttle.finished(0, succeeded = true, now = 1_000)
        assertFalse(throttle.tryStart(0, online = true, now = 60_000))
        assertFalse(throttle.tryStart(0, online = true, now = 60_999))
        assertTrue(throttle.tryStart(0, online = true, now = 61_000))
    }

    @Test
    fun aHungReadStaysSingleEvenAfterTheMinuteElapses() {
        assertTrue(throttle.tryStart(0, online = true, now = 0))
        assertFalse(throttle.tryStart(0, online = true, now = 60_000))
        assertFalse(throttle.tryStart(0, online = true, now = 600_000))
        throttle.finished(0, succeeded = true, now = 600_001)
        assertFalse(throttle.tryStart(0, online = true, now = 600_002))
    }

    @Test
    fun failuresAlsoRespectTheMinuteRequestCap() {
        assertTrue(throttle.tryStart(0, online = true, now = 0))
        throttle.finished(0, succeeded = false, now = 1_000)
        assertFalse(throttle.tryStart(0, online = true, now = 59_999))
        assertTrue(throttle.tryStart(0, online = true, now = 60_000))
    }

    @Test
    fun aNewAccountCanRefreshWhileTheOldAccountsReadStillHangs() {
        assertTrue(throttle.tryStart(0, online = true, now = 0))
        assertTrue(throttle.tryStart(1, online = true, now = 1))
        throttle.finished(0, succeeded = true, now = 2)
        assertFalse(throttle.tryStart(1, online = true, now = 60_001))
        throttle.finished(1, succeeded = false, now = 60_002)
        assertTrue(throttle.tryStart(1, online = true, now = 60_003))
    }

    @Test
    fun anOldOpeningCannotResetTheNewAccountsThrottle() {
        assertTrue(throttle.tryStart(1, online = true, now = 0))
        throttle.finished(1, succeeded = true, now = 1)
        assertFalse(throttle.tryStart(0, online = true, now = 2))
        assertFalse(throttle.tryStart(1, online = true, now = 3))
    }

    @Test
    fun simultaneousOpeningsStartOnlyOneRead() {
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..8).map {
                executor.submit<Boolean> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    throttle.tryStart(0, online = true, now = 0)
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, attempts.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
