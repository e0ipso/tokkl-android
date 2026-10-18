package com.github.e0ipso.tokkl.api

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Talks to the real Tokkl container over plain HTTP, which the network
 * security config permits once the user has confirmed the URL. Assertion
 * messages never carry the token or the password.
 */
class ServerSmokeTest {

    private val client = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken))

    private val servers = ServerConfigStore(InstrumentationRegistry.getInstrumentation().targetContext)

    private val trust = ServerTrustStore(InstrumentationRegistry.getInstrumentation().targetContext)

    /** The shared client sends requests only to the stored server, and to a plain http one only once confirmed. */
    @Before
    fun configureTheServer() {
        servers.save(TokklTestServer.url)
        trust.confirmCleartext(TokklTestServer.url)
    }

    /** TokklApplication points the guard at the stored trust decisions, so an unconfirmed http server gets nothing. */
    @Test
    fun unconfirmedHttpServerGetsNoRequest() {
        trust.clear()

        val result = client.get(client.server.api("me"), JsonObject.serializer())

        assertEquals(ApiError.CleartextNotConfirmed(TokklTestServer.url), (result as? ApiResult.Failure)?.error)
    }

    /** TokklApplication points the host guard at the stored server, so with none stored nothing goes out. */
    @Test
    fun withNoStoredServerTheGuardRefusesTheRequest() {
        servers.clear()
        val me = client.server.api("me")

        val result = client.get(me, JsonObject.serializer())

        assertEquals(ApiError.HostNotAllowed("${me.scheme}://${me.host}:${me.port}"), (result as? ApiResult.Failure)?.error)
    }

    @Test
    fun apiTokenAuthenticatesWithGetMe() {
        assertEquals(TokklTestServer.email, email(client))
    }

    @Test
    fun emailAndPasswordAuthenticateWithGetMe() {
        val credentials = ApiCredentials.EmailPassword(TokklTestServer.email, TokklTestServer.password)

        assertEquals(TokklTestServer.email, email(TogglApiClient(TokklTestServer.url, credentials)))
    }

    @Test
    fun wrongApiTokenGets403() {
        val client = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken("not-the-api-token"))

        val result = client.get(client.server.api("me"), JsonObject.serializer())

        // Compares the error alone, so a success body never lands in the message.
        assertEquals(ApiError.AuthFailed, (result as? ApiResult.Failure)?.error)
    }

    /** Tokkl answers a tag DELETE with 200 and an empty body. */
    @Test
    fun deleteWithAnEmptyResponseBodySucceeds() {
        val tags = client.server.api("workspaces", workspaceId(), "tags")
        val name = "smoke-${UUID.randomUUID()}"
        val tag = client.send("POST", tags, buildJsonObject { put("name", name) }, JsonObject.serializer())
            .valueOrFail("POST tags")
        assertEquals(name, tag.string("name"))

        val deleted = client.sendNoContent("DELETE", tags.newBuilder().addPathSegment(tag.string("id")).build())

        assertEquals(ApiResult.Success(Unit), deleted)
        val left = client.get(tags, JsonArray.serializer()).valueOrFail("GET tags")
        assertFalse("the deleted tag is still listed", left.any { it.jsonObject.string("id") == tag.string("id") })
    }

    @Test
    fun reportsSearchFindsAnEntryCreatedThroughTheApi() {
        val workspaceId = workspaceId()
        val entries = client.server.api("workspaces", workspaceId, "time_entries")
        val start = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS)
        val entry = client.send(
            "POST",
            entries,
            buildJsonObject {
                put("workspace_id", workspaceId.toLong())
                put("description", "smoke-${UUID.randomUUID()}")
                put("start", start.toString())
                put("duration", 600)
                put("created_with", "tokkl-android instrumented tests")
            },
            JsonObject.serializer(),
        ).valueOrFail("POST time_entries")
        val entryId = entry.string("id")
        try {
            // The test account's timezone is UTC (scripts/instrumented-tests.sh).
            val day = start.atZone(ZoneOffset.UTC).toLocalDate()
            val rows = client.send(
                "POST",
                client.server.reports("workspace", workspaceId, "search", "time_entries"),
                buildJsonObject {
                    put("start_date", day.minusDays(1).toString())
                    put("end_date", day.plusDays(1).toString())
                },
                JsonArray.serializer(),
            ).valueOrFail("POST reports search/time_entries")

            val found = rows.any { row ->
                row.jsonObject.getValue("time_entries").jsonArray.any { it.jsonObject.string("id") == entryId }
            }
            assertTrue("the report has no row for the new entry", found)
        } finally {
            client.sendNoContent("DELETE", entries.newBuilder().addPathSegment(entryId).build())
        }
    }

    private fun email(client: TogglApiClient): String =
        client.get(client.server.api("me"), JsonObject.serializer()).valueOrFail("GET /api/v9/me").string("email")

    private fun workspaceId(): String =
        client.get(client.server.api("me"), JsonObject.serializer()).valueOrFail("GET /api/v9/me")
            .string("default_workspace_id")

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    /** Fails with the error alone, so neither a response body nor a secret lands in the message. */
    private fun <T> ApiResult<T>.valueOrFail(call: String): T =
        when (this) {
            is ApiResult.Success -> value
            is ApiResult.Failure -> throw AssertionError("$call failed: ${error.describe()}")
        }

    private fun ApiError.describe(): String =
        when (this) {
            is ApiError.Http -> "HTTP $status"
            is ApiError.Network -> "network error: $cause"
            is ApiError.InvalidResponse -> "response is not the expected JSON"
            is ApiError.HostNotAllowed -> "refused a request to $origin"
            is ApiError.CleartextNotConfirmed -> "refused a request to $server, an unconfirmed http server"
        }
}
