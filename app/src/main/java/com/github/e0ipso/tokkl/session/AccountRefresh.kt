package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.time.DurationFormat
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** What runs after each refresh whose `GET /api/v9/me` answered. */
fun interface AccountRefreshSteps {

    /**
     * Runs on the refresh's thread, inside its job, with its client, its `/me` answer and its
     * `GET /api/v9/me/preferences`, null when that request failed.
     */
    fun afterAnswer(api: TogglApiClient, me: Me, preferences: MePreferences?)
}

/**
 * Reads the account settings again, so a change made in the web app reaches the phone, and keeps
 * them in [settings]. Each refresh runs [steps] with the same answers, so one `/me` and one
 * `/me/preferences` serve the capabilities, the cache and the preferences of every feature.
 *
 * @param client the API client of the signed-in account, or null when signed out. Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a refresh.
 * @param steps run on [io] after each refresh whose `GET /api/v9/me` answered, inside the same job,
 *   so a [refresh] call while they run joins them. They do not run when sign-in stored newer
 *   settings while the requests ran.
 */
class AccountRefresh(
    private val client: ApiClientProvider,
    private val settings: AccountSettingsSource,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val steps: AccountRefreshSteps,
) {

    /** Guards [answers] and [refreshing]. */
    private val lock = Any()

    /** Counts the refreshes whose `GET /api/v9/me` answered, so [refreshOnReconnect] can tell. */
    private var answers = 0L

    private var refreshing: Job? = null

    /**
     * Reads `GET /api/v9/me` for the signed-in account, with its related data for the cache, and
     * `GET /api/v9/me/preferences` for the duration and clock formats, and keeps the settings.
     * Does nothing when signed out. A failed `/me` or write keeps what is stored, failed
     * preferences keep the stored formats, and a `/me` without `default_workspace_id` keeps the
     * stored workspace. While one refresh runs, another call returns that one instead of sending
     * a second request.
     */
    fun refresh(): Job {
        val job = synchronized(lock) {
            refreshing?.takeUnless { it.isCompleted }
                ?: scope.launch(io, start = CoroutineStart.LAZY) { refreshNow() }.also { refreshing = it }
        }
        job.start()
        return job
    }

    /**
     * A [refresh] that reads the server after a change the caller has just sent, such as a new
     * project, so the cache ends up with the server's state after it. A refresh that is running
     * may have read the server before the change, so this waits for it and then starts a new one,
     * or joins one that another call started meanwhile. Returns the job that ends with that
     * refresh.
     */
    fun refreshAfterChange(): Job = scope.launch(io) {
        synchronized(lock) { refreshing }?.join()
        refresh().join()
    }

    /**
     * A [refresh] for the network's return. A refresh that is running may have sent its request
     * while the phone was offline, so when it ends without the server answering its `/me` after
     * this call, one more refresh starts. Only one runs at a time. Returns the job that ends with
     * the last of them.
     */
    fun refreshOnReconnect(): Job {
        val (running, answersBefore) = synchronized(lock) { refreshing?.takeUnless { it.isCompleted } to answers }
        running ?: return refresh()
        return scope.launch(io) {
            running.join()
            if (synchronized(lock) { answers } == answersBefore) refresh().join()
        }
    }

    /**
     * Shows no settings, and waits for a running refresh to end. That refresh drops its answer and
     * skips [steps] unless it had already stored the answer. Signing out calls this once [client]
     * returns null, so no later refresh stores anything, and then deletes the stored settings.
     */
    suspend fun forget() {
        settings.forget()
        synchronized(lock) { refreshing }?.join()
    }

    private fun refreshNow() {
        val writesBefore = settings.writeCount()
        val api = client() ?: return
        val url = api.server.api("me").newBuilder().addQueryParameter("with_related_data", "true").build()
        val me = (api.get(url, Me.serializer()) as? ApiResult.Success)?.value ?: return
        synchronized(lock) { answers++ }
        val fromMe = me.accountSettings() ?: return
        val preferences = readPreferences(api)
        val kept = settings.storeRefreshed(writesBefore) { stored ->
            fromMe.copy(
                durationFormat = preferences?.durationFormat ?: stored?.durationFormat ?: DurationFormat.IMPROVED,
                twelveHour = preferences?.twelveHour ?: stored?.twelveHour ?: false,
                collapseSimilar = preferences?.collapseSimilar ?: stored?.collapseSimilar ?: true,
                defaultWorkspaceId = fromMe.defaultWorkspaceId ?: stored?.defaultWorkspaceId,
            )
        }
        // Sign-in stored newer settings while the requests ran.
        if (!kept) return
        steps.afterAnswer(api, me, preferences)
    }

    /** The account's preferences, or null when the request fails. */
    private fun readPreferences(api: TogglApiClient): MePreferences? {
        val answer = api.get(api.server.api("me", "preferences"), JsonObject.serializer())
        return (answer as? ApiResult.Success)?.value?.let(::MePreferences)
    }
}
