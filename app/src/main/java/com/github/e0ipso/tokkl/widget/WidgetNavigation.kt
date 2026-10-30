package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.content.Intent
import com.github.e0ipso.tokkl.MainActivity

/** Page-only widget routes. Session notification taps keep their separate account/phase guard. */
object WidgetNavigation {
    const val OPEN_POMODORO = "open_pomodoro_widget"
    const val OPEN_TODAY = "open_today_widget"
    const val OPEN_TIMER = "open_timer_widget"

    fun intent(context: Context, page: String): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(page, true)
}

enum class WidgetPage { TIMER, TODAY, POMODORO }

/** Remove consumed extras, so rotation cannot replay an already handled tap. */
internal fun Intent.takeWidgetPage(): WidgetPage? {
    val page = when {
        getBooleanExtra(WidgetNavigation.OPEN_POMODORO, false) -> WidgetPage.POMODORO
        getBooleanExtra(WidgetNavigation.OPEN_TODAY, false) -> WidgetPage.TODAY
        getBooleanExtra(WidgetNavigation.OPEN_TIMER, false) -> WidgetPage.TIMER
        else -> null
    }
    removeExtra(WidgetNavigation.OPEN_POMODORO)
    removeExtra(WidgetNavigation.OPEN_TODAY)
    removeExtra(WidgetNavigation.OPEN_TIMER)
    return page
}
