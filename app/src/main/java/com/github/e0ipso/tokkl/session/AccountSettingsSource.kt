package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.DurationFormat
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The account settings for the whole process, and the one place screens read them from.
 * Sign-in stores the settings from its own `GET /api/v9/me`, and [refresh] reads them again, so
 * a change made in the web app reaches the phone. They persist, so screens have them offline and
 * after a restart. Screens take their zone, clock and week start from [AccountSettings.time],
 * never from the device, and write durations with [AccountSettings.formatDuration].
 *
 * @param initial the stored settings, or null when none are stored.
 * @param save writes the settings, throwing [IOException] when it cannot.
 * @param client the API client of the signed-in account, or null when signed out. Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a refresh.
 * @param onRefresh runs on [io] after each refresh whose `GET /api/v9/me` answered, inside the
 *   same job, with that client and answer, and the refresh's `GET /api/v9/me/preferences`, null
 *   when that request failed. The capabilities and the cache refresh there, so one `/me` and one
 *   `/me/preferences` serve them all, and a [refresh] call while it runs joins it. It does not
 *   run when sign-in stored newer settings while the requests ran.
 */
class AccountSettingsSource(
    initial: AccountSettings?,
    private val save: (AccountSettings) -> Unit,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val onRefresh: (TogglApiClient, Me, MePreferences?) -> Unit = { _, _, _ -> },
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
     * Reads `GET /api/v9/me` for the signed-in account, with its related data for the cache, and
     * `GET /api/v9/me/preferences` for the duration format, and keeps the settings. Does nothing
     * when signed out. A failed `/me` or write keeps what is stored, and failed preferences keep
     * the stored duration format. While one refresh runs, another call returns that one instead
     * of sending a second request.
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
        val url = api.server.api("me").newBuilder().addQueryParameter("with_related_data", "true").build()
        val me = (api.get(url, Me.serializer()) as? ApiResult.Success)?.value ?: return
        val fromMe = me.accountSettings() ?: return
        val preferences = readPreferences(api)
        synchronized(lock) {
            // Sign-in stored newer settings while the requests ran.
            if (writes != writesBefore) return
            val settings = fromMe.copy(
                durationFormat = preferences?.durationFormat ?: mutableState.value?.durationFormat ?: DurationFormat.IMPROVED,
            )
            if (settings != mutableState.value) {
                try {
                    write(settings)
                } catch (e: IOException) {
                    // Keeps the stored settings until the next refresh.
                }
            }
        }
        onRefresh(api, me, preferences)
    }

    /** The account's preferences, or null when the request fails. */
    private fun readPreferences(api: TogglApiClient): MePreferences? {
        val answer = api.get(api.server.api("me", "preferences"), JsonObject.serializer())
        return (answer as? ApiResult.Success)?.value?.let(::MePreferences)
    }

    /** Only while holding [lock]. */
    private fun write(settings: AccountSettings) {
        save(settings)
        writes++
        mutableState.value = settings
    }
}
