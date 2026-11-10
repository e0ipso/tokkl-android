package com.github.e0ipso.tokkl.notification

import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock

/** Records real preference writes, including writes that begin before the test method runs. */
internal object RunningNotificationRestartTrace {
    private val events = ArrayDeque<String>()

    fun preferences(name: String, preferences: SharedPreferences): SharedPreferences {
        if (name != "running_notification" && name != "timer_state") return preferences
        record("open $name: ${snapshot(name, preferences)}")
        return object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean {
                        record("commit starts $name: ${snapshot(name, preferences)}")
                        val saved = editor.commit()
                        record("commit ends $name saved=$saved: ${snapshot(name, preferences)}")
                        return saved
                    }

                    override fun apply() {
                        record("apply starts $name: ${snapshot(name, preferences)}")
                        editor.apply()
                        record("apply returns $name: ${snapshot(name, preferences)}")
                    }
                }
            }
        }
    }

    fun report(): String = synchronized(events) { events.joinToString("\n") }

    private fun snapshot(name: String, preferences: SharedPreferences): String = if (name == "timer_state") {
        "account=${preferences.getString("account", null)}"
    } else {
        preferences.all.toString()
    }

    private fun record(event: String) = synchronized(events) {
        if (events.size == MAX_EVENTS) events.removeFirst()
        events.addLast("${Process.myPid()} ${SystemClock.elapsedRealtimeNanos()}: $event")
    }

    private const val MAX_EVENTS = 100
}
