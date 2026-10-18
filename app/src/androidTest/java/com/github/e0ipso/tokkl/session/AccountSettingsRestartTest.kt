package com.github.e0ipso.tokkl.session

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.TokklApplication
import java.io.File
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * The account settings survive a restart of the app process, so screens have them offline. The
 * steps run in name order, and the test orchestrator (app/build.gradle.kts) starts a new process
 * for each. Each step reads what the step before stored through the app's own settings source,
 * which sends no request. A step fails if it runs in the same process as the one before.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AccountSettingsRestartTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val source get() = (context.applicationContext as TokklApplication).accountSettings

    /** Names the process that ran the step before. */
    private val previousStep = File(context.cacheDir, "AccountSettingsRestartTest.process")

    @Test
    fun step1StoresSettingsWithATimeResolution() {
        AccountSettingsStore(context).clear()
        source.store(WITH_RESOLUTION)
        previousStep.writeText(currentProcess())
    }

    @Test
    fun step2KeepsThemAndStoresSettingsWithout() {
        assertNewProcess()
        assertEquals(WITH_RESOLUTION, source.state.value)
        source.store(WITHOUT_RESOLUTION)
        previousStep.writeText(currentProcess())
    }

    @Test
    fun step3KeepsTheSettingsWithoutATimeResolution() {
        try {
            assertNewProcess()
            assertEquals(WITHOUT_RESOLUTION, source.state.value)
        } finally {
            AccountSettingsStore(context).clear()
            previousStep.delete()
        }
    }

    private fun assertNewProcess() {
        assertTrue("the step before did not run", previousStep.exists())
        assertNotEquals("the step before ran in this same process", previousStep.readText(), currentProcess())
    }

    /** A PID can be reused, so the process start time goes with it. */
    private fun currentProcess(): String = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"

    private companion object {
        val WITH_RESOLUTION = AccountSettings("Europe/Madrid", DayOfWeek.SUNDAY, timeResolutionMinutes = 15.0)
        val WITHOUT_RESOLUTION = AccountSettings("Asia/Kolkata", DayOfWeek.SATURDAY, timeResolutionMinutes = null)
    }
}
