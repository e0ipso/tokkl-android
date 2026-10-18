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
