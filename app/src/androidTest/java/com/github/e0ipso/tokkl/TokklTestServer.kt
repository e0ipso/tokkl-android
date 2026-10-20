package com.github.e0ipso.tokkl

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.api.ServerUrl
import java.util.Properties

/**
 * The real Tokkl server that scripts/instrumented-tests.sh starts for the
 * instrumented tests, and the account it creates there. The script passes the
 * URL and the email as instrumentation arguments. The password and the API
 * token are in a file on the device, which only the shell user can read; as
 * instrumentation arguments they would show in the arguments of the adb
 * process that starts the tests. No instrumented test replaces Tokkl with a
 * mock or a fake server.
 */
object TokklTestServer {

    /** The server's base URL as the emulator reaches it, such as `http://10.0.2.2:32768`. */
    val url: ServerUrl by lazy {
        val url = argument("tokklBaseUrl")
        ServerUrl.parse(url) ?: error("tokklBaseUrl is not an http or https URL: $url")
    }

    /**
     * The same server behind a TLS proxy with a self-signed certificate that
     * the script makes for each run, such as `https://10.0.2.2:32769`. The
     * certificate names 10.0.2.2 as an IP address, so the host name check
     * passes once the certificate is pinned.
     */
    val tlsUrl: ServerUrl by lazy {
        val url = argument("tokklTlsBaseUrl")
        ServerUrl.parse(url) ?: error("tokklTlsBaseUrl is not an https URL: $url")
    }

    /**
     * The same server under the path prefix `/tokkl`, as a reverse proxy
     * serves it beside other sites, such as `http://10.0.2.2:32770/tokkl`. The
     * proxy strips the prefix and answers 404 to every path outside it, so a
     * request that drops the prefix fails.
     */
    val prefixedUrl: ServerUrl by lazy {
        val url = argument("tokklPrefixedBaseUrl")
        ServerUrl.parse(url) ?: error("tokklPrefixedBaseUrl is not an http or https URL: $url")
    }

    /** The proxy certificate's SHA-256 fingerprint, as `openssl x509 -noout -fingerprint -sha256` prints it. */
    val tlsFingerprint: String by lazy { argument("tokklTlsFingerprint") }

    /**
     * The same server behind the same proxy, on a port that serves a second certificate, such as
     * `https://10.0.2.2:32771`. The script makes it for each run with the names and subject of
     * the first and a key of its own, as after the server's key was regenerated.
     */
    val newKeyTlsUrl: ServerUrl by lazy {
        val url = argument("tokklNewKeyTlsBaseUrl")
        ServerUrl.parse(url) ?: error("tokklNewKeyTlsBaseUrl is not an https URL: $url")
    }

    /** The second certificate's SHA-256 fingerprint, in the form of [tlsFingerprint]. */
    val newKeyTlsFingerprint: String by lazy { argument("tokklNewKeyTlsFingerprint") }

    val email: String by lazy { argument("tokklEmail") }

    val password: String by lazy { credential("tokklPassword") }

    val apiToken: String by lazy { credential("tokklApiToken") }

    private val credentials: Properties by lazy {
        val path = argument("tokklCredentialsFile")
        Properties().apply { load(DeviceShell.run("cat $path").reader()) }
    }

    private fun credential(name: String): String =
        credentials.getProperty(name)
            ?: error(
                "$name is missing from the file in tokklCredentialsFile. " +
                    "Run the instrumented tests with scripts/instrumented-tests.sh, which writes it.",
            )

    private fun argument(name: String): String =
        InstrumentationRegistry.getArguments().getString(name)
            ?: error(
                "The instrumentation argument $name is missing. " +
                    "Run the instrumented tests with scripts/instrumented-tests.sh, which starts the server.",
            )
}
