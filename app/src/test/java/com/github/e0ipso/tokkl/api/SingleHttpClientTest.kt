package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.ClassReferences
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only [SharedHttpClient] may build an HTTP client, so the host guard and the
 * other security settings cover every request. The test reads the constant
 * pool of every compiled app class through [ClassReferences].
 */
class SingleHttpClientTest {

    private val classes: Map<String, Set<String>> by lazy { ClassReferences.app.mapValues { it.value.names } }

    @Test
    fun noAppClassButSharedHttpClientBuildsAnHttpClient() {
        assertTrue("the scan found the app's classes", "com/github/e0ipso/tokkl/MainActivity" in classes)

        val offenders = classes
            .filterKeys { !isSharedHttpClient(it) }
            .flatMap { (name, references) -> references.filter(::isForbidden).map { "$name uses $it" } }

        assertTrue("Use SharedHttpClient.instance instead:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private fun isSharedHttpClient(className: String): Boolean =
        className == SHARED || className.startsWith("$SHARED\$")

    private fun isForbidden(reference: String): Boolean =
        FORBIDDEN.any { prefix ->
            reference == prefix ||
                (prefix.endsWith("/") && reference.startsWith(prefix)) ||
                reference.startsWith("$prefix.")
        }

    private companion object {
        const val SHARED = "com/github/e0ipso/tokkl/api/SharedHttpClient"

        /** Ways to build an HTTP client or open a connection without the shared client. */
        val FORBIDDEN = listOf(
            "okhttp3/OkHttpClient\$Builder",
            "okhttp3/OkHttpClient.<init>",
            "okhttp3/OkHttpClient.newBuilder",
            "java/net/URL.openConnection",
            "java/net/URL.openStream",
            "java/net/URLConnection",
            "java/net/HttpURLConnection",
            "javax/net/ssl/HttpsURLConnection",
            "java/net/http/",
            "java/net/Socket.<init>",
            "javax/net/SocketFactory.createSocket",
            "javax/net/ssl/SSLSocketFactory.createSocket",
            "android/webkit/WebView",
            "android/app/DownloadManager",
        )
    }
}
