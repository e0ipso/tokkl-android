package com.github.e0ipso.tokkl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The permissions every store page lists stay the ones the app uses. WorkManager's manifest adds
 * FOREGROUND_SERVICE and SystemForegroundService for workers that call setForeground, which the
 * queue's worker never does, so the app's manifest removes both. WAKE_LOCK and
 * RECEIVE_BOOT_COMPLETED stay for the background send. A Pomodoro phase end adds the exact-alarm
 * permission each API allows: SCHEDULE_EXACT_ALARM through API 32, and USE_EXACT_ALARM from API 33.
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
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.POST_PROMOTED_NOTIFICATIONS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.USE_EXACT_ALARM",
                "android.permission.WAKE_LOCK",
            ),
            permissions,
        )
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
    fun noForegroundServiceIsDeclared() {
        val services = MergedManifest.application().childElements()
            .filter { it.tagName == "service" }
            .map { it.androidAttribute("name") }

        assertFalse(services.toString(), "androidx.work.impl.foreground.SystemForegroundService" in services)
        assertFalse(services.toString(), services.any { it.contains("Foreground") })
    }
}
