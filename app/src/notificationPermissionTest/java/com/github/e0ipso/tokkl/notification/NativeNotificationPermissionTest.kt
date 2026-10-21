package com.github.e0ipso.tokkl.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timer.TimerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Each method runs alone through the script's fresh-permission fixture, on API 33 or later. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class NativeNotificationPermissionTest {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var account: TestAccounts.Account

    @Before
    fun prepareTheFirstInAppStart() {
        assertEquals("the script reset permission before starting this process", PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        // SessionRule normally suppresses this one-time prompt. Reset only this probe's record.
        assertTrue(context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE).edit().clear().commit())
        account = TestAccounts.create("Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
    }

    @After
    fun cleanUp() {
        try {
            Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
            if (::account.isInitialized) TestAccounts.delete(account)
        } finally {
            SessionRule.signOut()
        }
    }

    @Test
    fun allowingTheNativeDialogPostsTheAlreadyRunningTimer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startAndAwaitNativeDialog(scenario, "permission_allow_button").click()
            RunningNotifications.awaitShown("allowing the native dialog reposts the already running timer")
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    @Test
    fun denyingTheNativeDialogKeepsTheTimerAndOpensItsSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startAndAwaitNativeDialog(scenario, "permission_deny_button").click()
            compose.awaitText(context.getString(R.string.notification_permission_refused), TIMEOUT_MS)
            compose.onNodeWithText(context.getString(R.string.notification_permission_refused)).assertIsDisplayed()
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertNotNull(runBlocking { app.cache.running.first() })
            assertNull("denying permission hides the running notification", RunningNotifications.shown())
            compose.onNode(
                hasText(context.getString(R.string.notification_permission_settings)) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).performClick()
            compose.waitUntil("the feedback opens native notification settings", TIMEOUT_MS) {
                device.currentPackageName == "com.android.settings"
            }
            println("Native denial feedback opened ${device.currentPackageName}")
            device.pressBack()
        }
    }

    private fun startAndAwaitNativeDialog(scenario: ActivityScenario<MainActivity>, button: String): UiObject2 {
        compose.awaitTimerPage(TIMEOUT_MS)
        assertTrue("opening the app alone asks nothing", device.findObject(By.res(PERMISSION_CONTROLLER, "permission_allow_button")) == null)
        val start = hasContentDescription(context.getString(R.string.timer_start_spoken)) and hasClickAction()
        compose.waitUntil("the initial refresh enables Start", TIMEOUT_MS) {
            compose.onAllNodes(start and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(start).performClick()
        // The system question remains unanswered: starting the timer must not wait for consent.
        runBlocking { withTimeout(TIMEOUT_MS) { app.cache.running.first { it != null } } }
        scenario.onActivity { activity ->
            val starts = ViewModelProvider(activity)[TimerViewModel::class.java].model.starts.replayCache.size
            println("Native permission start: lifecycle=${activity.lifecycle.currentState}, events=$starts, permission=${activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)}, asked=${NotificationPermissionStore(activity.applicationContext).asked()}")
        }
        var nativeButton: UiObject2? = null
        // Keep Compose's test scheduler advancing while the permission coroutine returns from IO.
        // UiDevice.wait alone leaves that coroutine pending on the test clock.
        compose.waitUntil("Android shows its native notification permission dialog", TIMEOUT_MS) {
            nativeButton = device.findObject(By.res(PERMISSION_CONTROLLER, button))
            nativeButton != null
        }
        assertTrue("the launched request was persisted", NotificationPermissionStore(context).asked())
        println("Native notification permission dialog: timer running before $button on API ${Build.VERSION.SDK_INT}")
        return checkNotNull(nativeButton)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val PERMISSION_CONTROLLER = "com.android.permissioncontroller"
    }
}
