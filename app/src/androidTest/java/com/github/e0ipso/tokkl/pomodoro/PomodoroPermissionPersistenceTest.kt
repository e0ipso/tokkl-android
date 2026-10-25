package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.NotificationPermissionStore
import org.junit.After
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

}
