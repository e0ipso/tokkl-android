package com.github.e0ipso.tokkl.pomodoro

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.NotificationPermissionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SignedOut
class PomodoroPermissionPersistenceTest {
    @get:Rule val session = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
    private var wasPresent = false
    private var wasAsked = false

    @Before fun saveAndResetOnlyRequestHistory() {
        wasPresent = preferences.contains("asked")
        wasAsked = preferences.getBoolean("asked", false)
        assertTrue(preferences.edit().remove("asked").commit())
    }

    @After fun restoreRequestHistory() {
        val editor = preferences.edit()
        if (wasPresent) editor.putBoolean("asked", wasAsked) else editor.remove("asked")
        assertTrue(editor.commit())
    }

    @Test fun diskRecordSurvivesFreshStoreAndCoordinatorInstances() {
        // Explicit API value exercises durable request history even on the API 31 test device.
        val first = NotificationPermissionAsk(33, NotificationPermissionStore(context)) { false }
        assertTrue(first.atStartInApp())
        first.requested()
        val recreatedContext = context.createPackageContext(context.packageName, 0)
        val restarted = NotificationPermissionAsk(33, NotificationPermissionStore(recreatedContext)) { false }
        assertFalse(restarted.atStartInApp())
        assertTrue(NotificationPermissionStore(recreatedContext).asked())
    }

    @Test fun signOutKeepsTheDevicePermissionHistoryForTheNextAccount() {
        NotificationPermissionStore(context).markAsked()
        SessionRule.signOut()
        assertTrue(NotificationPermissionStore(context).asked())
        assertFalse(NotificationPermissionAsk(33, NotificationPermissionStore(context)) { false }.atStartInApp())
    }

    @Test fun platformSnapshotAndExactAlarmLinkFollowActualAndroidApis() {
        val actual = pomodoroPermissions(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val post = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        assertEquals(post && notifications.areNotificationsEnabled(), actual.notifications)
        assertEquals(context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(), actual.exactAlarm)
        assertEquals(canShowPomodoroFullScreen(context), actual.fullScreen)
        val settings = pomodoroExactAlarmSettings(context)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, settings.action)
        assertEquals("package:${context.packageName}", settings.dataString)
    }
}
