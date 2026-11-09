package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.SnappedSpan
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * The times the server stores for the ones the phone sends. Tokkl puts them on the account's time
 * resolution grid. Toggl Track keeps them as they are, which the defaults do.
 */
interface ServerSpans {

    /** Waits until the spans know the grid they snap to, as once the account settings have loaded. */
    suspend fun ready() {}

    /** Where the server puts [start] once the entry is stopped. Finds the entry a lost create made. */
    fun start(start: Instant): Instant = start

    /**
     * The span the server stores for a running entry from [start] that a new timer stopped at
     * [stop]. Tokkl puts both ends on the grid, the stop where the new timer's start snaps
     * ([AccountTime.snapStop] without one step). A queued start ended before a later timer goes
     * this way, so it does not overlap that timer.
     */
    fun switched(start: Instant, stop: Instant): SnappedSpan = SnappedSpan(start, stop)

    /** The legacy Stop prediction, kept for older queued Stops and a short Stop's snapped zero point. */
    fun stopped(start: Instant, at: Instant, oneStep: Boolean): SnappedSpan = SnappedSpan(start, maxOf(at, start))

    /** The span the server stores for a Stop at [at], or null when it moves the entry to the Trash. */
    fun capturedStop(start: Instant, at: Instant): SnappedSpan? = SnappedSpan(start, maxOf(at, start))

    /** Times as the phone sends them. */
    object Unsnapped : ServerSpans
}

/**
 * [ServerSpans] on the grid of the signed-in account's [settings], or unsnapped while there are
 * none, once [load] has read them from the disk.
 */
internal class AccountServerSpans(private val settings: StateFlow<AccountSettings?>, private val load: StartupLoad) :
    ServerSpans {

    override suspend fun ready() = load.ready()

    private fun time(): AccountTime? = settings.value?.time()

    override fun start(start: Instant): Instant = time()?.snapInstant(start) ?: start

    override fun switched(start: Instant, stop: Instant): SnappedSpan =
        time()?.snapStop(start, stop, oneStep = false) ?: super.switched(start, stop)

    override fun stopped(start: Instant, at: Instant, oneStep: Boolean): SnappedSpan =
        time()?.snapStop(start, at, oneStep) ?: super.stopped(start, at, oneStep)

    override fun capturedStop(start: Instant, at: Instant): SnappedSpan? {
        val time = time() ?: return super.capturedStop(start, at)
        return time.snapCapturedStop(start, at)
    }
}
