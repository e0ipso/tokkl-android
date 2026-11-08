package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.AccountRefreshed
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.CoroutineContext

/**
 * The lengths a Pomodoro session starts with: [workMinutes] of work, [shortRestMinutes] of rest
 * after it, and [longRestMinutes] of rest instead after every [longRestEvery] work phases.
 */
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
 * @param store keeps the values on the phone. The first read loads them, on the reading thread.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param keepsPreferences true when the server keeps Tokkl's preferences.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 */
class PomodoroDefaultsSetting(
    store: ClearableStore<StoredPomodoroDefaults>,
    client: ApiClientProvider,
    private val keepsPreferences: () -> Boolean,
    scope: CoroutineScope,
    io: CoroutineContext,
) : AccountRefreshListener,
    AccountDataOwner {

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
        default = PomodoroDefaults.DEFAULT,
        loaded = PomodoroDefaults::clamped,
        fromServer = { it.pomodoroDefaults() },
        body = { it.preferencesBody() },
    )

    /** The values a new session starts with, always within their ranges. */
    val defaults: StateFlow<PomodoroDefaults> get() = preference.value

    /**
     * Clamps [defaults] to their ranges, shows them at once, keeps them on the phone and sends
     * them to the server in the background. Call it on the main thread.
     */
    fun change(defaults: PomodoroDefaults): Job {
        val keepsPreferences = keepsPreferences()
        return preference.change(defaults.clamped(), unsent = keepsPreferences, sendIf = { keepsPreferences })
    }

    override fun refreshed(refresh: AccountRefreshed) {
        refreshed(refresh.preferences, keepsPreferences = refresh.capabilities.favorites)
    }

    /**
     * Takes the refresh's `GET /api/v9/me/preferences`, null when it failed, from a server that
     * keeps Tokkl's preferences when [keepsPreferences]. Sends unsent values instead of reading
     * the server's. Blocks on disk I/O, so call it off the main thread.
     */
    fun refreshed(preferences: MePreferences?, keepsPreferences: Boolean) {
        preference.refreshed(preferences, keepsPreferences)
    }

    /**
     * Goes back to Tokkl's defaults with nothing unsent. Signing out calls this once the client is
     * gone, so no send can succeed, and then deletes the stored values.
     */
    fun forget() {
        preference.forget()
    }

    internal companion object {
        /** Tokkl's preference keys, in the order of [PomodoroDefaults]'s values. */
        const val WORK_MINUTES_KEY = "pomodoro_focus_interval_in_minutes"
        const val SHORT_REST_MINUTES_KEY = "pomodoro_break_interval_in_minutes"
        const val LONG_REST_MINUTES_KEY = "pomodoro_long_break_interval_in_minutes"
        const val LONG_REST_EVERY_KEY = "pomodoro_long_break_every"
    }
}

/** The body of `POST /api/v9/me/preferences`: the four keys, clamped, and nothing else. */
internal fun PomodoroDefaults.preferencesBody(): JsonObject {
    val values = clamped()
    return buildJsonObject {
        put(PomodoroDefaultsSetting.WORK_MINUTES_KEY, values.workMinutes)
        put(PomodoroDefaultsSetting.SHORT_REST_MINUTES_KEY, values.shortRestMinutes)
        put(PomodoroDefaultsSetting.LONG_REST_MINUTES_KEY, values.longRestMinutes)
        put(PomodoroDefaultsSetting.LONG_REST_EVERY_KEY, values.longRestEvery)
    }
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
            PomodoroDefaultsSetting.WORK_MINUTES_KEY,
            PomodoroDefaults.WORK_MINUTES,
            default.workMinutes,
        ),
        shortRestMinutes = integerIn(
            PomodoroDefaultsSetting.SHORT_REST_MINUTES_KEY,
            PomodoroDefaults.SHORT_REST_MINUTES,
            default.shortRestMinutes,
        ),
        longRestMinutes = integerIn(
            PomodoroDefaultsSetting.LONG_REST_MINUTES_KEY,
            PomodoroDefaults.LONG_REST_MINUTES,
            default.longRestMinutes,
        ),
        longRestEvery = integerIn(
            PomodoroDefaultsSetting.LONG_REST_EVERY_KEY,
            PomodoroDefaults.LONG_REST_EVERY,
            default.longRestEvery,
        ),
    )
}

/** [key] as an integer in [range], as Tokkl's integerFrom accepts it, or [default]. Strings and booleans fail. */
private fun MePreferences.integerIn(key: String, range: IntRange, default: Int): Int {
    val primitive = (body[key] as? JsonPrimitive)?.takeUnless { it.isString } ?: return default
    val number = primitive.doubleOrNull ?: return default
    if (number % 1.0 != 0.0 || number < range.first || number > range.last) return default
    return number.toInt()
}
