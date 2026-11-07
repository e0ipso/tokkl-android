package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SignOutTest {

    private val ran = mutableListOf<String>()

    private fun step(name: String, phase: SignOutPhase, fails: () -> Boolean = { false }) = SignOutStep(phase) {
        ran += name
        if (fails()) throw IOException("$name failed")
    }

    @Test
    fun runsThePhasesInOrderAndCountsTheSignOut() {
        val signOut = SignOut(
            listOf(
                step("cache", SignOutPhase.CLEAR_CACHE),
                step("settings", SignOutPhase.CLEAR_DATA),
                step("session", SignOutPhase.FORGET_SESSION),
                step("sender", SignOutPhase.FINISH_SENDING),
                step("stream", SignOutPhase.STOP_WORK),
                step("refresh", SignOutPhase.FINISH_REFRESH),
                step("timer state", SignOutPhase.RETIRE_STATE),
            ),
            Dispatchers.Unconfined,
        )

        assertTrue(runBlocking { signOut.run() })

        assertEquals(listOf("session", "timer state", "stream", "refresh", "sender", "cache", "settings"), ran)
        assertEquals(1, signOut.finished.value)
    }

    @Test
    fun aPhaseStartsOnlyOnceTheOneBeforeHasEnded() = runBlocking {
        val drainEnds = CompletableDeferred<Unit>()
        val signOut = SignOut(
            listOf(
                SignOutStep(SignOutPhase.FINISH_SENDING) {
                    drainEnds.await()
                    ran += "sender"
                },
                step("cache", SignOutPhase.CLEAR_CACHE),
            ),
            Dispatchers.Unconfined,
        )

        val running = async { signOut.run() }
        yield()
        assertEquals(emptyList<String>(), ran)
        drainEnds.complete(Unit)

        assertTrue(running.await())
        assertEquals(listOf("sender", "cache"), ran)
    }

    @Test
    fun stepsOfOnePhaseAllRun() {
        val signOut = SignOut(
            listOf(step("a", SignOutPhase.CLEAR_DATA), step("b", SignOutPhase.CLEAR_DATA)),
            Dispatchers.Unconfined,
        )

        assertTrue(runBlocking { signOut.run() })

        assertEquals(setOf("a", "b"), ran.toSet())
        assertEquals(2, ran.size)
    }

    @Test
    fun aFailedStepDoesNotStopTheOthersButTheSignOutDoesNotCount() {
        val signOut = SignOut(
            listOf(
                step("session", SignOutPhase.FORGET_SESSION),
                step("sender", SignOutPhase.FINISH_SENDING, fails = { true }),
                step("cache", SignOutPhase.CLEAR_CACHE),
            ),
            Dispatchers.Unconfined,
        )

        assertFalse(runBlocking { signOut.run() })

        assertEquals(listOf("session", "sender", "cache"), ran)
        assertEquals(0, signOut.finished.value)
    }

    @Test
    fun aSecondSignOutRunsEveryStepAgain() {
        var failing = true
        val signOut = SignOut(
            listOf(
                step("session", SignOutPhase.FORGET_SESSION),
                step("cache", SignOutPhase.CLEAR_CACHE, fails = { failing }),
            ),
            Dispatchers.Unconfined,
        )

        assertFalse(runBlocking { signOut.run() })
        failing = false
        assertTrue(runBlocking { signOut.run() })

        assertEquals(listOf("session", "cache", "session", "cache"), ran)
        assertEquals(1, signOut.finished.value)
    }

    /**
     * The order of sign-out (docs/PRD.md S6). The session goes first, so nothing new is sent, and
     * the timer state next, so a late answer writes nothing. What still runs stops, the refresh
     * ends before the drain it can start, and only then do the stores clear, the cache before
     * what follows it.
     */
    @Test
    fun thePhasesRunInTheOrderSignOutNeeds() {
        assertEquals(
            listOf(
                "FORGET_SESSION",
                "RETIRE_STATE",
                "STOP_WORK",
                "FINISH_REFRESH",
                "FINISH_SENDING",
                "CLEAR_CACHE",
                "CLEAR_DATA",
            ),
            SignOutPhase.entries.map { it.name },
        )
    }
}
