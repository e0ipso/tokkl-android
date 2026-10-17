package com.github.e0ipso.tokkl

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun appCodeIsOnTheUnitTestClasspath() {
        assertEquals("com.github.e0ipso.tokkl", MainActivity::class.java.`package`?.name)
    }
}
