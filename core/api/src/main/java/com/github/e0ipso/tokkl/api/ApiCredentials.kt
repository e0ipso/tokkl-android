package com.github.e0ipso.tokkl.api

import okhttp3.Credentials

/**
 * HTTP Basic credentials for both APIs. Tokkl decodes the header as UTF-8 and
 * splits it at the first colon, so a password may contain colons and an email
 * may not. `toString` never prints a secret.
 */
sealed class ApiCredentials {

    /** The value of the `Authorization` header. */
    abstract val authorizationHeader: String

    /** Sends `<api_token>:api_token`. */
    class ApiToken(private val token: String) : ApiCredentials() {
        override val authorizationHeader: String
            get() = Credentials.basic(token, "api_token", Charsets.UTF_8)

        override fun toString(): String = "ApiToken(***)"
    }

    /** Sends `<email>:<password>`. */
    class EmailPassword(private val email: String, private val password: String) : ApiCredentials() {
        init {
            require(':' !in email) { "An email used for Basic auth cannot contain ':'" }
        }

        override val authorizationHeader: String
            get() = Credentials.basic(email, password, Charsets.UTF_8)

        override fun toString(): String = "EmailPassword(***)"
    }
}
