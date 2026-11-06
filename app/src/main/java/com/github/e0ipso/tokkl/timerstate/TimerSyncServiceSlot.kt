package com.github.e0ipso.tokkl.timerstate

import android.os.Handler
import android.os.Looper

/** [TimerSyncService] while it runs in the foreground. The service sets and clears it. */
class TimerSyncServiceSlot {

    @Volatile
    internal var service: TimerSyncService? = null

    /** Stops [TimerSyncService] unless a newer start is on its way, which then decides for itself. */
    fun stop() {
        Handler(Looper.getMainLooper()).post { service?.stopLatest() }
    }
}
