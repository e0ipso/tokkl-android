package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.Semaphore

/**
 * The server's capabilities for the whole process, and the one place features read them from.
 * They refresh with each `GET /api/v9/me` of the account settings refresh, and persist. The first
 * read loads them from [store], a file of a few flags, on the reading thread.
 */
class CapabilitiesSource(private val store: ValueStore<Capabilities>) {

    private val loaded by lazy { Loaded(store.load()) }

    /**
     * Lets one change of the flags run at a time, [refresh] included while its probes wait for the
     * server, so a change asked for meanwhile waits for the refresh and lands after it. Unlike a
     * monitor, a refresh may release it on another thread than the one that took it.
     */
    private val changing = Semaphore(1)

    private class Loaded(initial: Capabilities?) {
        val state = MutableStateFlow(initial ?: Capabilities.NONE)

        /** What the disk holds, so a refresh after a failed write writes again. */
        var stored: Capabilities? = initial
    }

    /** The latest capabilities: the stored ones after a restart, [Capabilities.NONE] before any refresh. */
    val state: StateFlow<Capabilities> by lazy { loaded.state.asStateFlow() }

    /** Whether the server keeps Tokkl's preferences, which only Tokkl offers, as it does favorites. */
    fun keepsPreferences(): Boolean = state.value.favorites

    /** Whether the server offers Tokkl's timer API ([Capabilities.timerState]). */
    fun offersTimerApi(): Boolean = state.value.timerState

    /** Whether the server stores no-time entries ([Capabilities.allDay]). */
    fun offersAllDay(): Boolean = state.value.allDay == true

    /** Whether the server restores deleted entries ([Capabilities.restore]). */
    fun offersRestore(): Boolean = state.value.restore

    /**
     * [offersAllDay] as it changes. The first collection loads the capabilities, on the
     * collecting thread.
     */
    fun allDayOffers(): Flow<Boolean> = flow { emitAll(state) }.map { it.allDay == true }.distinctUntilChanged()

    /**
     * Sets the flags from [me], the refresh's `GET /api/v9/me`, one `GET /api/app/trash` and one
     * `GET /api/app/timer`, and stores them. A timer state the probe read goes to [timerState], so
     * the refresh sends no second request for it. A failed write keeps the new flags for this
     * process. Blocks the thread while another change runs, and writes the disk, so call it on the
     * IO dispatcher. Returns the new flags.
     */
    suspend fun <R> refresh(api: TogglApiClient, me: Me, timerState: ProbedTimerState<R>): Capabilities {
        changing.acquire()
        try {
            val trash = probeTrash(api)
            val read = timerState.begin()
            val timer = probeTimerState(api)
            val flags = update(loaded.state.value.updatedWith(me, trash, timer))
            if (timer is TimerStateProbe.Present) timerState.accept(read, timer.state)
            return flags
        } finally {
            changing.release()
        }
    }

    /** A CSV request got definite endpoint absence. It affects only this export kind. */
    fun csvMissing(kind: ReportCsvKind) = change {
        val current = loaded.state.value
        if (!current.supports(kind)) return@change
        update(
            when (kind) {
                ReportCsvKind.SUMMARY -> current.copy(summaryCsv = false)
                ReportCsvKind.DETAILED -> current.copy(detailedCsv = false)
            },
        )
    }

    /**
     * A trash call outside the probe got the probe's answer for a server without the trash, a 404
     * or a body that is not a list. Turns the trash and restore flags off and stores them, as the
     * probe would. Flags that already say so, as after a sign-out, stay as they are and unwritten.
     * Writes to the disk, so call it off the main thread.
     */
    fun trashMissing() = change {
        val current = loaded.state.value
        if (current.trash || current.restore) update(current.copy(trash = false, restore = false))
    }

    /**
     * A Tokkl time entry list lacked the `all_day` key, as Tokkl before 1.11.0 sends it. Turns
     * no-time entries off for good and stores that. A flag that is not on, false or not known yet
     * as after a sign-out, stays as it is and unwritten. Writes to the disk, so call it off the
     * main thread.
     */
    fun allDayMissing() = change {
        val current = loaded.state.value
        if (current.allDay == true) update(current.copy(allDay = false))
    }

    private fun update(next: Capabilities): Capabilities {
        if (next != loaded.stored) {
            try {
                store.save(next)
                loaded.stored = next
            } catch (_: IOException) {
                // The next refresh writes them again.
            }
        }
        loaded.state.value = next
        return next
    }

    /**
     * Goes back to [Capabilities.NONE], after a refresh that is running. Signing out calls this,
     * then deletes the stored flags.
     */
    fun forget() = change {
        loaded.state.value = Capabilities.NONE
        loaded.stored = null
    }

    /** Runs [block] once no other change of the flags runs, waiting for it on this thread. */
    private fun change(block: () -> Unit) {
        changing.acquire()
        try {
            block()
        } finally {
            changing.release()
        }
    }
}

/**
 * Takes the timer state that [CapabilitiesSource.refresh] reads with its probe, ordered against the
 * states the timer's own requests and events read.
 */
interface ProbedTimerState<R> {

    /** Marks a read that starts now, before its request goes out. */
    fun begin(): R

    /** Takes the [state] that [read] got. Returns false when a newer state is already held. */
    fun accept(read: R, state: TimerState): Boolean
}
