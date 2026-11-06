package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults

/** Settings validates drafts before passing them to the shared defaults repository. */
internal enum class PomodoroDefaultField(val range: IntRange) {
    WORK(PomodoroDefaults.WORK_MINUTES),
    SHORT_REST(PomodoroDefaults.SHORT_REST_MINUTES),
    LONG_REST(PomodoroDefaults.LONG_REST_MINUTES),
    EVERY(PomodoroDefaults.LONG_REST_EVERY),
    ;

    fun value(defaults: PomodoroDefaults): Int = when (this) {
        WORK -> defaults.workMinutes
        SHORT_REST -> defaults.shortRestMinutes
        LONG_REST -> defaults.longRestMinutes
        EVERY -> defaults.longRestEvery
    }

    fun parse(input: String): Int? = input.toIntOrNull()?.takeIf { it in range }

    /** Returns no change for an invalid draft. Other defaults retain their current values. */
    fun edited(defaults: PomodoroDefaults, input: String): PomodoroDefaults? {
        val value = parse(input) ?: return null
        return when (this) {
            WORK -> defaults.copy(workMinutes = value)
            SHORT_REST -> defaults.copy(shortRestMinutes = value)
            LONG_REST -> defaults.copy(longRestMinutes = value)
            EVERY -> defaults.copy(longRestEvery = value)
        }
    }
}
