package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.TimerViewChange
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.preferences.PreferenceCodec
import com.github.e0ipso.tokkl.preferences.StoredPreference
import com.github.e0ipso.tokkl.preferences.SyncedPreference
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
 * @param store keeps the view on the phone. It loads from it as this is made, off the main thread.
 * @param capabilities whether the server keeps Tokkl's preferences, read when a tap is sent.
 * @param account the account each change belongs to: once sign-out has ended it, nothing more is stored or sent.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 */
class TimerViewSetting(
    store: ClearableStore<StoredTimerView>,
    client: ApiClientProvider,
    capabilities: CapabilitiesSource,
    account: CurrentAccount,
    scope: CoroutineScope,
    io: CoroutineContext,
) : SyncedPreference<TimerView>(
    store,
    client,
    account,
    scope,
    io,
    default = TimerView.LIST,
    codec = object : PreferenceCodec<TimerView> {
        override fun fromServer(preferences: MePreferences) = preferences.timerView()

        override fun body(value: TimerView) = TimerViewChange(value.preferenceValue)
    },
    sharing = { capabilities.keepsPreferences() },
) {

    val view: StateFlow<TimerView> get() = value

    /** Shows [view] at once, keeps it on the phone and sends it to the server in the background. */
    fun choose(view: TimerView) = change(view)
}

/**
 * The preferences' `timerView`: Calendar for `calendar`, and List for `list`, anything else or
 * nothing, as TimerPage.tsx reads it.
 */
internal fun MePreferences.timerView(): TimerView {
    val value = string(TimerViewChange.KEY)
    return TimerView.entries.firstOrNull { it.preferenceValue == value } ?: TimerView.LIST
}

/**
 * How the Timer page shows time entries, as the web app's view switch does. [preferenceValue] is
 * the view's value in Tokkl's `timerView` preference.
 */
enum class TimerView(@StringRes val label: Int, val preferenceValue: String) {
    LIST(R.string.timer_view_list, "list"),

    /** The calendar: the week, or the day on a phone held upright. */
    CALENDAR(R.string.timer_view_calendar, "calendar"),
}
