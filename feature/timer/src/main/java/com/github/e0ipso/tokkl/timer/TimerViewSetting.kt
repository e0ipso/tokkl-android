package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.TimerViewChange
import com.github.e0ipso.tokkl.preferences.PreferenceCodec
import com.github.e0ipso.tokkl.preferences.StoredPreference
import com.github.e0ipso.tokkl.preferences.SyncedPreference
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.CoroutineContext

/** The Timer page's view as the phone keeps it, and whether the server still has to get it. */
typealias StoredTimerView = StoredPreference<TimerView>

/**
 * The Timer page's List or Calendar choice, which Tokkl keeps in the account's `timerView`
 * preference, as the web app's TimerPage.tsx does, so a choice made on one device opens there on
 * the others. The phone keeps the last choice too, as [SyncedPreference] describes.
 *
 * A tap shows the new view at once and posts it to `POST /api/v9/me/preferences`. Each refresh
 * otherwise opens the server's `timerView`, List when the key is missing or holds anything else.
 * A server without Tokkl's preferences, such as Toggl Track, gets nothing: the choice stays on the
 * phone.
 *
 * @param store keeps the view on the phone. The first read loads it, on the reading thread.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param keepsTimerView true when the server keeps Tokkl's preferences, read when a tap is sent.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 */
class TimerViewSetting(
    store: ClearableStore<StoredTimerView>,
    client: ApiClientProvider,
    private val keepsTimerView: () -> Boolean,
    scope: CoroutineScope,
    io: CoroutineContext,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() {
        forget()
        preference.clear()
    }

    private val preference = SyncedPreference(
        store,
        client,
        scope,
        io,
        default = TimerView.LIST,
        codec = object : PreferenceCodec<TimerView> {
            override fun fromServer(preferences: MePreferences) = preferences.timerView()

            override fun body(value: TimerView) = TimerViewChange(value.preferenceValue)
        },
    )

    /** The view the Timer page shows. */
    val view: StateFlow<TimerView> get() = preference.value

    /** Shows [view] at once, keeps it on the phone and sends it to the server in the background. */
    fun choose(view: TimerView) {
        preference.change(view, unsent = true, sendIf = keepsTimerView)
    }

    /**
     * Takes the refresh's `GET /api/v9/me/preferences`, null when it failed, from a server that
     * keeps Tokkl's preferences when [keepsTimerView]. Sends an unsent choice instead of reading
     * the server's. Blocks on disk I/O, so call it off the main thread.
     */
    fun refreshed(preferences: MePreferences?, keepsTimerView: Boolean) {
        preference.refreshed(preferences, keepsTimerView)
    }

    /**
     * Goes back to List with nothing unsent. Signing out calls this once the client is gone, so no
     * send can succeed, and then deletes the stored view.
     */
    fun forget() {
        preference.forget()
    }

    internal companion object {
        /** Tokkl's preference key, which ../tokkl/packages/api/src/models/users.ts accepts as list or calendar. */
        const val PREFERENCE = TimerViewChange.KEY
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
