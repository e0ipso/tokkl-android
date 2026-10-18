package com.github.e0ipso.tokkl.api

import java.io.File
import java.util.zip.ZipFile
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.TrustManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No code path accepts a certificate without a check (docs/PRD.md S3): no
 * trust-all trust manager and no host name verifier anywhere, in the app,
 * its debug build, its tests or the scripts. The only trust manager is
 * [PinningTrustManager], and only [SharedHttpClient] installs it.
 * [PinningTrustManager] may call OkHttp's own host name check, to refuse a
 * certificate that check would refuse later anyway.
 */
class NoTrustAllTest {

    @Test
    fun sharedClientUsesThePinningTrustManagerAndOkHttpsHostNameCheck() {
        val client = SharedHttpClient.instance

        assertTrue(client.x509TrustManager is PinningTrustManager)
        assertEquals("okhttp3.internal.tls.OkHostnameVerifier", client.hostnameVerifier.javaClass.name)
    }

    @Test
    fun noCompiledAppClassButThePinningTrustManagerIsATrustManagerOrHostnameVerifier() {
        val classes = appClasses()
        assertTrue("the scan found the app's classes", classes.any { it.name == "com.github.e0ipso.tokkl.MainActivity" })

        val trustManagers = classes.filter { TrustManager::class.java.isAssignableFrom(it) }.map { it.name }
        val verifiers = classes.filter { HostnameVerifier::class.java.isAssignableFrom(it) }.map { it.name }

        assertEquals(listOf(PinningTrustManager::class.java.name), trustManagers)
        assertEquals(emptyList<String>(), verifiers)
    }

    /**
     * Kotlin compiles a lambda for a Java interface without a class of its
     * own, so the sources are read too: every source set and the scripts.
     */
    @Test
    fun noSourceOrScriptBuildsAnotherTrustPath() {
        val module = moduleDirectory()
        val root = checkNotNull(module.parentFile)
        val sources = File(module, "src").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") && it.name != "NoTrustAllTest.kt" }
            .toList()
        val scripts = File(root, "scripts").walkTopDown().filter { it.isFile }.toList()
        assertTrue("the scan found the sources", sources.any { it.name == "SharedHttpClient.kt" })
        assertTrue("the scan found the scripts", scripts.any { it.name == "instrumented-tests.sh" })

        val offenders = sources.flatMap { file ->
            val text = file.readText()
            SOURCE_RULES.filter { (pattern, allowedIn) -> pattern.containsMatchIn(text) && file.name !in allowedIn }
                .map { (pattern, _) -> "${file.relativeTo(root)} matches ${pattern.pattern}" }
        } + scripts.flatMap { file ->
            val text = file.readText()
            SCRIPT_RULES.filter { it.containsMatchIn(text) }.map { "${file.relativeTo(root)} matches ${it.pattern}" }
        }

        assertTrue("Only PinningTrustManager may decide trust:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private fun appClasses(): List<Class<*>> {
        val location = File(SharedHttpClient::class.java.protectionDomain!!.codeSource.location.toURI())
        val names = if (location.isDirectory) {
            location.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .map { it.relativeTo(location).invariantSeparatorsPath }
                .toList()
        } else {
            ZipFile(location).use { jar -> jar.entries().asSequence().map { it.name }.filter { it.endsWith(".class") }.toList() }
        }
        return names
            .filter { it.startsWith(APP_PACKAGE) }
            .map { Class.forName(it.removeSuffix(".class").replace('/', '.'), false, javaClass.classLoader) }
    }

    /** The app module's directory, which Gradle makes the working directory of its unit tests. */
    private fun moduleDirectory(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private companion object {
        const val APP_PACKAGE = "com/github/e0ipso/tokkl/"

        /** A pattern, and the only files where it may appear. */
        val SOURCE_RULES = listOf(
            Regex("""(?<!Ok)HostnameVerifier|hostnameVerifier""") to emptySet(),
            Regex("""OkHostnameVerifier""") to setOf("PinningTrustManager.kt"),
            // Every trust manager implements checkServerTrusted.
            Regex("""(fun|void)\s+checkServerTrusted""") to setOf("PinningTrustManager.kt"),
            Regex("""sslSocketFactory|SSLContext""") to setOf("SharedHttpClient.kt"),
        )

        /** Ways for a script to skip the certificate check. */
        val SCRIPT_RULES = listOf(
            Regex("""--insecure"""),
            Regex("""\bcurl\b[^\n]*\s-k\b"""),
            Regex("""verify=0|ssl-verify\s+none""", RegexOption.IGNORE_CASE),
        )
    }
}
