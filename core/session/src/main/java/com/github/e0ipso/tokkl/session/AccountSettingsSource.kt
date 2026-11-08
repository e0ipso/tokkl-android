package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * The account settings for the whole process, and the one place screens read them from.
 * Sign-in stores the settings from its own `GET /api/v9/me`, and [AccountRefresh] reads them
 * again, so a change made in the web app reaches the phone. They persist, so screens have them
 * offline and after a restart. Screens take their zone, clock and week start from
 * [AccountSettings.time], never from the device, write durations with
 * [AccountSettings.formatDuration] and clock times with [AccountSettings.formatTime]. The timer
 * and manual entry take the workspace of a new entry from [AccountSettings.defaultWorkspaceId].
 *
 * @param disk keeps the settings on the phone. The first read loads them, on the reading thread.
 */
class AccountSettingsSource(private val disk: ValueStore<AccountSettings>) : DefaultWorkspace {

    private val mutableState by lazy { MutableStateFlow(disk.load()) }

    /** The stored settings, or null when none are stored yet. */
    val state: StateFlow<AccountSettings?> by lazy { mutableState.asStateFlow() }

    override fun defaultWorkspaceId(): Long? = state.value?.defaultWorkspaceId

    /**
     * Guards [writes], and each write together with the state it sets. A JVM lock, not a Mutex,
     * because the refresh on the IO dispatcher and the main thread write without suspending.
     */
    private val lock = Any()

    /** Counts the writes, so a refresh drops an answer that a newer write has replaced. */
    private var writes = 0L

    /**
     * Writes [settings] and shows them in [state]. Sign-in calls this with the settings from its
     * own `GET /api/v9/me`. Blocks on disk I/O, so call it off the main thread.
     */
    @Throws(IOException::class)
    fun store(settings: AccountSettings) {
        synchronized(lock) { write(settings) }
    }

    /** How many writes and forgets have happened, for [storeRefreshed]. */
    internal fun writeCount(): Long = synchronized(lock) { writes }

    /**
     * Writes what [merge] makes of the stored settings, unless a write or [forget] came after
     * [writesBefore], as when sign-in stored newer settings while a refresh ran. Settings equal to
     * the stored ones are not written again, and a failed write keeps the stored ones until the
     * next refresh. Returns false when a newer write came.
     */
    internal fun storeRefreshed(writesBefore: Long, merge: (stored: AccountSettings?) -> AccountSettings): Boolean =
        synchronized(lock) {
            if (writes != writesBefore) return false
            val settings = merge(mutableState.value)
            if (settings != mutableState.value) {
                try {
                    write(settings)
                } catch (_: IOException) {
                    // Keeps the stored settings until the next refresh.
                }
            }
            true
        }

    /**
     * Shows no settings, so a refresh that is running drops its answer. Signing out calls this
     * through [AccountRefresh.forget], and then deletes the stored settings.
     */
    internal fun forget() {
        synchronized(lock) {
            writes++
            mutableState.value = null
        }
    }

    /** Only while holding [lock]. */
    private fun write(settings: AccountSettings) {
        disk.save(settings)
        writes++
        mutableState.value = settings
    }
}
