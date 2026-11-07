package com.github.e0ipso.tokkl.reminder

import android.content.Context
import com.github.e0ipso.tokkl.preferences.ReminderPreferences
import com.github.e0ipso.tokkl.preferences.StoredReminderPreferences
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore

/** Account-scoped, excluded from backups with the other private stores. */
class ReminderStore(context: Context) : ClearableStore<StoredReminderPreferences> {
    private val file = PreferencesFile(context, "long_timer_reminder")

    // Opens the file as the store is made, which starts loading it from disk.
    private val preferences = file.preferences

    override fun load(): StoredReminderPreferences? = if (!preferences.contains(
            "hours",
        )
    ) {
        null
    } else {
        StoredReminderPreferences(
            ReminderPreferences(
                preferences.getBoolean("enabled", true),
                preferences.getInt("hours", ReminderPreferences().hours),
            ).clamped(),
            preferences.getBoolean("unsent", false),
            preferences.getBoolean("shared", false),
        )
    }

    override fun save(value: StoredReminderPreferences) = file.commit {
        putBoolean("enabled", value.preferences.enabled)
        putInt("hours", value.preferences.clamped().hours)
        putBoolean("unsent", value.unsent)
        putBoolean("shared", value.shared)
    }

    /** The reminder of the running timer, null for none. */
    val timers: ValueStore<ReminderTimer?> = object : ValueStore<ReminderTimer?> {
        override fun load(): ReminderTimer? = loadTimer()

        override fun save(value: ReminderTimer?) = saveTimer(value)
    }

    fun loadTimer(): ReminderTimer? = if (!preferences.contains("timer_id")) {
        null
    } else {
        ReminderTimer(
            preferences.getLong("timer_id", 0),
            preferences.getLong("timer_start", 0),
            preferences.getLong("timer_due", 0),
            preferences.getBoolean("timer_handled", false),
        )
    }

    fun saveTimer(timer: ReminderTimer?) = file.commit {
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

    override fun clear() = file.commit { clear() }
}
