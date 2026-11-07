package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.AccountRefreshed
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.session.SignOutOrder
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.IOException
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
data class StoredPomodoroDefaults(val defaults: PomodoroDefaults, val unsent: Boolean)

/**
 * The Pomodoro defaults, which Tokkl keeps in the account's `pomodoro_focus_interval_in_minutes`,
 * `pomodoro_break_interval_in_minutes`, `pomodoro_long_break_interval_in_minutes` and
 * `pomodoro_long_break_every` preferences, which the web app's Settings edit. The phone keeps the
 * last values too, so a cold start has them before the refresh answers, and offline. It never
 * reads or writes `pomodoro_enabled` or `pomodoro_stop_timer_at_end`.
 *
 * A change shows at once, clamped to the ranges Tokkl accepts, and goes to
 * `POST /api/v9/me/preferences` with only the four keys, without waiting for the answer. When
 * that fails, as offline, the values stay on the phone, marked unsent, and go to the server with
 * the next refresh, so the change that reaches the server last wins, as for time entries
 * (docs/PRD.md O3). Each refresh otherwise takes the server's values, except the one refresh right
 * after a change, whose preferences may predate it. A server without Tokkl's preferences, such
 * as Toggl Track, gets nothing: the values stay on the phone, from 25, 5, 15 and 4.
 *
 * @param store keeps the values on the phone. The first read loads them, on the reading thread.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param keepsPreferences true when the server keeps Tokkl's preferences.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 */
class PomodoroDefaultsSetting(
    private val store: ClearableStore<StoredPomodoroDefaults>,
    private val client: ApiClientProvider,
    private val keepsPreferences: () -> Boolean,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AccountRefreshListener,
    AccountDataOwner {

    override val signOutOrder = SignOutOrder.POMODORO_DEFAULTS

    override suspend fun forgetAccount() {
        forget()
        store.clear()
    }

    private val loaded by lazy { Loaded(store.load()) }

    /** What the store held, and what changed since. Guarded by [lock], apart from [defaults]' reads. */
    private class Loaded(stored: StoredPomodoroDefaults?) {
        val defaults = MutableStateFlow(stored?.defaults?.clamped() ?: PomodoroDefaults.DEFAULT)

        /** The server has not received the values in [defaults]. */
        var unsent = stored?.unsent ?: false
    }

    /** The values a new session starts with, always within their ranges. */
    val defaults: StateFlow<PomodoroDefaults> by lazy { loaded.defaults.asStateFlow() }

    /** Guards [Loaded.unsent], [changedSinceRefresh] and each change of [defaults] with them. */
    private val lock = Any()

    /** A change came after the last refresh, whose preferences may predate it. */
    private var changedSinceRefresh = false

    /** Writes go one at a time, so the last change reaches the server last. */
    private val sending = Mutex()

    /** Orders the writes to disk, so the last one holds the latest values. */
    private val storing = Any()

    /**
     * Clamps [defaults] to their ranges, shows them at once, keeps them on the phone and sends
     * them to the server in the background. Call it on the main thread.
     */
    fun change(defaults: PomodoroDefaults): Job {
        val keepsPreferences = keepsPreferences()
        synchronized(lock) {
            loaded.defaults.value = defaults.clamped()
            loaded.unsent = keepsPreferences
            changedSinceRefresh = true
        }
        return scope.launch(io) {
            persist()
            if (keepsPreferences) send()
        }
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
        val sendNow = synchronized(lock) {
            val changed = changedSinceRefresh
            changedSinceRefresh = false
            when {
                // The values stay on the phone.
                !keepsPreferences -> {
                    loaded.unsent = false
                    false
                }

                loaded.unsent -> true

                changed || preferences == null -> false

                else -> {
                    loaded.defaults.value = preferences.pomodoroDefaults()
                    false
                }
            }
        }
        persist()
        if (sendNow) scope.launch(io) { send() }
    }

    /**
     * Goes back to Tokkl's defaults with nothing unsent. Signing out calls this once the client is
     * gone, so no send can succeed, and then deletes the stored values.
     */
    fun forget() {
        synchronized(storing) {
            synchronized(lock) {
                loaded.defaults.value = PomodoroDefaults.DEFAULT
                loaded.unsent = false
                changedSinceRefresh = false
            }
        }
    }

    /** Posts the values on show while they are unsent. A failure leaves them unsent for the next refresh. */
    private suspend fun send() = sending.withLock {
        val defaults = synchronized(lock) { loaded.defaults.value.takeIf { loaded.unsent } } ?: return@withLock
        val api = client() ?: return@withLock
        val answer = api.send(
            "POST",
            api.server.api("me", "preferences"),
            defaults.preferencesBody(),
            JsonObject.serializer(),
        )
        if (answer !is ApiResult.Success) return@withLock
        synchronized(lock) {
            // A change while the request ran has its own write queued.
            if (loaded.defaults.value == defaults) loaded.unsent = false
        }
        persist()
    }

    private fun persist() {
        synchronized(storing) {
            val stored = synchronized(lock) { StoredPomodoroDefaults(loaded.defaults.value, loaded.unsent) }
            try {
                store.save(stored)
            } catch (e: IOException) {
                // The next change writes it again.
            }
        }
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
