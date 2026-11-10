package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.navigation.LinkIntent
import com.github.e0ipso.tokkl.navigation.PageTap
import com.github.e0ipso.tokkl.pomodoro.EXTRA_PHASE_START
import com.github.e0ipso.tokkl.pomodoro.EXTRA_REVISION
import com.github.e0ipso.tokkl.pomodoro.POMODORO_NOTIFICATION_ACCOUNT
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionTap
import com.github.e0ipso.tokkl.pomodoro.SHOW_POMODORO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pages that MainActivity's intents ask to open, as the features the app's graph binds read
 * them.
 */
class DeepLinksTest {

    private val links = DeepLinks(appFeatures.requests)

    private class Extras(override val action: String? = null, private val extras: Map<String, Any> = emptyMap()) :
        LinkIntent {
        override fun flag(key: String) = extras[key] == true

        override fun text(key: String) = extras[key] as? String

        override fun number(key: String) = extras[key] as? Long
    }

    @Test
    fun aSessionNotificationsTapNeedsItsActionAccountAndPhase() {
        val tap = mapOf(POMODORO_NOTIFICATION_ACCOUNT to "account-a", EXTRA_PHASE_START to 1_700_000_000_000L)
        assertEquals(
            listOf(PomodoroSessionTap("account-a", 1_700_000_000_000L)),
            links.parse(Extras(SHOW_POMODORO, tap)),
        )
        assertTrue("another action opens nothing", links.parse(Extras("android.intent.action.MAIN", tap)).isEmpty())
        assertTrue("no phase opens nothing", links.parse(Extras(SHOW_POMODORO, tap - EXTRA_PHASE_START)).isEmpty())
        assertTrue(
            "no account opens nothing",
            links.parse(Extras(SHOW_POMODORO, tap - POMODORO_NOTIFICATION_ACCOUNT)).isEmpty(),
        )
    }

    @Test
    fun aLauncherIntentOpensNothing() {
        assertTrue(links.parse(Extras("android.intent.action.MAIN")).isEmpty())
    }

    @Test
    fun everyExtraOfATapIsTakenOnce() {
        val extras = listOf(
            POMODORO_NOTIFICATION_ACCOUNT,
            EXTRA_PHASE_START,
            EXTRA_REVISION,
        ) + PageTap.entries.map { it.extra }
        assertEquals(extras.sorted(), links.extras.sorted())
    }
}
