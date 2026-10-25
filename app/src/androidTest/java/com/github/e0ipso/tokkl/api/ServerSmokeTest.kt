package com.github.e0ipso.tokkl.api

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            is ApiError.UntrustedCertificate -> "refused the certificate of ${certificate.host}:${certificate.port}"
            is ApiError.CertificateHostMismatch -> "refused a certificate that does not name $host"
        }
}
