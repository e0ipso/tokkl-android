package com.github.e0ipso.tokkl.pomodoro

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.notification.RunningNotifier
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native notification and immutable PendingIntent contracts, with no fixture server writes. */
class PomodoroSessionNotifierTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val start = Instant.parse("2026-10-20T09:00:00Z")
    private val held = heldSession(phaseStart = start, number = 2, description = "Native session clock")
    private val content = checkNotNull(held.notificationContent())
    private val key = PomodoroPhaseKey(start)

    @Test fun nativeClockHasOneFixedPlannedEndAndShowsTheServersPhaseNumberUpcomingPhaseAndDescription() {
        val built = PomodoroSessionNotifier(context).build(content, "native-clock")
        assertEquals(start.plusSeconds(1500).toEpochMilli(), built.`when`)
        assertTrue(built.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(built.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(built.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Work · Pomodoro 2 of 4", built.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Native session clock", built.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Up next: Short rest", built.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertEquals(listOf("Next phase", "End session"), built.actions.map { it.title.toString() })
        assertEquals(PomodoroSessionNotifier.CHANNEL_ID, built.channelId)
        assertNotEquals(RunningNotifier.CHANNEL_ID, built.channelId)
        assertNotEquals(PomodoroRingingNotifier.CHANNEL_ID, built.channelId)
        assertEquals(3, PomodoroSessionNotifier.NOTIFICATION_ID)
        assertNotEquals(RunningNotifier.NOTIFICATION_ID, PomodoroSessionNotifier.NOTIFICATION_ID)
        assertNull(built.fullScreenIntent)
        assertEquals(Notification.CATEGORY_STOPWATCH, built.category)
    }

    @Test fun postedIntentsKeepDistinctActionPhaseRevisionAndAccountIdentities() {
        val notifier = PomodoroSessionNotifier(context)
        val first = notifier.build(content, "account-a")
        val same = notifier.build(content, "account-a")
        val next = notifier.build(checkNotNull(heldSession(PhaseKind.SHORT_REST, start.plusSeconds(1500), 300).notificationContent()), "account-a")
        val account = notifier.build(content, "account-b")
        val revision = notifier.build(content.copy(revision = content.revision + 1), "account-a")
        try {
            assertEquals(first.contentIntent, same.contentIntent)
            assertNotEquals(first.contentIntent, next.contentIntent)
            assertNotEquals(first.contentIntent, account.contentIntent)
            assertNotEquals(first.actions[0].actionIntent, first.actions[1].actionIntent)
            assertNotEquals(first.actions[0].actionIntent, next.actions[0].actionIntent)
            assertNotEquals(first.actions[1].actionIntent, account.actions[1].actionIntent)
            assertNotEquals(first.actions[0].actionIntent, revision.actions[0].actionIntent)
            assertTrue(first.contentIntent.isActivity)
            assertTrue(first.actions.all { it.actionIntent.isBroadcast && it.actionIntent.isImmutable })
        } finally {
            listOf(first, next, account, revision).forEach { notification ->
                notification.contentIntent.cancel()
                notification.actions.forEach { it.actionIntent.cancel() }
            }
        }
    }

    @Test fun tapCarriesTheGuardedPhaseAndIsConsumedOnlyOnceWithoutAffectingLauncherIntents() {
        val tap = pomodoroSessionOpenIntent(context, "tap-account", key)
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertEquals(PomodoroNotificationTap("tap-account", key), tap.takePomodoroNotificationTap())
        assertNull(tap.takePomodoroNotificationTap())
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        assertNull(launcher.takePomodoroNotificationTap())
        assertEquals(Intent.ACTION_MAIN, launcher.action)
        assertTrue(launcher.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertNull(Intent(SHOW_POMODORO).takePomodoroNotificationTap())
    }

    @Test fun shownIdentitySurvivesReopenAndForgetErasesAllPersistedDisplayData() {
        val file = "notification-fixture-${UUID.randomUUID()}"
        val fixture = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(file, mode)
        }
        val store = PomodoroSessionNotificationStore(fixture)
        try {
            val display = PomodoroNotificationDisplay("fixture-account", content.key)
            store.save(display)
            assertEquals(display, PomodoroSessionNotificationStore(fixture).load())
            val all = fixture.getSharedPreferences("ignored", Context.MODE_PRIVATE).all
            assertEquals(setOf("account", "shown"), all.keys)
            assertFalse(all.values.any { it.toString().contains(content.description) })
            store.save(null)
            assertNull(PomodoroSessionNotificationStore(fixture).load())
            assertTrue(fixture.getSharedPreferences("ignored", Context.MODE_PRIVATE).all.isEmpty())
        } finally {
            store.save(null)
            assertTrue(context.deleteSharedPreferences(file))
        }
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    fun aDeniedNotificationPermissionDoesNotPostOrThrow() {
        val denied = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String) = PackageManager.PERMISSION_DENIED
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val before = manager.activeNotifications.map { it.key }.toSet()
        assertFalse(PomodoroSessionNotifier(denied).post(content, "denied"))
        assertEquals(before, manager.activeNotifications.map { it.key }.toSet())
    }

    @Test fun restPhaseLabelsAndBlankDescriptionUseResources() {
        val notifier = PomodoroSessionNotifier(context)
        for ((kind, label) in listOf(PhaseKind.SHORT_REST to R.string.pomodoro_session_short_rest, PhaseKind.LONG_REST to R.string.pomodoro_session_long_rest)) {
            val built = notifier.build(checkNotNull(heldSession(kind, start, 300).notificationContent()), "labels")
            assertEquals(context.getString(R.string.pomodoro_session_title, context.getString(label), 1, 4), built.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(context.getString(R.string.pomodoro_session_no_description), built.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        }
    }
}
