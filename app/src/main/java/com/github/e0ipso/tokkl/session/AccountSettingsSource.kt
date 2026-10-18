package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The account settings for the whole process, and the one place screens read them from.
 * Sign-in stores the settings from its own `GET /api/v9/me`, and [refresh] reads them again, so
 * a change made in the web app reaches the phone. They persist, so screens have them offline and
 * after a restart. Screens take their zone, clock and week start from [AccountSettings.time],
 * never from the device.
 *
 * @param initial the stored settings, or null when none are stored.
 * @param save writes the settings, throwing [IOException] when it cannot.
 * @param client the API client of the signed-in account, or null when signed out. Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a refresh.
 */
class AccountSettingsSource(
    initial: AccountSettings?,
    private val save: (AccountSettings) -> Unit,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    private val mutableState = MutableStateFlow(initial)

    /** The stored settings, or null when none are stored yet. */
    val state: StateFlow<AccountSettings?> = mutableState.asStateFlow()

    /** Guards [writes], [refreshing], and each write together with the state it sets. */
    private val lock = Any()

    /** Counts the writes, so a refresh drops an answer that a newer write has replaced. */
    private var writes = 0L

    private var refreshing: Job? = null

    /**
     * Writes [settings] and shows them in [state]. Sign-in calls this with the settings from its
     * own `GET /api/v9/me`. Blocks on disk I/O, so call it off the main thread.
     */
    @Throws(IOException::class)
    fun store(settings: AccountSettings) {
        synchronized(lock) { write(settings) }
    }

    /**
     * Reads `GET /api/v9/me` for the signed-in account and keeps its settings. Does nothing when
     * signed out. A failed request or write keeps what is stored. While one refresh runs, another
     * call returns that one instead of sending a second request.
     */
    fun refresh(): Job {
        val job = synchronized(lock) {
            refreshing?.takeUnless { it.isCompleted }
                ?: scope.launch(io, start = CoroutineStart.LAZY) { refreshNow() }.also { refreshing = it }
        }
        job.start()
        return job
    }

    private fun refreshNow() {
        val writesBefore = synchronized(lock) { writes }
        val api = client() ?: return
        val me = api.get(api.server.api("me"), Me.serializer())
        val settings = (me as? ApiResult.Success)?.value?.accountSettings() ?: return
        synchronized(lock) {
            // Sign-in stored newer settings while the request ran.
            if (writes != writesBefore || settings == mutableState.value) return
            try {
                write(settings)
            } catch (e: IOException) {
                // Keeps the stored settings until the next refresh.
            }
        }
    }

    /** Only while holding [lock]. */
    private fun write(settings: AccountSettings) {
        save(settings)
        writes++
        mutableState.value = settings
    }
}
