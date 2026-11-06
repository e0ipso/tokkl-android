package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.ApiError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When [TimerSync] opens, closes and reopens the timer event stream: the watchdog on a silent
 * stream, the capped backoff, the network's return, a refused credential, sign-out, capability
 * loss and a new account. A hand-driven clock stands in for real waits.
 */
class TimerSyncLifecycleTest {

    private val clock = ManualClock()

    /** Every call opened, in order, with its account and the time it opened. */
    private val opens = mutableListOf<Opened>()
    private val stops = mutableListOf<Pair<String, StreamEnd>>()
    private val sync = TimerSync<String>(
        clock = clock,
        open = { account, link -> opens += Opened(account, link, clock.time) },
        stopped = { account, end -> stops += account to end },
    )

    @Test
    fun heartbeatsKeepAStreamOpenAndAboutAMinuteOfSilenceClosesIt() {
        sync.run(ANA)
        val stream = opens.single().link
        stream.connected()
        repeat(12) {
            clock.advanceBy(25_000)
            stream.alive()
        }
        assertTrue("five minutes of keep-alives keep it open", stream.active)

        clock.advanceBy(59_000)
        assertTrue(stream.active)
        clock.advanceBy(1_000)
        assertFalse("a minute without a keep-alive closes it", stream.active)
        assertTrue("the dead call's cancellation reaches OkHttp", isCancelled(stream))

        // It had been healthy, so the reconnect waits the shortest delay.
        clock.advanceBy(TimerSync.FIRST_DELAY_MILLIS)
        assertEquals(2, opens.size)
        assertEquals(ANA, opens.last().account)
    }

    @Test
    fun aStreamThatNeverAnswersIsClosedByTheSameWatchdog() {
        sync.run(ANA)

        clock.advanceBy(TimerSync.SILENCE_MILLIS)

        assertFalse("headers that never come count as silence", opens.single().link.active)
    }

