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
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.notification.RunningNotifier
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
    private val session = PomodoroSession(
        workspaceId = 7, workMinutes = 25.0, shortRestMinutes = 5.0, longRestMinutes = 15.0, longRestEvery = 4.0,
        phaseKind = "work", phaseStartedAt = start, pomodoroStartedAt = start, completed = 0.0,
        fields = EntryFields("Native session clock"),
    )

    @Test fun nativeClockHasOneFixedPlannedEndAndShowsThePhaseNumberAndDescription() {
        val built = PomodoroSessionNotifier(context).build(session.notificationContent(), "native-clock")
        assertEquals(start.plusSeconds(1500).toEpochMilli(), built.`when`)
        assertTrue(built.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(built.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(built.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Work · Pomodoro 1", built.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Native session clock", built.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Next phase", "End session"), built.actions.map { it.title.toString() })
        assertEquals(PomodoroSessionNotifier.CHANNEL_ID, built.channelId)
        assertNotEquals(RunningNotifier.CHANNEL_ID, built.channelId)
        assertNotEquals(PomodoroRingingNotifier.CHANNEL_ID, built.channelId)
        assertEquals(3, PomodoroSessionNotification.ID)
        assertNotEquals(RunningNotifier.NOTIFICATION_ID, PomodoroSessionNotification.ID)
        assertNull(built.fullScreenIntent)
        assertEquals(Notification.CATEGORY_STOPWATCH, built.category)
    }

    @Test fun postedIntentsKeepDistinctActionPhaseAndAccountIdentities() {
        val notifier = PomodoroSessionNotifier(context)
        val first = notifier.build(session.notificationContent(), "account-a")
        val same = notifier.build(session.notificationContent(), "account-a")
        val next = notifier.build(session.copy(phaseKind = "shortRest", completed = 1.0).notificationContent(), "account-a")
        val account = notifier.build(session.notificationContent(), "account-b")
        try {
            assertEquals(first.contentIntent, same.contentIntent)
            assertNotEquals(first.contentIntent, next.contentIntent)
            assertNotEquals(first.contentIntent, account.contentIntent)
            assertNotEquals(first.actions[0].actionIntent, first.actions[1].actionIntent)
            assertNotEquals(first.actions[0].actionIntent, next.actions[0].actionIntent)
            assertNotEquals(first.actions[1].actionIntent, account.actions[1].actionIntent)
            assertTrue(first.contentIntent.isActivity)
            assertTrue(first.actions.all { it.actionIntent.isBroadcast && it.actionIntent.isImmutable })
        } finally {
            listOf(first, next, account).forEach { notification ->
                notification.contentIntent.cancel()
                notification.actions.forEach { it.actionIntent.cancel() }
            }
        }
    }

    @Test fun tapCarriesTheGuardedPhaseAndIsConsumedOnlyOnceWithoutAffectingLauncherIntents() {
        val tap = pomodoroSessionOpenIntent(context, "tap-account", session.phaseKey())
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertEquals(PomodoroNotificationTap("tap-account", session.phaseKey()), tap.takePomodoroNotificationTap())
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
            val display = PomodoroNotificationDisplay("fixture-account", session.notificationContent().key)
            store.save(display)
            assertEquals(display, PomodoroSessionNotificationStore(fixture).load())
            val all = fixture.getSharedPreferences("ignored", Context.MODE_PRIVATE).all
            assertEquals(setOf("account", "shown"), all.keys)
            assertFalse(all.values.any { it.toString().contains(session.fields.description) })
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
        assertFalse(PomodoroSessionNotifier(denied).post(session.notificationContent(), "denied"))
        assertEquals(before, manager.activeNotifications.map { it.key }.toSet())
    }

    @Test fun restPhaseLabelsAndBlankDescriptionUseResources() {
        val notifier = PomodoroSessionNotifier(context)
        for ((kind, label) in listOf("shortRest" to R.string.pomodoro_session_short_rest, "longRest" to R.string.pomodoro_session_long_rest)) {
            val built = notifier.build(session.copy(phaseKind = kind, completed = 1.0, fields = EntryFields()).notificationContent(), "labels")
            assertEquals(context.getString(R.string.pomodoro_session_title, context.getString(label), 1), built.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(context.getString(R.string.pomodoro_session_no_description), built.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        }
    }
}
