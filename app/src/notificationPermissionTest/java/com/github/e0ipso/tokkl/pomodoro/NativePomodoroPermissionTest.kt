package com.github.e0ipso.tokkl.pomodoro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.notification.NotificationPermissionStore
import com.github.e0ipso.tokkl.openBarPage
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Each method needs the runner's fresh POST_NOTIFICATIONS fixture in a separate process.
 * The integration gate must extend its native-permission method allowlist before execution.
 */
@SdkSuppress(minSdkVersion = 33)
class NativePomodoroPermissionTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var account: TestAccounts.Account

    @Before fun prepareFreshPermissionAndDisposableAccount() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE).edit().clear().commit())
        account = TestAccounts.create("Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
    }

    @After fun removeFixtures() {
        try {
            Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
            if (::account.isInitialized) TestAccounts.delete(account)
        } finally { SessionRule.signOut() }
    }

    @Test fun grantingTheFirstSessionDialogKeepsTheSameStartedSession() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAndWaitForDialog()
            val started = checkNotNull(session())
            device.findObject(By.res(CONTROLLER, "permission_allow_button")).click()
            compose.waitUntil(15_000) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            assertEquals(started, session())
            compose.awaitText(context.getString(R.string.pomodoro_end), 15_000)
            compose.onNodeWithText(context.getString(R.string.pomodoro_notifications_denied)).assertDoesNotExist()
        }
    }

    @Test fun denyingThenRecreatingKeepsPromptsAndLaterStartsAskOnlyOnExplicitRetry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startAndWaitForDialog()
            device.findObject(By.res(CONTROLLER, "permission_deny_button")).click()
            compose.awaitText(context.getString(R.string.pomodoro_notifications_denied), 15_000)
            assertNotNull(session())
            scenario.recreate()
            compose.awaitText(context.getString(R.string.pomodoro_end), 15_000)
            // The refusal's snackbar comes back after recreation, for Material's long duration.
            waitOutSnackbar(R.string.notification_permission_refused)
            tap(R.string.pomodoro_end)
            compose.waitUntil(15_000) { session() == null }
            // Shorter than half the test server's 5-minute resolution, the pomodoro went to the trash.
            waitOutSnackbar(R.string.timer_pomodoro_too_short)
            tap(R.string.pomodoro_start)
            compose.waitUntil(15_000) { session() != null }
            assertNull(device.findObject(By.res(CONTROLLER, "permission_allow_button")))
            assertTrue(NotificationPermissionStore(context).asked())
            tap(R.string.pomodoro_notifications_settings)
            compose.waitUntil(15_000) { device.currentPackageName == "com.android.settings" }
            assertNotNull("settings never ends the running session", session())
            device.pressBack()
        }
    }

    private fun startAndWaitForDialog() {
        compose.awaitTimerPage(15_000)
        compose.openBarPage(R.string.pomodoro_title)
        compose.awaitText(context.getString(R.string.pomodoro_start), 15_000)
        tap(R.string.pomodoro_start)
        compose.waitUntil(15_000) { device.findObject(By.res(CONTROLLER, "permission_allow_button")) != null }
        // The native dialog is deliberately unanswered while the server's session arrives.
        compose.waitUntil(15_000) { session() != null }
        compose.waitUntil(15_000) { NotificationPermissionStore(context).asked() }
    }

    /** The server's session as the phone holds it, or null without one. */
    private fun session(): TimerPomodoro? = app.timerState.state.value?.state?.pomodoro

    /**
     * Waits for the snackbar that says [id], then for it to go. It shows at the bottom of the page,
     * where performScrollTo leaves the page's last button, and would take that button's touch.
     */
    private fun waitOutSnackbar(id: Int) {
        val text = context.getString(id)
        compose.awaitText(text, 15_000)
        compose.waitUntil("\"$text\" goes", 15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    private fun tap(id: Int) = compose.onNodeWithText(context.getString(id)).performScrollTo().performClick()
    private companion object { const val CONTROLLER = "com.android.permissioncontroller" }
}
