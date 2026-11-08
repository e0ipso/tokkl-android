package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.content.Intent
import com.github.e0ipso.tokkl.platform.mainActivityIntent

/**
 * Page-only widget routes: each extra names the page a widget's tap opens. Session notification
 * taps keep their separate account and phase guard.
 */
object WidgetNavigation {
    const val OPEN_POMODORO = "open_pomodoro_widget"
    const val OPEN_TODAY = "open_today_widget"
    const val OPEN_TIMER = "open_timer_widget"

    fun intent(context: Context, page: String): Intent = mainActivityIntent(context)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(page, true)
}

enum class WidgetPage { TIMER, TODAY, POMODORO }
