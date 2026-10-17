package com.github.e0ipso.tokkl

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.api.ServerUrl

/**
 * The real Tokkl server that scripts/instrumented-tests.sh starts for the
 * instrumented tests, and the account it creates there. The script passes
 * both as instrumentation arguments. No instrumented test replaces Tokkl
 * with a mock or a fake server.
 */
object TokklTestServer {

    /** The server's base URL as the emulator reaches it, such as `http://10.0.2.2:32768`. */
    val url: ServerUrl by lazy {
        val url = argument("tokklBaseUrl")
        ServerUrl.parse(url) ?: error("tokklBaseUrl is not an http or https URL: $url")
    }

    val email: String by lazy { argument("tokklEmail") }

    val password: String by lazy { argument("tokklPassword") }

    val apiToken: String by lazy { argument("tokklApiToken") }

    private fun argument(name: String): String =
        InstrumentationRegistry.getArguments().getString(name)
            ?: error(
                "The instrumentation argument $name is missing. " +
                    "Run the instrumented tests with scripts/instrumented-tests.sh, which starts the server.",
            )
}
