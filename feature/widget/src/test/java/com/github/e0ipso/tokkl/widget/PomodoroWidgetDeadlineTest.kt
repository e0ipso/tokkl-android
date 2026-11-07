package com.github.e0ipso.tokkl.widget

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PomodoroWidgetDeadlineTest {
    @Test fun aFractionalMillisecondCannotRedrawBeforeThePhaseEnds() = runBlocking {
        val time = Time()
        val draws = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Unconfined) {
            pomodoroWidgetDeadlineChanges(MutableStateFlow(Instant.ofEpochSecond(0, 1)), time, time::wait)
                .collect { draws.send(Unit) }
        }
        try {
            withTimeout(1_000) { draws.receive() }
            val pending = withTimeout(1_000) { time.waits.receive() }
            assertEquals("round up so the ended state is true at redraw", 1L, pending.milliseconds)
            time.now = 1
            pending.release.complete(Unit)
            withTimeout(1_000) { draws.receive() }
        } finally { job.cancel() }
    }

    @Test fun phaseEndRedrawDoesNotDependOnAnAlarmOrAnotherServerEvent() = runBlocking {
        val time = Time()
        val ends = MutableStateFlow<Instant?>(Instant.ofEpochMilli(1_000))
        val draws = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Unconfined) {
            pomodoroWidgetDeadlineChanges(ends, time, time::wait).collect { draws.send(Unit) }
        }
        try {
            withTimeout(1_000) { draws.receive() }
            val pending = withTimeout(1_000) { time.waits.receive() }
            assertEquals(1_000L, pending.milliseconds)
            assertTrue(draws.tryReceive().isFailure)
            time.now = 1_000
            pending.release.complete(Unit)
            withTimeout(1_000) { draws.receive() }
            assertTrue("one phase has one deadline", time.waits.tryReceive().isFailure)
            assertTrue("no repeated redraws", draws.tryReceive().isFailure)
        } finally { job.cancel() }
    }

    @Test fun aNewPhaseCancelsThePreviousDeadline() = runBlocking {
        val time = Time()
        val ends = MutableStateFlow<Instant?>(Instant.ofEpochMilli(1_000))
        val draws = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Unconfined) {
            pomodoroWidgetDeadlineChanges(ends, time, time::wait).collect { draws.send(Unit) }
        }
        try {
            withTimeout(1_000) { draws.receive() }
            val previous = withTimeout(1_000) { time.waits.receive() }
            time.now = 500
            ends.value = Instant.ofEpochMilli(3_000)
            withTimeout(1_000) { draws.receive() }
            val current = withTimeout(1_000) { time.waits.receive() }
            assertTrue("the old wait was cancelled", previous.finished)
            assertEquals(2_500L, current.milliseconds)
            previous.release.complete(Unit)
            assertTrue("the old phase cannot redraw", draws.tryReceive().isFailure)
            time.now = 3_000
            current.release.complete(Unit)
            withTimeout(1_000) { draws.receive() }
        } finally { job.cancel() }
    }

    @Test fun sessionEndCancelsTheDeadlineAndPastEndsNeedNoFutureWork() = runBlocking {
        val time = Time()
        val ends = MutableStateFlow<Instant?>(Instant.ofEpochMilli(1_000))
        val draws = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Unconfined) {
            pomodoroWidgetDeadlineChanges(ends, time, time::wait).collect { draws.send(Unit) }
        }
        try {
            withTimeout(1_000) { draws.receive() }
            val pending = withTimeout(1_000) { time.waits.receive() }
            ends.value = null
            withTimeout(1_000) { draws.receive() }
            assertTrue(pending.finished)
            assertTrue("idle has no deadline", time.waits.tryReceive().isFailure)
            time.now = 10_000
            ends.value = Instant.EPOCH
            withTimeout(1_000) { draws.receive() }
            assertTrue("an overdue restored phase needs no future wait", time.waits.tryReceive().isFailure)
        } finally { job.cancel() }
    }

    private class Pending(val milliseconds: Long) {
        val release = CompletableDeferred<Unit>()
        var finished = false
    }

    private class Time : Clock() {
        var now = 0L
        val waits = Channel<Pending>(Channel.UNLIMITED)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
        suspend fun wait(milliseconds: Long) {
            val pending = Pending(milliseconds)
            waits.send(pending)
            try { pending.release.await() } finally { pending.finished = true }
        }
    }
}
