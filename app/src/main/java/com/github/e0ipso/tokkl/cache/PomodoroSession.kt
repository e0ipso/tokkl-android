package com.github.e0ipso.tokkl.cache

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * The signed-in account's running Pomodoro, one row in [CacheDatabase.FILE_NAME]. The lengths,
 * the entry fields, the phase and the current pomodoro's unsnapped start commit with the queue
 * change that finishes a pomodoro, and sign-out removes the row with the rest of the cache.
 * [completed] counts work phases ended since the session began or the last long rest ended, as
 * [com.github.e0ipso.tokkl.time.SessionPhase] does.
 *
 * @property pomodoroStartedAt the unsnapped instant the current pomodoro's work began. The next
 *   one starts at the previous one's unsnapped end, not at its snapped stop.
 */
@Entity(tableName = "pomodoro_session")
data class PomodoroSession(
    @PrimaryKey val slot: Int = 0,
    val workspaceId: Long,
    val workMinutes: Double,
    val shortRestMinutes: Double,
    val longRestMinutes: Double,
    val longRestEvery: Double,
    val phaseKind: String,
    val phaseStartedAt: Instant,
    val pomodoroStartedAt: Instant,
    val completed: Double,
    @Embedded(prefix = "fields_") val fields: EntryFields,
)
