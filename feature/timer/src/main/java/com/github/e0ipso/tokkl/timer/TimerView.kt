package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import com.github.e0ipso.tokkl.feature.timer.R

/**
 * How the Timer page shows time entries, as the web app's view switch does. [preferenceValue] is
 * the view's value in Tokkl's `timerView` preference.
 */
enum class TimerView(@StringRes val label: Int, val preferenceValue: String) {
    /** The entries, a day at a time. */
    LIST(R.string.timer_view_list, "list"),

    /** The calendar: the week, or the day on a phone held upright. */
    CALENDAR(R.string.timer_view_calendar, "calendar"),
}
