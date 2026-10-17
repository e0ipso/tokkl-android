package com.github.e0ipso.tokkl.credentials

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * The token survives a restart of the app process. The steps run in name
 * order, and the test orchestrator (app/build.gradle.kts) starts a new process
 * for each, so step 2 reads what step 1 saved in another process. Step 2 fails
 * if both run in the same process.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ApiTokenStoreRestartTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ApiTokenStore(context)

    /** Names the process that saved the token. Holds no secret. */
    private val savedBy = File(context.cacheDir, "ApiTokenStoreRestartTest.process")

    @Test
    fun step1SavesTheToken() {
        store.clear()
        store.save(TOKEN)
        savedBy.writeText(currentProcess())
    }

    @Test
    fun step2LoadsTheTokenInANewProcess() {
        try {
            assertTrue("step 1 did not run before step 2", savedBy.exists())
            assertNotEquals("step 1 ran in this same process", savedBy.readText(), currentProcess())
            assertEquals(TOKEN, store.load())
        } finally {
            store.clear()
            savedBy.delete()
        }
    }

    /** A PID can be reused, so the process start time goes with it. */
    private fun currentProcess(): String = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"

    private companion object {
        const val TOKEN = "restart-test-token"
    }
}
