package com.github.e0ipso.tokkl.navigation

import android.content.Intent

/**
 * A tap outside the app that opens one page of MainActivity, such as a widget's or a reminder's,
 * and the boolean extra its intent carries. The surface that makes the intent adds the tap with
 * [into], and the feature that opens the page reads it with [isIn], so both read one name.
 */
enum class PageTap(val extra: String) {
    /** A Timer widget's tap, which opens Timer. */
    TIMER_WIDGET("open_timer_widget"),

    /** The Timer widget's day report tap, which opens today's report. */
    TODAY_REPORT("open_today_widget"),

    /** The Pomodoro widget's tap, which opens Pomodoro. */
    POMODORO_WIDGET("open_pomodoro_widget"),

    /** A long-running timer reminder's tap, which opens Timer. */
    REMINDER("com.github.e0ipso.tokkl.OPEN_TIMER_REMINDER"),
    ;

    /**
     * [mainActivity], an intent that opens MainActivity, carrying this tap: in a new task, or in the
     * one on show, brought back to MainActivity.
     */
    fun into(mainActivity: Intent): Intent = mainActivity
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(extra, true)

    /** Whether [intent] carries this tap. */
    fun isIn(intent: LinkIntent): Boolean = intent.flag(extra)
}
