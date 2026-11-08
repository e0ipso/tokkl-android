package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.pomodoro.EXTRA_PHASE_START
import com.github.e0ipso.tokkl.pomodoro.POMODORO_NOTIFICATION_ACCOUNT
import com.github.e0ipso.tokkl.pomodoro.SHOW_POMODORO
import com.github.e0ipso.tokkl.reminder.ReminderNotifier
import com.github.e0ipso.tokkl.widget.WidgetNavigation
import com.github.e0ipso.tokkl.widget.WidgetPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pages that MainActivity's intents ask to open. */
class DeepLinksTest {

    private class Extras(override val action: String? = null, private val extras: Map<String, Any> = emptyMap()) :
        LinkIntent {
        override fun flag(key: String) = extras[key] == true

        override fun text(key: String) = extras[key] as? String

        override fun number(key: String) = extras[key] as? Long
    }

    @Test
    fun aWidgetsTapOpensItsPageAndPomodoroWinsOverTheOthers() {
        assertEquals(
            listOf(NavigationRequest.Widget(WidgetPage.TODAY)),
            DeepLinks.parse(Extras(extras = mapOf(WidgetNavigation.OPEN_TODAY to true))),
        )
        assertEquals(
            listOf(NavigationRequest.Widget(WidgetPage.POMODORO)),
            DeepLinks.parse(
                Extras(extras = mapOf(WidgetNavigation.OPEN_TIMER to true, WidgetNavigation.OPEN_POMODORO to true)),
            ),
        )
    }

    @Test
    fun aSessionNotificationsTapNeedsItsActionAccountAndPhase() {
        val tap = mapOf(POMODORO_NOTIFICATION_ACCOUNT to "account-a", EXTRA_PHASE_START to 1_700_000_000_000L)
        assertEquals(
            listOf(NavigationRequest.Pomodoro("account-a", 1_700_000_000_000L)),
            DeepLinks.parse(Extras(SHOW_POMODORO, tap)),
        )
        assertTrue("another action opens nothing", DeepLinks.parse(Extras("android.intent.action.MAIN", tap)).isEmpty())
        assertTrue(
            "no phase opens nothing",
            DeepLinks.parse(Extras(SHOW_POMODORO, tap - EXTRA_PHASE_START)).isEmpty(),
        )
        assertTrue(
            "no account opens nothing",
            DeepLinks.parse(Extras(SHOW_POMODORO, tap - POMODORO_NOTIFICATION_ACCOUNT)).isEmpty(),
        )
    }

    @Test
    fun aRemindersTapOpensTimerAndALauncherIntentOpensNothing() {
        assertEquals(
            listOf(NavigationRequest.ReminderTimer),
            DeepLinks.parse(Extras(extras = mapOf(ReminderNotifier.OPEN_TIMER to true))),
        )
        assertTrue(DeepLinks.parse(Extras("android.intent.action.MAIN")).isEmpty())
    }
}
