package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.platform.SingleFlight
import com.github.e0ipso.tokkl.time.DurationFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * The server reads behind an account refresh, for the account signed in when the refresh opened
 * them: `GET /api/v9/me` with its related data and `GET /api/v9/me/preferences`. [AccountRefresh]
 * orders them. The steps after each refresh read the rest of the account with the same [api].
 */
class ServerAccountReads(val api: TokklApi) {
    suspend fun meWithRelatedData(): ApiResult<Me> = api.account.meWithRelatedData()

    suspend fun preferences(): ApiResult<MePreferences> = api.account.preferences()
}

/** What runs after each refresh whose `GET /api/v9/me` answered, with the reads it made. */
fun interface AccountRefreshSteps {

    /**
     * Runs on the refresh's dispatcher, inside its job, with its [reads], its `/me` answer and its
     * `GET /api/v9/me/preferences`, null when that request failed.
     */
    suspend fun afterAnswer(reads: ServerAccountReads, me: Me, preferences: MePreferences?)
}

/**
 * The account refreshes that a page or a step asks for. [AccountRefresh] reads the server; the
 * unit tests of what asks count the refreshes without one.
 */
interface CacheRefresh {

    /** Reads the account's data from the server again. */
    fun refresh(): Job

    /** Reads it again after a change the server has just accepted, so the cache ends up with that change. */
    fun refreshAfterChange(): Job
}

/**
 * Reads the account settings again, so a change made in the web app reaches the phone, and keeps
 * them in [settings]. Each refresh runs [steps] with the same answers, so one `/me` and one
 * `/me/preferences` serve the capabilities, the cache and the preferences of every feature.
 *
 * @param reads opens the server reads of the signed-in account, or gives null when signed out.
 *   Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a refresh.
 * @param steps run on [io] after each refresh whose `GET /api/v9/me` answered, inside the same job,
 *   so a [refresh] call while they run joins them. They do not run when sign-in stored newer
 *   settings while the requests ran.
 */
class AccountRefresh(
    private val reads: suspend () -> ServerAccountReads?,
    private val settings: AccountSettingsSource,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val steps: AccountRefreshSteps,
) : AccountDataOwner,
    CacheRefresh {

    override val signOutPhase = SignOutPhase.FINISH_REFRESH

    override suspend fun forgetAccount() = forget()

    /**
     * Guards [answers], and [refreshing]'s job read with it. A JVM lock, not a Mutex, because
     * refreshOnReconnect() is called without suspending, and reads the running job and the count
     * together.
     */
    private val lock = Any()

    /** Counts the refreshes whose `GET /api/v9/me` answered, so [refreshOnReconnect] can tell. */
    private var answers = 0L

    private val refreshing = SingleFlight(scope, io)

    /**
     * Reads `GET /api/v9/me` for the signed-in account, with its related data for the cache, and
     * `GET /api/v9/me/preferences` for the duration and clock formats, and keeps the settings.
     * Does nothing when signed out. A failed `/me` or write keeps what is stored, failed
     * preferences keep the stored formats, and a `/me` without `default_workspace_id` keeps the
     * stored workspace. While one refresh runs, another call returns that one instead of sending
     * a second request.
     */
    override fun refresh(): Job = refreshing.launch { refreshNow() }

    /**
     * A [refresh] that reads the server after a change the caller has just sent, such as a new
     * project, so the cache ends up with the server's state after it. A refresh that is running
     * may have read the server before the change, so this waits for it and then starts a new one,
     * or joins one that another call started meanwhile. Returns the job that ends with that
     * refresh.
     */
    override fun refreshAfterChange(): Job = scope.launch(io) {
        refreshing.running()?.join()
        refresh().join()
    }

    /**
     * A [refresh] for the network's return. A refresh that is running may have sent its request
     * while the phone was offline, so when it ends without the server answering its `/me` after
     * this call, one more refresh starts. Only one runs at a time. Returns the job that ends with
     * the last of them.
     */
    fun refreshOnReconnect(): Job {
        val (running, answersBefore) = synchronized(lock) { refreshing.running() to answers }
        running ?: return refresh()
        return scope.launch(io) {
            running.join()
            if (synchronized(lock) { answers } == answersBefore) refresh().join()
        }
    }

    /**
     * Shows no settings, and waits for a running refresh to end. That refresh drops its answer and
     * skips [steps] unless it had already stored the answer. Signing out calls this once [reads]
     * gives null, so no later refresh stores anything, and then deletes the stored settings.
     */
    suspend fun forget() {
        settings.forget()
        refreshing.running()?.join()
    }

    private suspend fun refreshNow() {
        val writesBefore = settings.writeCount()
        val server = reads() ?: return
        val me = (server.meWithRelatedData() as? ApiResult.Success)?.value ?: return
        synchronized(lock) { answers++ }
        val fromMe = me.accountSettings() ?: return
        val preferences = (server.preferences() as? ApiResult.Success)?.value
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
        steps.afterAnswer(server, me, preferences)
    }
}
