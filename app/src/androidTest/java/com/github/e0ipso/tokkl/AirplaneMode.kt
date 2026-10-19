package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.serialization.json.JsonObject

/**
 * Puts the phone in airplane mode, or takes it out, through the shell user, the only user who may
 * switch it, and waits until [client]'s requests to its server fail for want of a network, or get
 * through again. `cmd connectivity airplane-mode` needs Android 10 or later.
 */
object AirplaneMode {

    fun set(on: Boolean, client: TogglApiClient) {
        DeviceShell.run("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (serverAnswers(client) == on) {
            if (System.nanoTime() > deadline) throw AssertionError("the network did not ${if (on) "go" else "come back"}")
            Thread.sleep(POLL_MS)
        }
    }

    /** Takes the phone out of airplane mode without waiting, for a test's clean-up. */
    fun off() {
        DeviceShell.run("cmd connectivity airplane-mode disable")
    }

    private fun serverAnswers(client: TogglApiClient): Boolean {
        val result = client.get(client.server.api("me"), JsonObject.serializer())
        return (result as? ApiResult.Failure)?.error !is ApiError.Network
    }

    private const val TIMEOUT_NANOS = 30_000_000_000L
    private const val POLL_MS = 200L
}
