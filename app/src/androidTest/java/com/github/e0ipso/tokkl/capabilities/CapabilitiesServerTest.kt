package com.github.e0ipso.tokkl.capabilities

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Against the real Tokkl container every capability is on, also favorites on the fresh account
 * scripts/instrumented-tests.sh made, which has saved none. The flags survive a restart of the
 * app process. The steps run in name order, and the test orchestrator starts a new process for
 * each. A step fails if it runs in the same process as the one before.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CapabilitiesServerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication

    /** Names the process that ran the step before. */
    private val previousStep = File(context.cacheDir, "CapabilitiesServerTest.process")

    @Test
    fun step1RefreshTurnsEveryCapabilityOnForAFreshTokklAccount() {
        SessionRule.signOut()
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
        val api = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken))
        val preferences = api.get(api.server.api("me", "preferences"), JsonObject.serializer()).orFail("GET /api/v9/me/preferences")
        assertFalse("the account has saved no favorites", "favorites" in preferences)
        assertEquals("nothing is stored before the refresh", Capabilities.NONE, app.capabilities.state.value)

        runBlocking { app.accountSettings.refresh().join() }

        assertEquals(ALL, app.capabilities.state.value)
        assertEquals("the stored flags", ALL, CapabilitiesStore(context).load())
        previousStep.writeText(currentProcess())
    }

    @Test
    fun step2TheFlagsSurviveARestart() {
        try {
            assertTrue("the step before did not run", previousStep.exists())
            assertNotEquals("the step before ran in this same process", previousStep.readText(), currentProcess())
            // Nothing refreshes here, so the flags come from the file.
            assertEquals(ALL, app.capabilities.state.value)
        } finally {
            SessionRule.signOut()
            previousStep.delete()
        }
    }

    /** A PID can be reused, so the process start time goes with it. */
    private fun currentProcess(): String = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"

    private companion object {
        val ALL = Capabilities(
            trash = true,
            restore = true,
            favorites = true,
            timeResolution = true,
            negatedFilters = true,
            summaryCsv = true,
            detailedCsv = true,
        )
    }
}