    @Test
    fun failedReconnectsBackOffExponentiallyUpToACapAndAHealthyStreamStartsOver() {
        sync.run(ANA)
        val waits = (1..10).map {
            opens.last().link.ended(StreamEnd.Failed(ApiError.Network(IOException("Connection refused"))))
            waitForOpen()
        }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 120_000L, 120_000L, 120_000L), waits)

        // A stream that stays up a minute and then ends starts the delays over.
        val healthy = opens.last().link
        healthy.connected()
        clock.advanceBy(25_000)
        healthy.alive()
        clock.advanceBy(25_000)
        healthy.alive()
        clock.advanceBy(10_000)
        healthy.ended(StreamEnd.Ended)
        assertEquals(1_000L, waitForOpen())

        // One that ends at once does not.
        opens.last().link.connected()
        opens.last().link.ended(StreamEnd.Ended)
        assertEquals(2_000L, waitForOpen())
    }

    @Test
    fun theDelayStaysAtTheCapHoweverManyFailuresCameBefore() {
        assertEquals(TimerSync.MAX_DELAY_MILLIS, TimerSync.delayAfter(Int.MAX_VALUE))
    }

    @Test
    fun aReturningNetworkReconnectsAtOnceWithoutASecondCall() {
        sync.run(ANA)
        // The server is unreachable for a while, so the next wait has grown to 32 s.
        repeat(5) {
            opens.last().link.ended(StreamEnd.Failed(ApiError.Network(IOException("No route to host"))))
            waitForOpen()
        }
        opens.last().link.ended(StreamEnd.Failed(ApiError.Network(IOException("Network is unreachable"))))
        val before = opens.size

        sync.networkAvailable()
        assertEquals("the network's return connects at once", before + 1, opens.size)
        assertEquals(clock.time, opens.last().at)

        sync.networkAvailable()
        opens.last().link.connected()
        sync.networkAvailable()
        assertEquals("a call is open, so no second one", before + 1, opens.size)

        // The 32 s wait that was pending when the network came back opens nothing either.
        clock.advanceBy(40_000)
        assertEquals(before + 1, opens.size)

        // Losing the network closes the call, and its return reconnects at once.
        val open = opens.last().link
        sync.networkLost()
        assertFalse(open.active)
        sync.networkAvailable()
        assertEquals(before + 2, opens.size)
        assertEquals(clock.time, opens.last().at)
        clock.advanceBy(40_000)
        assertEquals("the reconnect the loss scheduled was dropped", before + 2, opens.size)
    }

    @Test
    fun aRefusedCredentialStopsReconnectingAndAsksToSignInAgain() {
        sync.run(ANA)
        // The token is reset during the stream: the server ends it, and the reconnect gets 403.
        opens.last().link.connected()
        opens.last().link.ended(StreamEnd.Ended)
        clock.advanceBy(TimerSync.FIRST_DELAY_MILLIS)
        opens.last().link.ended(StreamEnd.Refused)

        assertEquals(listOf(ANA to StreamEnd.Refused), stops)
        clock.advanceBy(10 * TimerSync.MAX_DELAY_MILLIS)
        sync.networkAvailable()
        sync.run(ANA)
        assertEquals("nothing retries the refused credential", 2, opens.size)

        // Signing in again makes a new account, whose stream starts.
        sync.run(BEN)
        assertEquals(BEN, opens.last().account)
        assertEquals(3, opens.size)
    }

    @Test
    fun aStreamWithNoAccountStopsWithoutAskingToSignIn() {
        sync.run(ANA)
        opens.single().link.ended(StreamEnd.SignedOut)

        assertEquals(listOf(ANA to StreamEnd.SignedOut), stops)
        clock.advanceBy(10 * TimerSync.MAX_DELAY_MILLIS)
        assertEquals(1, opens.size)
    }

    @Test
    fun signOutOrCapabilityLossCancelsTheStreamAndEveryReconnect() {
        sync.run(ANA)
        val open = opens.single().link
        open.connected()

        sync.stop()
        assertFalse(open.active)
        assertTrue(isCancelled(open))
        // The cancelled call's late end changes nothing: no reconnect and no sign-in prompt.
        open.ended(StreamEnd.Refused)
        open.alive()
        clock.advanceBy(10 * TimerSync.MAX_DELAY_MILLIS)
        sync.networkAvailable()
        assertEquals(1, opens.size)
        assertEquals(emptyList<Pair<String, StreamEnd>>(), stops)

        // Stopping while a reconnect waits drops the reconnect.
        sync.run(ANA)
        opens.last().link.ended(StreamEnd.Failed(null))
        sync.stop()
        clock.advanceBy(10 * TimerSync.MAX_DELAY_MILLIS)
        assertEquals(2, opens.size)
    }

    @Test
    fun anotherAccountReplacesTheStreamAndTheOldOnesLateCallbacksChangeNothing() {
        sync.run(ANA)
        val old = opens.single().link
        sync.run(ANA)
        assertEquals("the same account keeps its stream", 1, opens.size)

        sync.run(BEN)
        assertFalse(old.active)
        assertEquals(BEN, opens.last().account)
        val current = opens.last().link
        current.connected()

        old.ended(StreamEnd.Refused)
        old.ended(StreamEnd.Failed(null))
        clock.advanceBy(TimerSync.SILENCE_MILLIS - 1)
        assertEquals(emptyList<Pair<String, StreamEnd>>(), stops)
        assertEquals(2, opens.size)
        assertTrue("the old stream's end did not touch the new one", current.active)
    }

    @Test
    fun aStartupThatOutlivesItsServiceCannotReopenTheStoppedStream() {
        var ownsStream = true
        sync.run(ANA) { ownsStream }
        val original = opens.single().link
        // The service stops while another startup is still reading its account on IO.
        ownsStream = false
        sync.stop()
        sync.run(ANA) { ownsStream }
        sync.networkAvailable()
        clock.advanceBy(10 * TimerSync.MAX_DELAY_MILLIS)
        assertFalse(original.active)
        assertEquals("a late startup opens no orphan stream or reconnect", 1, opens.size)
    }

    @Test
    fun aStartupFromAnOldOwnerCannotReplaceTheNewOwnersStream() {
        sync.run(BEN)
        val current = opens.single().link
        sync.run(ANA) { false }
        assertTrue("the current owner's stream stays open", current.active)
        assertEquals("the old owner sends no request", listOf(BEN), opens.map { it.account })
    }

    /** Advances the clock until the next call opens, and returns how long that took. */
    private fun waitForOpen(): Long {
        val from = clock.time
        val count = opens.size
        while (opens.size == count) {
            check(clock.time - from <= TimerSync.MAX_DELAY_MILLIS) { "no call opened" }
            clock.advanceBy(STEP_MILLIS)
        }
        return opens.last().at - from
    }

    private fun isCancelled(link: TimerSyncLink): Boolean =
        try {
            link.cancellation.check()
            false
        } catch (e: IOException) {
            true
        }

    private data class Opened(val account: String, val link: TimerSyncLink, val at: Long)

    /** Runs scheduled tasks in time order, only as far as the test advances it. */
    private class ManualClock : TimerSyncClock {
        var time = 0L
            private set
        private var sequence = 0L
        private val tasks = mutableListOf<Task>()

        override fun now(): Long = time

        override fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit {
            val scheduled = Task(time + delayMillis, sequence++, task)
            tasks += scheduled
            return { tasks.remove(scheduled) }
        }

        fun advanceBy(millis: Long) {
            val until = time + millis
            while (true) {
                val next = tasks.filter { it.at <= until }.minWithOrNull(compareBy<Task>({ it.at }, { it.sequence })) ?: break
                tasks.remove(next)
                time = next.at
                next.run()
            }
            time = until
        }

        private class Task(val at: Long, val sequence: Long, val run: () -> Unit)
    }

    private companion object {
        const val ANA = "ana@example.com"
        const val BEN = "ben@example.com"
        const val STEP_MILLIS = 250L
    }
}
