package com.github.e0ipso.tokkl.platform

import com.github.e0ipso.tokkl.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityIntentTest {

    /** The surfaces below the app shell open the main activity by this name. */
    @Test
    fun theNameIsTheMainActivitysClass() {
        assertEquals(MainActivity::class.java.name, MAIN_ACTIVITY)
    }
}
