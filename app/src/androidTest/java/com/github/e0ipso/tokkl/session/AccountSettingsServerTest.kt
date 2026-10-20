package com.github.e0ipso.tokkl.session

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import java.time.DayOfWeek
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The app's own settings source reads the account settings from the real Tokkl container. The
 * test changes the account's timezone and first day of the week, and puts back the UTC and
 * Monday that scripts/instrumented-tests.sh set up.
 */
class AccountSettingsServerTest {

    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val client = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken))

    @After
    fun restoreTheAccount() {
        try {
            putMe(timezone = "UTC", beginningOfWeek = 1)
        } finally {
            AccountSettingsStore(context).clear()
        }
    }

    @Test
    fun refreshReadsTheSettingsFromTheServerAndKeepsThem() {
        // Tokkl 1.5.0 stores the name Intl resolves, which accepts it in any case.
        putMe(timezone = "america/sao_paulo", beginningOfWeek = 0)
        val source = (context.applicationContext as TokklApplication).accountSettings

        runBlocking { source.refresh().join() }

        // Tokkl's instance time resolution is 5 minutes unless an admin changes it.
        val expected = AccountSettings("America/Sao_Paulo", DayOfWeek.SUNDAY, timeResolutionMinutes = 5.0, defaultWorkspaceId = defaultWorkspace())
        assertEquals(expected, source.state.value)
        assertEquals("the stored settings", expected, AccountSettingsStore(context).load())
        assertEquals(ZoneId.of("America/Sao_Paulo"), expected.time()?.zone)
    }

    private fun defaultWorkspace(): Long =
        client.get(client.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long

    private fun putMe(timezone: String, beginningOfWeek: Int) {
        val body = buildJsonObject {
            put("timezone", timezone)
            put("beginning_of_week", beginningOfWeek)
        }
        // Fails with the error alone, so neither a response body nor a secret lands in the message.
        val result = client.send("PUT", client.server.api("me"), body, JsonObject.serializer())
        if (result is ApiResult.Failure) {
            val error = result.error
            val reason = if (error is ApiError.Http) "HTTP ${error.status}" else error::class.simpleName
            throw AssertionError("PUT /api/v9/me failed: $reason")
        }
    }
}
