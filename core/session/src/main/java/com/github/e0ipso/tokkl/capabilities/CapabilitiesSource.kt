package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.storage.LoadedState
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The server's capabilities for the whole process, and the one place features read them from.
 * They refresh with each `GET /api/v9/me` of the account settings refresh, and persist. They load
 * from [store], a file of a few flags, as this is made, in [scope], off the main thread. Code
 * that may run before that load ends reads them through the suspending calls or [loaded].
 */
class CapabilitiesSource(private val store: ValueStore<Capabilities>, scope: CoroutineScope) : StartupLoad {

    /** What the disk holds, so a refresh after a failed write writes again. Guarded by [changing]. */
    private var stored: Capabilities? = null

    private val held = LoadedState(scope, Capabilities.NONE) { store.load()?.also { stored = it } ?: Capabilities.NONE }

    /**
     * Lets one change of the flags run at a time, [refresh] included while its probes wait for the
     * server, so a change asked for meanwhile waits for the refresh and lands after it.
     */
    private val changing = Mutex()

    /** The latest capabilities: the stored ones after a restart, [Capabilities.NONE] before any refresh or load. */
    val state: StateFlow<Capabilities> = held.state

    /** [state] once loaded, for readers that may run before the load ends. */
    val loaded: Flow<Capabilities> = held.loaded

    override val isReady: Boolean get() = held.isReady

    override suspend fun ready() = held.ready()

    /** The capabilities, once loaded. */
    suspend fun current(): Capabilities = held.current()

    /** Whether the server keeps Tokkl's preferences, which only Tokkl offers, as it does favorites. */
    suspend fun keepsPreferences(): Boolean = current().favorites

    /** Whether the server offers Tokkl's timer API ([Capabilities.timerState]). */
    suspend fun offersTimerApi(): Boolean = current().timerState

    /** Whether the server stores no-time entries ([Capabilities.allDay]). */
    suspend fun offersAllDay(): Boolean = current().allDay == true

    /** Whether the server restores deleted entries ([Capabilities.restore]). */
    suspend fun offersRestore(): Boolean = current().restore

    /** [offersAllDay] as it changes, from the loaded capabilities. */
    fun allDayOffers(): Flow<Boolean> = loaded.map { it.allDay == true }.distinctUntilChanged()

    /**
     * Sets the flags from [me], the refresh's `GET /api/v9/me`, one `GET /api/app/trash` and one
     * `GET /api/app/timer`, and stores them. A timer state the probe read goes to [timerState], so
     * the refresh sends no second request for it. A failed write keeps the new flags for this
     * process. Waits while another change runs. Returns the new flags.
     */
    suspend fun <R> refresh(api: TokklApi, me: Me, timerState: ProbedTimerState<R>): Capabilities = change {
        val trash = probeTrash(api)
        val read = timerState.begin()
        val timer = probeTimerState(api)
        val flags = update(held.value.updatedWith(me, trash, timer))
        if (timer is TimerStateProbe.Present) timerState.accept(read, timer.state)
        flags
    }

    /** A CSV request got definite endpoint absence. It affects only this export kind. */
    suspend fun csvMissing(kind: ReportCsvKind) = change<Unit> {
        val current = held.value
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
     */
    suspend fun trashMissing() = change<Unit> {
        val current = held.value
        if (current.trash || current.restore) update(current.copy(trash = false, restore = false))
    }

    /**
     * A Tokkl time entry list lacked the `all_day` key, as Tokkl before 1.11.0 sends it. Turns
     * no-time entries off for good and stores that. A flag that is not on, false or not known yet
     * as after a sign-out, stays as it is and unwritten.
     */
    suspend fun allDayMissing() = change<Unit> {
        val current = held.value
        if (current.allDay == true) update(current.copy(allDay = false))
    }

    /** Only inside [change]. */
    private suspend fun update(next: Capabilities): Capabilities {
        if (next != stored) {
            try {
                store.save(next)
                stored = next
            } catch (_: IOException) {
                // The next refresh writes them again.
            }
        }
        held.value = next
        return next
    }

    /**
     * Goes back to [Capabilities.NONE], after a refresh that is running. Signing out calls this,
     * then deletes the stored flags.
     */
    suspend fun forget() = change<Unit> {
        held.value = Capabilities.NONE
        stored = null
    }

    /** Runs [block] once the flags have loaded and no other change of them runs. */
    private suspend fun <T> change(block: suspend () -> T): T {
        ready()
        return changing.withLock { block() }
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
