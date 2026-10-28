package com.github.e0ipso.tokkl.timerstate

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.api.ApiJson
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A timer state as the phone holds it: the server's [state] and the phone's clock when it
 * arrived, [receivedAt].
 */
@Serializable
data class HeldTimerState(
    val state: TimerState,
    @SerialName("received_at") @Serializable(with = IsoInstantSerializer::class) val receivedAt: Instant,
) {
    /** `server_time - receivedAt`. The phone's clock plus this is the server's clock. */
    val clockOffset: Duration get() = Duration.between(receivedAt, state.serverTime)
}

/**
 * What [TimerStateStore] keeps: the [held] state and the entry id of the last too-short pomodoro
 * whose notice was taken, [acknowledgedDiscard], so neither a replay nor a restart shows it again.
 */
@Serializable
data class StoredTimerState(
    val held: HeldTimerState,
    @SerialName("acknowledged_discard") val acknowledgedDiscard: Long?,
)

/**
 * The signed-in account's last timer state in its own private preferences file, so the alarm
 * receivers, the notifications and the widget can act on it after process death without a
 * request. It is a snapshot of the server's state, not a session of the phone's own. The file
 * holds no credential and stays on the phone, because the backup and data extraction rules leave
 * out every domain. Sign-out clears it.
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class TimerStateStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /** The stored state, or null when none is stored or the file does not decode. */
    fun load(): StoredTimerState? = preferences.getString(KEY_STATE, null)?.let(::decodeStoredTimerState)

    /** Replaces the stored state in one write. */
    @Throws(IOException::class)
    fun save(stored: StoredTimerState) {
        commit { putString(KEY_STATE, encodeStoredTimerState(stored)) }
    }

    /**
     * The name the timer surfaces give this account in the targets of their controls, a random
     * UUID made on first use after each [clear] and kept until the next one. Sign-in and sign-out
     * clear it, so a control drawn for one account names none that follows. A name that cannot
     * be written lasts for this process only: the controls drawn with it then act on nothing in
     * a later process, which is safe.
     */
    fun account(): String {
        preferences.getString(KEY_ACCOUNT, null)?.let { return it }
        val made = UUID.randomUUID().toString()
        try {
            commit { putString(KEY_ACCOUNT, made) }
        } catch (e: IOException) {
            // Kept in memory: see above.
        }
        return made
    }

    @Throws(IOException::class)
    fun clear() {
        commit { clear() }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    internal companion object {
        const val FILE_NAME = "timer_state"
        private const val KEY_STATE = "state"
        private const val KEY_ACCOUNT = "account"
    }
}

/** [stored] as the text [TimerStateStore] writes. */
internal fun encodeStoredTimerState(stored: StoredTimerState): String =
    ApiJson.encodeToString(StoredTimerState.serializer(), stored)

/** The state [text] holds, or null when it does not decode, as after a format change. */
internal fun decodeStoredTimerState(text: String): StoredTimerState? =
    try {
        ApiJson.decodeFromString(StoredTimerState.serializer(), text)
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization's SerializationException is an IllegalArgumentException.
        null
    }
