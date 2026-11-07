package com.github.e0ipso.tokkl.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SharedHttpClient] checks certificates with [PinningTrustManager] and OkHttp's own host name
 * check. The app module's NoTrustAllTest checks that no other class decides trust.
 */
class SharedClientTrustTest {

    private val testApi = TestApi(null)

    @Test
    fun sharedClientUsesThePinningTrustManagerAndOkHttpsHostNameCheck() {
        val client = testApi.http.instance

        assertTrue(client.x509TrustManager is PinningTrustManager)
        assertEquals("okhttp3.internal.tls.OkHostnameVerifier", client.hostnameVerifier.javaClass.name)
    }
}
