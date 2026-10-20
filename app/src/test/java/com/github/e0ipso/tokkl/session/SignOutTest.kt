package com.github.e0ipso.tokkl.session

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutTest {

    private val ran = mutableListOf<String>()

    private fun step(name: String, fails: Boolean = false): suspend () -> Unit = {
        ran += name
        if (fails) throw IOException("$name failed")
    }

    @Test
    fun runsEveryStepInOrderAndCountsTheSignOut() {
        val signOut = SignOut(listOf(step("session"), step("work"), step("cache")), Dispatchers.Unconfined)

        assertTrue(runBlocking { signOut.run() })

        assertEquals(listOf("session", "work", "cache"), ran)
        assertEquals(1, signOut.finished.value)
    }

    @Test
    fun aFailedStepDoesNotStopTheOthersButTheSignOutDoesNotCount() {
        val signOut = SignOut(listOf(step("session"), step("work", fails = true), step("cache")), Dispatchers.Unconfined)

        assertFalse(runBlocking { signOut.run() })

        assertEquals(listOf("session", "work", "cache"), ran)
        assertEquals(0, signOut.finished.value)
    }

    @Test
    fun aSecondSignOutRunsEveryStepAgain() {
        var failing = true
        val flaky: suspend () -> Unit = {
            ran += "flaky"
            if (failing) throw IOException("disk full")
        }
        val signOut = SignOut(listOf(step("session"), flaky), Dispatchers.Unconfined)

        assertFalse(runBlocking { signOut.run() })
        failing = false
        assertTrue(runBlocking { signOut.run() })

        assertEquals(listOf("session", "flaky", "session", "flaky"), ran)
        assertEquals(1, signOut.finished.value)
    }
}
