package com.github.e0ipso.tokkl.reminder

import android.content.Context
import com.github.e0ipso.tokkl.preferences.ReminderPreferences
import com.github.e0ipso.tokkl.preferences.StoredReminderPreferences
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlin.coroutines.CoroutineContext

/** Account-scoped, excluded from backups with the other private stores. */
class ReminderStore(context: Context, private val io: CoroutineContext) : ClearableStore<StoredReminderPreferences> {
    private val file = PreferencesFile(context, "long_timer_reminder", io)

    override suspend fun load(): StoredReminderPreferences? = file.read {
        if (!contains("hours")) return@read null
        StoredReminderPreferences(
            ReminderPreferences(getBoolean("enabled", true), getInt("hours", ReminderPreferences().hours)).clamped(),
            getBoolean("unsent", false),
            getBoolean("shared", false),
        )
    }

    override suspend fun save(value: StoredReminderPreferences) = file.commit {
        putBoolean("enabled", value.value.enabled)
        putInt("hours", value.value.clamped().hours)
        putBoolean("unsent", value.unsent)
        putBoolean("shared", value.shared)
    }

    val timers: ValueStore<ReminderTimer?> = object : ValueStore<ReminderTimer?> {
        override suspend fun load(): ReminderTimer? = loadTimer()

        override suspend fun save(value: ReminderTimer?) = saveTimer(value)
    }

    suspend fun loadTimer(): ReminderTimer? = file.read {
        if (!contains("timer_id")) return@read null
        ReminderTimer(
            getLong("timer_id", 0),
            getLong("timer_start", 0),
            getLong("timer_due", 0),
            getBoolean("timer_handled", false),
        )
    }

    suspend fun saveTimer(timer: ReminderTimer?) = file.commit {
        if (timer == null) {
            remove("timer_id")
            remove("timer_start")
            remove("timer_due")
            remove("timer_handled")
        } else {
            putLong("timer_id", timer.id)
            putLong("timer_start", timer.start)
            putLong("timer_due", timer.due)
            putBoolean("timer_handled", timer.handled)
        }
    }

    override suspend fun clear() = file.commit { clear() }
}
