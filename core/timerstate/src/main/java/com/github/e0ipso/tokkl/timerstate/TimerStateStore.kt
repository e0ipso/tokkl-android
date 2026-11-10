package com.github.e0ipso.tokkl.timerstate

import android.content.Context
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.IsoInstantSerializer
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.model.PauseChain
import com.github.e0ipso.tokkl.model.PomodoroSession
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.PreferencesFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * A timer state as the phone holds it, in the model's types, with the server's clock [serverTime]
 * when it sent the state and the phone's clock when it arrived, [receivedAt].
 */
data class HeldTimerState(
    val revision: Long,
    val serverTime: Instant,
    val runningEntry: TimeEntry?,
    val pause: PauseChain?,
    val pomodoro: PomodoroSession?,
    val discardedPomodoro: DiscardedPomodoro?,
    val receivedAt: Instant,
) {
    /** `server_time - receivedAt`. The phone's clock plus this is the server's clock. */
    val clockOffset: Duration get() = Duration.between(receivedAt, serverTime)
}

@Serializable
internal data class StoredHeld(
    val state: TimerState,
    @SerialName("received_at") @Serializable(with = IsoInstantSerializer::class) val receivedAt: Instant,
)

/**
 * What [TimerStateStore] keeps: the state as the server sent it, [wire], and the entry id of the
 * last too-short pomodoro whose notice was taken, [acknowledgedDiscard], so neither a replay nor a
 * restart shows it again.
 */
@Serializable
class StoredTimerState internal constructor(
    @SerialName("held") internal val wire: StoredHeld,
    @SerialName("acknowledged_discard") internal val acknowledgedDiscard: Long?,
) {
    val held: HeldTimerState get() = wire.toHeld()
}

/**
 * The signed-in account's last timer state in its own private preferences file, so the alarm
 * receivers, the notifications and the widget can act on it after process death without a
 * request. It is a snapshot of the server's state, not a session of the phone's own. The file
 * holds no credential and stays on the phone, because the backup and data extraction rules leave
 * out every domain. Sign-out clears it.
 *
 * Every call reads or writes the file on the calling thread, because the timer state's holder
 * writes it under its acceptance lock, on the OkHttp and I/O threads that answers and events
 * arrive on, without suspending. Never call it on the main thread.
 */
class TimerStateStore(context: Context, private val io: CoroutineContext) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = clear()

    private val file = PreferencesFile(context, FILE_NAME, io)
    private val preferences get() = file.preferences

    /** The stored state, or null when none is stored or the file does not decode. */
    fun load(): StoredTimerState? = preferences.getString(KEY_STATE, null)?.let(::decodeStoredTimerState)

    @Throws(IOException::class)
    fun save(stored: StoredTimerState) {
        file.commitHere { putString(KEY_STATE, encodeStoredTimerState(stored)) }
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
            file.commitHere { putString(KEY_ACCOUNT, made) }
        } catch (_: IOException) {
            // Kept in memory: see above.
        }
        return made
    }

    @Throws(IOException::class)
    fun clear() {
        file.commitHere { clear() }
    }

    internal companion object {
        const val FILE_NAME = "timer_state"
        private const val KEY_STATE = "state"
        private const val KEY_ACCOUNT = "account"
    }
}

internal fun encodeStoredTimerState(stored: StoredTimerState): String =
    ApiJson.encodeToString(StoredTimerState.serializer(), stored)

/** The state [text] holds, or null when it does not decode, as after a format change. */
internal fun decodeStoredTimerState(text: String): StoredTimerState? = try {
    ApiJson.decodeFromString(StoredTimerState.serializer(), text)
} catch (_: IllegalArgumentException) {
    // kotlinx.serialization's SerializationException is an IllegalArgumentException.
    null
}
