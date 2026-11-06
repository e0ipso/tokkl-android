package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.ClassReferences
import java.io.File
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
    fun noCompiledAppClassButThePinningTrustManagerIsATrustManagerOrHostnameVerifier() {
        val classes = appClasses()
        assertTrue("the scan found the app's classes", classes.any { it.name == "com.github.e0ipso.tokkl.MainActivity" })

        val trustManagers = classes.filter { TrustManager::class.java.isAssignableFrom(it) }.map { it.name }
        val verifiers = classes.filter { HostnameVerifier::class.java.isAssignableFrom(it) }.map { it.name }

        assertEquals(listOf(PINNING_TRUST_MANAGER), trustManagers)
        assertEquals(emptyList<String>(), verifiers)
    }

    /**
     * Kotlin compiles a lambda for a Java interface without a class of its
     * own, so the sources are read too: every source set and the scripts.
     */
    @Test
    fun noSourceOrScriptBuildsAnotherTrustPath() {
        val root = rootDirectory()
        val sources = root.walkTopDown()
            .onEnter { it.name !in SKIPPED_DIRECTORIES }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") && it.name !in TRUST_CHECKS }
            .filter { "src" in it.relativeTo(root).invariantSeparatorsPath.split('/') }
            .toList()
        val scripts = File(root, "scripts").walkTopDown().filter { it.isFile }.toList()
        assertTrue("the scan found the sources", sources.any { it.name == "SharedHttpClient.kt" })
        assertTrue("the scan found the scripts", scripts.any { it.name == "instrumented-tests.sh" })

        val offenders = sources.flatMap { file ->
            val text = file.readText()
            SOURCE_RULES.filter { (pattern, allowedIn) -> pattern.containsMatchIn(text) && file.name !in allowedIn }
                .map { (pattern, _) -> "${file.relativeTo(root)} matches ${pattern.pattern}" }
        } + scripts.flatMap { file ->
            // A JSON fixture keeps a whole script on one file line, with `\n` between commands.
            // Expand those before matching, so `journalctl -k` is not read as `curl -k`.
            val text = file.readText().replace("\\n", "\n")
            SCRIPT_RULES.filter { it.containsMatchIn(text) }.map { "${file.relativeTo(root)} matches ${it.pattern}" }
        }

        assertTrue("Only PinningTrustManager may decide trust:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private fun appClasses(): List<Class<*>> =
        ClassReferences.app.keys.map { Class.forName(it.replace('/', '.'), false, javaClass.classLoader) }

    /** The checkout's root: the parent of the app module, which Gradle makes the working directory of its unit tests. */
    private fun rootDirectory(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    private companion object {
        const val PINNING_TRUST_MANAGER = "com.github.e0ipso.tokkl.api.PinningTrustManager"

        /** This test and core:api's check of the shared client, which name the patterns they look for. */
        val TRUST_CHECKS = setOf("NoTrustAllTest.kt", "SharedClientTrustTest.kt")

        /** Build output and tools, which hold no source of the app. */
        val SKIPPED_DIRECTORIES = setOf("build", ".git", ".gradle", ".kotlin", "node_modules")

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
