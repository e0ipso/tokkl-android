package com.github.e0ipso.tokkl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permissions every store page lists stay the ones the app uses. WorkManager's manifest adds
 * FOREGROUND_SERVICE and SystemForegroundService for workers that call setForeground, which the
 * queue's worker never does. Only the Pomodoro ringing service uses mediaPlayback; the worker
 * service stays removed. Its type permission and VIBRATE support ringing. The timer sync service
 * is the one specialUse service, with its type permission and the subtype Android 14 requires,
 * which says why it runs. WAKE_LOCK and
 * RECEIVE_BOOT_COMPLETED stay for the background send. A Pomodoro phase end adds the exact-alarm
 * permission each API allows: SCHEDULE_EXACT_ALARM through API 32, and USE_EXACT_ALARM from API 33.
 * USE_FULL_SCREEN_INTENT is only for its lock-screen alarm Activity.
 */
class MergedPermissionsTest {

    @Test
    fun theMergedManifestAsksForThePermissionsTheAppUsesOnly() {
        val permissions = MergedManifest.manifest().childElements()
            .filter { it.tagName == "uses-permission" }
            .map { it.androidAttribute("name") }
            // androidx.core's signature permission of the app's own, which guards its unexported receivers.
            .filterNot { it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
            .sorted()

        assertEquals(
            listOf(
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.POST_PROMOTED_NOTIFICATIONS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.USE_EXACT_ALARM",
                "android.permission.USE_FULL_SCREEN_INTENT",
                "android.permission.VIBRATE",
                "android.permission.WAKE_LOCK",
            ),
            permissions,
        )
    }

    @Test
    fun pomodoroAlarmHasAnUnexportedLockScreenTask() {
        val alarm = MergedManifest.application().childElements().single {
            it.tagName == "activity" && it.androidAttribute("name").endsWith(".PomodoroAlarmActivity")
        }
        assertEquals("false", alarm.androidAttribute("exported"))
        assertEquals("true", alarm.androidAttribute("showWhenLocked"))
        assertEquals("true", alarm.androidAttribute("turnScreenOn"))
        assertEquals("true", alarm.androidAttribute("excludeFromRecents"))
        assertEquals("singleTask", alarm.androidAttribute("launchMode"))
        assertEquals("com.canpicasoft.tokkl.pomodoroAlarm", alarm.androidAttribute("taskAffinity"))
        assertEquals("@style/Theme.Tokkl", alarm.androidAttribute("theme"))
        assertFalse(alarm.childElements().any { it.tagName == "intent-filter" })
    }

    @Test
    fun exactAlarmsUseThePermissionEachApiAllows() {
        val permissions = MergedManifest.manifest().childElements().filter { it.tagName == "uses-permission" }
        val schedule = permissions.single { it.androidAttribute("name") == "android.permission.SCHEDULE_EXACT_ALARM" }
        val use = permissions.single { it.androidAttribute("name") == "android.permission.USE_EXACT_ALARM" }

        assertEquals("32", schedule.androidAttribute("maxSdkVersion"))
        assertEquals("33", use.androidAttribute("minSdkVersion"))
    }

    @Test
    fun thePhaseAlarmReceiversAreDeclared() {
        val receivers = MergedManifest.application().childElements().filter { it.tagName == "receiver" }
        val phase = receivers.single { it.androidAttribute("name").endsWith(".PomodoroPhaseAlarmReceiver") }
        val rearm = receivers.single { it.androidAttribute("name").endsWith(".PomodoroAlarmRearmReceiver") }

        assertEquals("false", phase.androidAttribute("exported"))
        assertEquals("false", rearm.androidAttribute("enabled"))
        assertEquals("true", rearm.androidAttribute("exported"))
        val actions = rearm.childElements()
            .filter { it.tagName == "intent-filter" }
            .flatMap { it.childElements() }
            .filter { it.tagName == "action" }
            .map { it.androidAttribute("name") }
            .sorted()
        assertEquals(
            listOf(
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIMEZONE_CHANGED",
                "android.intent.action.TIME_SET",
            ),
            actions,
        )
    }

    @Test
    fun onlyTheRingingAndTimerSyncServicesMayRunInTheForeground() {
        val services = MergedManifest.application().childElements()
            .filter { it.tagName == "service" }
            .map { it.androidAttribute("name") }

        assertFalse(services.toString(), "androidx.work.impl.foreground.SystemForegroundService" in services)
        val foreground = MergedManifest.application().childElements().filter {
            it.tagName == "service" && it.getAttributeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType").isNotEmpty()
        }
        assertEquals(
            listOf("com.github.e0ipso.tokkl.pomodoro.PomodoroRingingService", "com.github.e0ipso.tokkl.timerstate.TimerSyncService"),
            foreground.map { it.androidAttribute("name") }.sorted(),
        )
        val ringing = foreground.single { it.androidAttribute("name").endsWith(".PomodoroRingingService") }
        assertEquals("mediaPlayback", ringing.androidAttribute("foregroundServiceType"))
        assertEquals("false", ringing.androidAttribute("exported"))
        assertEquals("false", ringing.androidAttribute("stopWithTask"))
        val sync = foreground.single { it.androidAttribute("name").endsWith(".TimerSyncService") }
        assertEquals("specialUse", sync.androidAttribute("foregroundServiceType"))
        assertEquals("false", sync.androidAttribute("exported"))
        assertEquals("false", sync.androidAttribute("stopWithTask"))
        val subtype = sync.childElements().single { it.tagName == "property" }
        assertEquals("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", subtype.androidAttribute("name"))
        assertTrue("the subtype says why the service runs", subtype.androidAttribute("value").isNotBlank())
        // Catch an accidental additional service even if it forgot to declare a foreground type.
        assertEquals(
            listOf(
                "androidx.core.widget.RemoteViewsCompatService",
                "androidx.glance.appwidget.GlanceRemoteViewsService",
                "androidx.room.MultiInstanceInvalidationService",
                "androidx.work.impl.background.systemjob.SystemJobService",
                "com.github.e0ipso.tokkl.pomodoro.PomodoroRingingService",
                "com.github.e0ipso.tokkl.tile.RunningTileService",
                "com.github.e0ipso.tokkl.timerstate.TimerSyncService",
            ),
            services.sorted(),
        )
        val action = MergedManifest.application().childElements().single {
            it.tagName == "receiver" && it.androidAttribute("name").endsWith(".PomodoroRingingActionReceiver")
        }
        assertEquals("false", action.androidAttribute("exported"))
    }
}
