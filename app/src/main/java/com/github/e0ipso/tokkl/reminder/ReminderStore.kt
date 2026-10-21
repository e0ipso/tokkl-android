package com.github.e0ipso.tokkl.reminder

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/** Account-scoped, excluded from backups with the other private stores. */
class ReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences("long_timer_reminder", Context.MODE_PRIVATE)

    fun load(): StoredReminderPreferences? = if (!preferences.contains("hours")) null else StoredReminderPreferences(
        ReminderPreferences(preferences.getBoolean("enabled", true), preferences.getInt("hours", 8)).clamped(),
        preferences.getBoolean("unsent", false),
        preferences.getBoolean("shared", false),
    )

    fun save(value: StoredReminderPreferences) = commit {
        putBoolean("enabled", value.preferences.enabled)
        putInt("hours", value.preferences.clamped().hours)
        putBoolean("unsent", value.unsent)
        putBoolean("shared", value.shared)
    }

    fun loadTimer(): ReminderTimer? = if (!preferences.contains("timer_id")) null else ReminderTimer(
        preferences.getLong("timer_id", 0), preferences.getLong("timer_start", 0),
        preferences.getLong("timer_due", 0), preferences.getBoolean("timer_handled", false),
    )

    fun saveTimer(timer: ReminderTimer?) = commit {
        if (timer == null) {
            remove("timer_id"); remove("timer_start"); remove("timer_due"); remove("timer_handled")
        } else {
            putLong("timer_id", timer.id); putLong("timer_start", timer.start)
            putLong("timer_due", timer.due); putBoolean("timer_handled", timer.handled)
        }
    }

    fun clear() = commit { clear() }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write long_timer_reminder")
    }
}
