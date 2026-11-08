package com.github.e0ipso.tokkl.api

import okhttp3.Call
import java.io.InterruptedIOException

/** Cancels just this operation's calls, including a call registered after cancellation. */
class ApiCallCancellation {
    private var cancelled = false
    private val calls = mutableSetOf<Call>()

    // A JVM lock, not a Mutex: OkHttp's threads and the cancelling coroutine call these without
    // suspending.
    @Synchronized
    internal fun attach(call: Call) {
        if (cancelled) call.cancel() else calls.add(call)
    }

    @Synchronized
    internal fun detach(call: Call) {
        calls.remove(call)
    }

    @Synchronized
    fun cancel() {
        cancelled = true
        calls.forEach { it.cancel() }
        calls.clear()
    }

    @Synchronized
    fun check() {
        if (cancelled) throw InterruptedIOException("Export cancelled")
    }
}
