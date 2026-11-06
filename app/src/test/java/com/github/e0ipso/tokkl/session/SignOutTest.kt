package com.github.e0ipso.tokkl.session

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutTest {

    private val ran = mutableListOf<SignOutOrder>()

    private fun step(order: SignOutOrder, fails: () -> Boolean = { false }) = SignOutStep(order) {
        ran += order
        if (fails()) throw IOException("$order failed")
    }

    @Test
    fun runsEveryStepInSignOutOrderAndCountsTheSignOut() {
        val signOut = SignOut(
            listOf(step(SignOutOrder.CACHE), step(SignOutOrder.SESSION), step(SignOutOrder.CHANGE_SENDER)),
            Dispatchers.Unconfined,
        )

        assertTrue(runBlocking { signOut.run() })

        assertEquals(listOf(SignOutOrder.SESSION, SignOutOrder.CHANGE_SENDER, SignOutOrder.CACHE), ran)
        assertEquals(1, signOut.finished.value)
    }

    @Test
    fun aFailedStepDoesNotStopTheOthersButTheSignOutDoesNotCount() {
        val signOut = SignOut(
            listOf(step(SignOutOrder.SESSION), step(SignOutOrder.CHANGE_SENDER, fails = { true }), step(SignOutOrder.CACHE)),
            Dispatchers.Unconfined,
        )

        assertFalse(runBlocking { signOut.run() })

        assertEquals(listOf(SignOutOrder.SESSION, SignOutOrder.CHANGE_SENDER, SignOutOrder.CACHE), ran)
        assertEquals(0, signOut.finished.value)
    }

    @Test
    fun aSecondSignOutRunsEveryStepAgain() {
        var failing = true
        val signOut = SignOut(listOf(step(SignOutOrder.SESSION), step(SignOutOrder.CACHE, fails = { failing })), Dispatchers.Unconfined)

        assertFalse(runBlocking { signOut.run() })
        failing = false
        assertTrue(runBlocking { signOut.run() })

        assertEquals(listOf(SignOutOrder.SESSION, SignOutOrder.CACHE, SignOutOrder.SESSION, SignOutOrder.CACHE), ran)
        assertEquals(1, signOut.finished.value)
    }

    @Test
    fun twoStepsCannotShareAnOrder() {
        assertThrows(IllegalArgumentException::class.java) {
            SignOut(listOf(step(SignOutOrder.CACHE), step(SignOutOrder.CACHE)), Dispatchers.Unconfined)
        }
    }

    /**
     * The order of sign-out (docs/PRD.md S6). The session goes first, so nothing new is sent, and
     * the timer state next, so a late answer writes nothing. What still sends stops before the
     * stores clear.
     */
    @Test
    fun theStepsRunInTheOrderSignOutNeeds() {
        assertEquals(
            listOf(
                "SESSION", "TIMER_STATE", "POMODORO_ALARM", "TIMER_SYNC", "POMODORO_NOTIFICATION",
                "POMODORO_NOTIFICATION_ID", "BACKGROUND_SEND", "RUNNING_CHECK", "HTTP_CALLS", "ACCOUNT_REFRESH",
                "CHANGE_SENDER", "TIMER_STATE_STORE", "POMODORO_WIDGET_FIELDS", "CACHE", "REMINDER",
                "REMINDER_SETTING", "RUNNING_NOTIFICATION", "FAVORITE_SHORTCUTS", "ACCOUNT_SETTINGS", "CAPABILITIES",
                "TIMER_VIEW", "POMODORO_DEFAULTS", "SERVER_TRUST", "SESSION_TRUST", "LAST_ANSWER",
                "DISCARDED_CHANGES",
            ),
            SignOutOrder.entries.map { it.name },
        )
    }
}
