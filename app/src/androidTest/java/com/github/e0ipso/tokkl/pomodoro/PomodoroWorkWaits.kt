package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import org.junit.Assert.assertTrue
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Waits for work the Pomodoro receivers and the ringing service do off the test thread. Each wait
 * puts a counting wrapper around the app's own handler in the Koin graph, so a receiver or service
 * made meanwhile runs the real work through it, and puts the app's own handler back afterwards.
 */
object PomodoroWorkWaits {

    private val koin: Koin get() = GlobalContext.get()

    /** Runs [send], then waits until [count] presses or swipes of the ringing notification are done. */
    fun ringingButtons(count: Int, seconds: Long = 10, what: String = "notification action", send: () -> Unit) {
        val real = koin.get<PomodoroRingingButtons>()
        val done = CountDownLatch(count)
        koin.declare<PomodoroRingingButtons>(
            object : PomodoroRingingButtons {
                override suspend fun press(phase: PomodoroPhaseKey, revision: Long, end: Boolean) = try {
                    real.press(phase, revision, end)
                } finally {
                    done.countDown()
                }

                override suspend fun repost(phase: PomodoroPhaseKey) = try {
                    real.repost(phase)
                } finally {
                    done.countDown()
                }
            },
            allowOverride = true,
        )
        try {
            send()
            assertTrue("$what did not finish", done.await(seconds, TimeUnit.SECONDS))
        } finally {
            koin.declare<PomodoroRingingButtons>(real, allowOverride = true)
        }
    }

    /** Runs [send], then waits until [count] presses of the session notification's buttons are done. */
    fun sessionButtons(count: Int, seconds: Long = 15, what: String = "notification actions", send: () -> Unit) {
        val real = koin.get<PomodoroSessionButtons>()
        val done = CountDownLatch(count)
        koin.declare<PomodoroSessionButtons>(
            object : PomodoroSessionButtons {
                override suspend fun press(account: String, phase: PomodoroPhaseKey, revision: Long, end: Boolean) =
                    try {
                        real.press(account, phase, revision, end)
                    } finally {
                        done.countDown()
                    }
            },
            allowOverride = true,
        )
        try {
            send()
            assertTrue("$what did not finish", done.await(seconds, TimeUnit.SECONDS))
        } finally {
            koin.declare<PomodoroSessionButtons>(real, allowOverride = true)
        }
    }

    /**
     * Counts the ringing service's checks of a start against the held phase until closed. Open it
     * before the service starts: a running service keeps the check it resolved.
     */
    class RingChecks : AutoCloseable {
        private val real = koin.get<PomodoroRingCheck>()
        private val checked = Semaphore(0)

        init {
            koin.declare<PomodoroRingCheck>(
                object : PomodoroRingCheck {
                    override suspend fun withRingingPhase(
                        expected: PomodoroPhaseKey?,
                        use: suspend (PomodoroRing) -> Unit,
                    ): Boolean = try {
                        real.withRingingPhase(expected, use)
                    } finally {
                        checked.release()
                    }
                },
                allowOverride = true,
            )
        }

        /** Runs [start], then waits until the service has checked one more start. */
        fun awaitOne(what: String, seconds: Long = 10, start: () -> Unit) {
            checked.drainPermits()
            start()
            assertTrue("$what was not checked", checked.tryAcquire(seconds, TimeUnit.SECONDS))
        }

        override fun close() {
            koin.declare<PomodoroRingCheck>(real, allowOverride = true)
        }
    }
}
