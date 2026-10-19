package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.session.MePreferences
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The Timer page's view as the phone keeps it, and whether the server still has to get it. */
data class StoredTimerView(val view: TimerView, val unsent: Boolean)

/**
 * The Timer page's List or Calendar choice, which Tokkl keeps in the account's `timerView`
 * preference, as the web app's TimerPage.tsx does, so a choice made on one device opens there on
 * the others. The phone keeps the last choice too, so a cold start opens it before the refresh
 * answers, and offline.
 *
 * A tap shows the new view at once and posts it to `POST /api/v9/me/preferences` without waiting
 * for the answer. When that fails, as offline, the choice stays on the phone, marked unsent, and
 * goes to the server with the next refresh, so the choice that reaches the server last wins, as
 * for time entries (docs/PRD.md O3). Each refresh otherwise opens the server's `timerView`, List
 * when the key is missing or holds anything else, except the one refresh right after a tap, whose
 * preferences may predate it. A server without Tokkl's preferences, such as Toggl Track, gets
 * nothing: the choice stays on the phone.
 *
 * @param initial the stored view, or null when none is stored.
 * @param save writes the view, throwing [IOException] when it cannot.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param keepsTimerView true when the server keeps Tokkl's preferences.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 */
class TimerViewSetting(
    initial: StoredTimerView?,
    private val save: (StoredTimerView) -> Unit,
    private val client: () -> TogglApiClient?,
    private val keepsTimerView: () -> Boolean,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    private val mutableView = MutableStateFlow(initial?.view ?: TimerView.LIST)

    /** The view the Timer page shows. */
    val view: StateFlow<TimerView> = mutableView.asStateFlow()

    /** Guards [unsent], [chosenSinceRefresh] and each change of [view] with them. */
    private val lock = Any()

    /** The server has not received the view on show. */
    private var unsent = initial?.unsent ?: false

    /** A tap came after the last refresh, whose preferences may predate it. */
    private var chosenSinceRefresh = false

    /** Writes go one at a time, so the last choice reaches the server last. */
    private val sending = Mutex()

    /** Orders the writes to disk, so the last one holds the latest view. */
    private val storing = Any()

    /**
     * Shows [view] at once, keeps it on the phone and sends it to the server in the background.
     * Call it on the main thread.
     */
    fun choose(view: TimerView) {
        synchronized(lock) {
            mutableView.value = view
            unsent = true
            chosenSinceRefresh = true
        }
        scope.launch(io) {
            store()
            if (keepsTimerView()) send()
        }
    }

    /**
     * Takes the refresh's `GET /api/v9/me/preferences`, null when it failed, from a server that
     * keeps Tokkl's preferences when [keepsTimerView]. Sends an unsent choice instead of reading
     * the server's. Blocks on disk I/O, so call it off the main thread.
     */
    fun refreshed(preferences: MePreferences?, keepsTimerView: Boolean) {
        val sendNow = synchronized(lock) {
            val tapped = chosenSinceRefresh
            chosenSinceRefresh = false
            when {
                // The choice stays on the phone.
                !keepsTimerView -> {
                    unsent = false
                    false
                }
                unsent -> true
                tapped || preferences == null -> false
                else -> {
                    mutableView.value = preferences.timerView()
                    false
                }
            }
        }
        store()
        if (sendNow) scope.launch(io) { send() }
    }

    /** Posts the view on show while it is unsent. A failure leaves it unsent for the next refresh. */
    private suspend fun send() = sending.withLock {
        val view = synchronized(lock) { mutableView.value.takeIf { unsent } } ?: return@withLock
        val api = client() ?: return@withLock
        val body = buildJsonObject { put(PREFERENCE, view.preferenceValue) }
        val answer = api.send("POST", api.server.api("me", "preferences"), body, JsonObject.serializer())
        if (answer !is ApiResult.Success) return@withLock
        synchronized(lock) {
            // A tap while the request ran has its own write queued.
            if (mutableView.value == view) unsent = false
        }
        store()
    }

    private fun store() {
        synchronized(storing) {
            val stored = synchronized(lock) { StoredTimerView(mutableView.value, unsent) }
            try {
                save(stored)
            } catch (e: IOException) {
                // The next change writes it again.
            }
        }
    }

    internal companion object {
        /** Tokkl's preference key, which ../tokkl/packages/api/src/models/users.ts accepts as list or calendar. */
        const val PREFERENCE = "timerView"
    }
}

/**
 * The preferences' `timerView`: Calendar for `calendar`, and List for `list`, anything else or
 * nothing, as TimerPage.tsx reads it.
 */
internal fun MePreferences.timerView(): TimerView {
    val value = (body[TimerViewSetting.PREFERENCE] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return TimerView.entries.firstOrNull { it.preferenceValue == value } ?: TimerView.LIST
}
