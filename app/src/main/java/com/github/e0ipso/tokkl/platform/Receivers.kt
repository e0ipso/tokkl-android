package com.github.e0ipso.tokkl.platform

import android.content.BroadcastReceiver
import java.util.concurrent.atomic.AtomicBoolean

/** Ends this broadcast the first time the function it returns is called, and never again. */
fun BroadcastReceiver.PendingResult.finishOnce(): () -> Unit {
    val finished = AtomicBoolean(false)
    return { if (finished.compareAndSet(false, true)) finish() }
}
