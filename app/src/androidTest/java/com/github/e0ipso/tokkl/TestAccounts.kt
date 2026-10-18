package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.security.SecureRandom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Extra accounts on the Tokkl test server, for a test that fills an account with data. The
 * account scripts/instrumented-tests.sh creates stays empty for the other tests. Its admin invites
 * each account, and the invite is accepted as the web app does. Needs the stored server to be the
 * test server, as [SessionRule] leaves it, because the app's client sends nowhere else.
 */
object TestAccounts {

    /** `toString` leaves out the token. */
    class Account(val id: Long, val email: String, val apiToken: String) {
        val client: TogglApiClient get() = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(apiToken))

        override fun toString(): String = "Account($id)"
    }

    private val admin get() = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken))

    fun create(timezone: String): Account {
        val invite = admin
            .send("POST", TokklTestServer.url.app("admin", "invites"), buildJsonObject { put("role", "user") }, JsonObject.serializer())
            .orFail("POST /api/app/admin/invites")
        val token = invite.getValue("url").jsonPrimitive.content.substringAfterLast('/')
        val email = "cache-${hex(8)}@example.com"
        val password = hex(16)
        val account = buildJsonObject {
            put("email", email)
            put("password", password)
            put("fullname", "Cache test")
            put("timezone", timezone)
        }
        // The web app's form, which needs the CSRF header and no Basic auth.
        val accept = Request.Builder()
            .url(TokklTestServer.url.app("invites", token))
            .header("X-Tokkl-Csrf", "1")
            .post(account.toString().toRequestBody("application/json".toMediaType()))
            .build()
        SharedHttpClient.instance.newCall(accept).execute().use { response ->
            if (!response.isSuccessful) throw AssertionError("POST /api/app/invites/<token> failed: HTTP ${response.code}")
        }
        val me = TogglApiClient(TokklTestServer.url, ApiCredentials.EmailPassword(email, password))
            .get(TokklTestServer.url.api("me"), NewMe.serializer())
            .orFail("GET /api/v9/me")
        return Account(me.id, email, me.apiToken)
    }

    /** Deletes [account] with its workspace and everything in it. */
    fun delete(account: Account) {
        val confirm = buildJsonObject { put("email", account.email) }
        admin.sendNoContent("DELETE", TokklTestServer.url.app("admin", "users", account.id.toString()), confirm)
            .orFail("DELETE /api/app/admin/users/<id>")
    }

    private fun hex(bytes: Int): String =
        ByteArray(bytes).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

    @Serializable
    private class NewMe(val id: Long, @SerialName("api_token") val apiToken: String)
}

/** The value, or an error that names [call] and the failure alone, so no body or secret lands in it. */
fun <T> ApiResult<T>.orFail(call: String): T =
    when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> {
            val error = error
            val reason = if (error is ApiError.Http) "HTTP ${error.status}" else error::class.simpleName
            throw AssertionError("$call failed: $reason")
        }
    }
