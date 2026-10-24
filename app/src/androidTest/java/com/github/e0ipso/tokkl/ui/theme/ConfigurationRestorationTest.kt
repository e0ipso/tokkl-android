package com.github.e0ipso.tokkl.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** Failure paths in the font/display rules must not hide the test or leak partial setup. */
class ConfigurationRestorationTest {
    @Test
    fun successRestoresAfterTheBody() {
        val calls = mutableListOf<String>()
        withConfigurationRestored({ calls += "change" }, { calls += "restore" }) { calls += "body" }
        assertEquals(listOf("change", "body", "restore"), calls)
    }

    @Test
    fun partialSetupIsRestoredAndTheBodyIsNotRun() {
        val calls = mutableListOf<String>()
        val setup = IllegalStateException("partial setup")
        val thrown = assertThrows(IllegalStateException::class.java) {
            withConfigurationRestored({ calls += "change"; throw setup }, { calls += "restore" }) { calls += "body" }
        }
        assertSame(setup, thrown)
        assertEquals(listOf("change", "restore"), calls)
    }

    @Test
    fun cleanupFailureIsSuppressedOnTheOriginalTestFailure() {
        val test = AssertionError("test")
        val cleanup = IllegalStateException("cleanup")
        val thrown = assertThrows(AssertionError::class.java) {
            withConfigurationRestored({}, { throw cleanup }) { throw test }
        }
        assertSame(test, thrown)
        assertEquals(listOf(cleanup), thrown.suppressed.toList())
    }

    @Test
    fun cleanupFailureAfterSuccessFailsTheTest() {
        val cleanup = IllegalStateException("cleanup")
        assertSame(cleanup, assertThrows(IllegalStateException::class.java) {
            withConfigurationRestored({}, { throw cleanup }) { }
        })
    }
}
