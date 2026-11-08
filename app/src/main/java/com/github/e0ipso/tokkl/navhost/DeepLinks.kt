package com.github.e0ipso.tokkl.navhost

import android.content.Intent
import com.github.e0ipso.tokkl.pomodoro.EXTRA_PHASE_START
import com.github.e0ipso.tokkl.pomodoro.EXTRA_REVISION
import com.github.e0ipso.tokkl.pomodoro.POMODORO_NOTIFICATION_ACCOUNT
import com.github.e0ipso.tokkl.pomodoro.SHOW_POMODORO
import com.github.e0ipso.tokkl.reminder.ReminderNotifier
import com.github.e0ipso.tokkl.widget.WidgetNavigation
import com.github.e0ipso.tokkl.widget.WidgetPage

/** What [DeepLinks] reads of one of MainActivity's intents: its action and its extras. */
internal interface LinkIntent {
    val action: String?

    /** The boolean extra [key], false when absent. */
    fun flag(key: String): Boolean

    /** The string extra [key], or null. */
    fun text(key: String): String?

    /** The long extra [key], or null when absent. */
    fun number(key: String): Long?
}

/**
 * The pages that one of MainActivity's intents asks to open: a widget's page, a Pomodoro session
 * notification's tap, which names its account and phase, and a reminder's tap, which opens Timer.
 * It reads only what [LinkIntent] gives it, so it has no Android in it.
 */
internal object DeepLinks {

    /** Every extra [parse] reads. MainActivity removes them once read, so a rotation opens nothing again. */
    val extras = listOf(
        WidgetNavigation.OPEN_POMODORO,
        WidgetNavigation.OPEN_TODAY,
        WidgetNavigation.OPEN_TIMER,
        POMODORO_NOTIFICATION_ACCOUNT,
        EXTRA_PHASE_START,
        EXTRA_REVISION,
        ReminderNotifier.OPEN_TIMER,
    )

    fun parse(intent: LinkIntent): List<NavigationRequest> = listOfNotNull(
        pomodoro(intent),
        NavigationRequest.ReminderTimer.takeIf { intent.flag(ReminderNotifier.OPEN_TIMER) },
        widgetPage(intent)?.let(NavigationRequest::Widget),
    )

    /** A session notification's tap, when it names both its account and its phase. */
    private fun pomodoro(intent: LinkIntent): NavigationRequest.Pomodoro? {
        if (intent.action != SHOW_POMODORO) return null
        val account = intent.text(POMODORO_NOTIFICATION_ACCOUNT) ?: return null
        val phaseStart = intent.number(EXTRA_PHASE_START) ?: return null
        return NavigationRequest.Pomodoro(account, phaseStart)
    }

    private fun widgetPage(intent: LinkIntent): WidgetPage? = when {
        intent.flag(WidgetNavigation.OPEN_POMODORO) -> WidgetPage.POMODORO
        intent.flag(WidgetNavigation.OPEN_TODAY) -> WidgetPage.TODAY
        intent.flag(WidgetNavigation.OPEN_TIMER) -> WidgetPage.TIMER
        else -> null
    }
}

/**
 * The pages this intent asks to open ([DeepLinks]). Removes the extras that name them, so they
 * open nothing again. The action and the data stay, so the activity keeps the identity it was
 * started with.
 */
internal fun Intent.takeDeepLinks(): List<NavigationRequest> {
    val intent = this
    val requests = DeepLinks.parse(
        object : LinkIntent {
            override val action: String? get() = intent.action

            override fun flag(key: String) = intent.getBooleanExtra(key, false)

            override fun text(key: String) = intent.getStringExtra(key)

            override fun number(key: String) = if (intent.hasExtra(key)) intent.getLongExtra(key, 0) else null
        },
    )
    DeepLinks.extras.forEach(::removeExtra)
    return requests
}
