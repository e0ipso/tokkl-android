package com.github.e0ipso.tokkl.api

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiCredentialsTest {

    @Test
    fun apiTokenSendsTheTokenWithApiTokenAsPassword() {
        assertEquals(
            "Basic " + base64("1971800d4d82861d8f2c1651fea4d212:api_token"),
            ApiCredentials.ApiToken("1971800d4d82861d8f2c1651fea4d212").authorizationHeader,
        )
    }

    @Test
    fun emailAndPasswordAreSentAsUtf8() {
        assertEquals(
            "Basic " + base64("ana@example.com:pàss:wörd"),
            ApiCredentials.EmailPassword("ana@example.com", "pàss:wörd").authorizationHeader,
        )
    }

    @Test
    fun emailWithAColonIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiCredentials.EmailPassword("a:b@example.com", "secret")
        }
    }

    @Test
    fun toStringHidesSecrets() {
        val token = ApiCredentials.ApiToken("tok-123").toString()
        val password = ApiCredentials.EmailPassword("ana@example.com", "hunter2").toString()
        assertFalse(token, "tok-123" in token)
        assertFalse(password, "hunter2" in password || "ana@example.com" in password)
    }

    private fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}
