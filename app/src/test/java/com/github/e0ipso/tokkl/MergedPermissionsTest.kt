package com.github.e0ipso.tokkl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The permissions every store page lists stay the ones the app uses. WorkManager's manifest adds
 * FOREGROUND_SERVICE and SystemForegroundService for workers that call setForeground, which the
 * queue's worker never does, so the app's manifest removes both. WAKE_LOCK and
 * RECEIVE_BOOT_COMPLETED stay for the background send.
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
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.WAKE_LOCK",
            ),
            permissions,
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
