package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Extra accounts on the Tokkl test server, for a test that fills an account with data. The
 * account scripts/instrumented-tests.sh creates stays empty for the other tests. Its admin invites
 * each account, and the invite is accepted as the web app does. Admin calls use a harness client,
 * so creating and deleting an account still reach the test server after a test signs out. Entry
 * calls use the app's client and need the stored server to be the test server, as [SessionRule]
 * leaves it, because that client sends nowhere else.
 */
object TestAccounts {

    /**
     * `toString` leaves out the token and the password. [password] is known only for an account
     * [create] made in this process.
     */
    class Account(val id: Long, val email: String, val apiToken: String, private val password: String? = null) {
        val client: TogglApiClient get() = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(apiToken))

        /**
         * Another device of the same user, such as the web app or a desktop client: it signs in
         * with the account's email and password, not the API token the phone uses, and talks to
         * the server straight, past the app's guard and its shared connections. It opens a new
         * connection for each request, so none of its calls goes out on a connection the server
         * has closed. The timer state it changes is the phone's too, as it is the same user's.
         */
        val otherDevice: TogglApiClient by lazy {
            val secret = checkNotNull(password) { "only an account made by TestAccounts.create has a password" }
            TogglApiClient(
                TokklTestServer.url,
                ApiCredentials.EmailPassword(email, secret),
                httpClient = OkHttpClient.Builder().connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS)).build(),
            )
        }

        /** The account's default workspace, where [createEntry] puts entries. */
        val workspaceId: Long by lazy {
            client.get(client.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
                .getValue("default_workspace_id").jsonPrimitive.long
        }

        /** Creates an entry from [start] to [stop], or a running one without [stop], and returns its id. */
        fun createEntry(description: String, start: Instant, stop: Instant? = null): Long {
            val body = buildJsonObject {
                put("workspace_id", workspaceId)
                put("created_with", "tokkl-android tests")
                put("description", description)
                put("start", start.toString())
                if (stop == null) put("duration", -1) else put("stop", stop.toString())
            }
            return client.send("POST", entries(), body, JsonObject.serializer())
                .orFail("POST /api/v9/workspaces/<id>/time_entries")
                .getValue("id").jsonPrimitive.long
        }

        /** The entry [id] as the server keeps it. */
        fun entry(id: Long): JsonObject =
            client.get(client.server.api("me", "time_entries", id.toString()), JsonObject.serializer())
                .orFail("GET /api/v9/me/time_entries/<id>")

        fun deleteEntry(id: Long) {
            client.sendNoContent("DELETE", entries(id.toString())).orFail("DELETE /api/v9/workspaces/<id>/time_entries/<id>")
        }

        private fun entries(vararg path: String) =
            client.server.api("workspaces", workspaceId.toString(), "time_entries", *path)

        override fun toString(): String = "Account($id)"
    }

    private val admin by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken), httpClient = OkHttpClient())
    }

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
        return Account(me.id, email, me.apiToken, password)
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
