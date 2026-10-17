package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.TokklTestServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Talks to the real Tokkl container over plain HTTP, which the network
 * security config permits. Assertion messages never carry the token.
 */
class ServerSmokeTest {

    @Test
    fun apiTokenAuthenticatesWithGetMe() {
        val client = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken))

        val me = when (val result = client.get(client.server.api("me"), JsonObject.serializer())) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> throw AssertionError("GET /api/v9/me failed: ${result.error}")
        }

        assertEquals(TokklTestServer.email, me.getValue("email").jsonPrimitive.content)
    }

    @Test
    fun wrongApiTokenGets403() {
        val client = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken("not-the-api-token"))

        val result = client.get(client.server.api("me"), JsonObject.serializer())

        // Compares the error alone, so a success body never lands in the message.
        assertEquals(ApiError.AuthFailed, (result as? ApiResult.Failure)?.error)
    }
}
