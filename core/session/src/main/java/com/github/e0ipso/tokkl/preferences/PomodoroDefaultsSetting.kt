package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.PomodoroDefaultsChange
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

/** The lengths a Pomodoro session starts with, in minutes; a long rest follows every [longRestEvery] work phases. */
data class PomodoroDefaults(
    val workMinutes: Int,
    val shortRestMinutes: Int,
    val longRestMinutes: Int,
    val longRestEvery: Int,
) {

    /** Each value moved into its range, the one Tokkl accepts in ../tokkl/packages/api/src/models/users.ts. */
    fun clamped(): PomodoroDefaults = PomodoroDefaults(
        workMinutes = workMinutes.coerceIn(WORK_MINUTES),
        shortRestMinutes = shortRestMinutes.coerceIn(SHORT_REST_MINUTES),
        longRestMinutes = longRestMinutes.coerceIn(LONG_REST_MINUTES),
        longRestEvery = longRestEvery.coerceIn(LONG_REST_EVERY),
    )

    companion object {
        /** Tokkl's own defaults for the four keys, which the phone uses until it reads others. */
        val DEFAULT = PomodoroDefaults(workMinutes = 25, shortRestMinutes = 5, longRestMinutes = 15, longRestEvery = 4)

        val WORK_MINUTES = 1..180
        val SHORT_REST_MINUTES = 1..60
        val LONG_REST_MINUTES = 1..60
        val LONG_REST_EVERY = 1..12
    }
}

/** The Pomodoro defaults as the phone keeps them, and whether the server still has to get them. */
typealias StoredPomodoroDefaults = StoredPreference<PomodoroDefaults>

/**
 * The Pomodoro defaults, which Tokkl keeps in the account's `pomodoro_focus_interval_in_minutes`,
 * `pomodoro_break_interval_in_minutes`, `pomodoro_long_break_interval_in_minutes` and
 * `pomodoro_long_break_every` preferences, which the web app's Settings edit. The phone keeps the
 * last values too, as [SyncedPreference] describes. It never reads or writes `pomodoro_enabled` or
 * `pomodoro_stop_timer_at_end`.
 *
 * A change shows at once, clamped to the ranges Tokkl accepts, and goes to
 * `POST /api/v9/me/preferences` with only the four keys. A server without Tokkl's preferences, such
 * as Toggl Track, gets nothing: the values stay on the phone, from 25, 5, 15 and 4.
 *
 * @param store keeps the values on the phone. They load from it as this is made, off the main thread.
 * @param account the account each change belongs to: once sign-out has ended it, nothing more is stored or sent.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 */
class PomodoroDefaultsSetting(
    store: ClearableStore<StoredPomodoroDefaults>,
    client: ApiClientProvider,
    capabilities: CapabilitiesSource,
    account: CurrentAccount,
    scope: CoroutineScope,
    io: CoroutineContext,
) : SyncedPreference<PomodoroDefaults>(
    store,
    client,
    account,
    scope,
    io,
    default = PomodoroDefaults.DEFAULT,
    codec = object : PreferenceCodec<PomodoroDefaults> {
        override fun loaded(value: PomodoroDefaults) = value.clamped()

        override fun fromServer(preferences: MePreferences) = preferences.pomodoroDefaults()

        override fun body(value: PomodoroDefaults) = value.preferencesBody()
    },
    sharing = { capabilities.keepsPreferences() },
) {

    /** The values a new session starts with, always within their ranges. */
    val defaults: StateFlow<PomodoroDefaults> get() = value

    /** [defaults] once loaded, for readers that may run before the load ends. */
    val loadedDefaults: Flow<PomodoroDefaults> get() = loaded

    /** Clamps [value] to their ranges first. Call it on the main thread. */
    override fun change(value: PomodoroDefaults): Job = super.change(value.clamped())
}

/** The change `POST /api/v9/me/preferences` sends: the four keys, clamped, and nothing else. */
internal fun PomodoroDefaults.preferencesBody(): PomodoroDefaultsChange {
    val values = clamped()
    return PomodoroDefaultsChange(
        workMinutes = values.workMinutes,
        shortRestMinutes = values.shortRestMinutes,
        longRestMinutes = values.longRestMinutes,
        longRestEvery = values.longRestEvery,
    )
}

/**
 * The preferences' four Pomodoro lengths. Each key has a reader of its own, so a malformed one
 * falls back to its own default alone. Tokkl's getPreferences hands out its default for a stored
 * value it would refuse, so a key reads as its default unless it is a JSON integer in its range.
 */
internal fun MePreferences.pomodoroDefaults(): PomodoroDefaults {
    val default = PomodoroDefaults.DEFAULT
    return PomodoroDefaults(
        workMinutes = integerIn(
            PomodoroDefaultsChange.WORK_MINUTES,
            PomodoroDefaults.WORK_MINUTES,
            default.workMinutes,
        ),
        shortRestMinutes = integerIn(
            PomodoroDefaultsChange.SHORT_REST_MINUTES,
            PomodoroDefaults.SHORT_REST_MINUTES,
            default.shortRestMinutes,
        ),
        longRestMinutes = integerIn(
            PomodoroDefaultsChange.LONG_REST_MINUTES,
            PomodoroDefaults.LONG_REST_MINUTES,
            default.longRestMinutes,
        ),
        longRestEvery = integerIn(
            PomodoroDefaultsChange.LONG_REST_EVERY,
            PomodoroDefaults.LONG_REST_EVERY,
            default.longRestEvery,
        ),
    )
}

/** [key] as an integer in [range], as Tokkl's integerFrom accepts it, or [default]. Strings and booleans fail. */
private fun MePreferences.integerIn(key: String, range: IntRange, default: Int): Int {
    val number = number(key) ?: return default
    if (number % 1.0 != 0.0 || number < range.first || number > range.last) return default
    return number.toInt()
}
