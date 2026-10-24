package com.github.e0ipso.tokkl.pomodoro

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.WindowCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.NotificationPermissionPrompt
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Uses the same shared prompt as navigation, with Android's result registry held unanswered. */
@SignedOut
class PomodoroPermissionPromptTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val starts = Channel<Unit>(Channel.UNLIMITED)
    private val retries = Channel<Unit>(Channel.UNLIMITED)
    private val startFlow = starts.receiveAsFlow()
    private val retryFlow = retries.receiveAsFlow()
    private val requests = CopyOnWriteArrayList<Int>()
    private var settings = 0
    private var started = 0
    private var exactSettings = 0
    private var fullSettings = 0
    private var asked = false
    private var page by mutableStateOf(PomodoroPageState(
        loading = false,
        account = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, null, defaultWorkspaceId = 11),
        notificationsDenied = true, alarmDenied = true, fullScreenDenied = true,
    ))
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            requests += requestCode
        }
    }
    private val record = object : NotificationPermissionAsk.AskRecord {
        override fun asked() = asked
        override fun markAsked() { asked = true }
    }

    private fun show(granted: Boolean = false) {
        val ask = NotificationPermissionAsk(Build.VERSION.SDK_INT, record) { granted }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.activityRule.scenario.onActivity { WindowCompat.enableEdgeToEdge(it.window) }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TokklTheme {
                    Box {
                        PomodoroContent(page, {}, {}, onStart = { started++; starts.trySend(Unit) },
                            onNext = {}, onEnd = {}, onReload = {},
                            onNotificationRetry = { retries.trySend(Unit) },
                            onExactAlarmSettings = { exactSettings++ }, onFullScreenSettings = { fullSettings++ })
                        NotificationPermissionPrompt(startFlow, ask, retries = retryFlow,
                            openSettings = { settings++ }, onResult = { allowed -> page = page.copy(notificationsDenied = !allowed) })
                    }
                }
            }
        }
    }

    private fun tap(id: Int) = compose.onNodeWithText(context.getString(id)).performScrollTo().performClick()

    @Test @SdkSuppress(minSdkVersion = 33)
    fun firstStartIsImmediateAndOutstandingRequestsCannotCauseDuplicateAutomaticDialogs() {
        show()
        assertEquals(0, requests.size)
        tap(R.string.pomodoro_start)
        compose.waitUntil(5_000) { requests.size == 1 && asked }
        assertEquals(1, started)
        // Keep the platform dialog outstanding while another start event arrives.
        tap(R.string.pomodoro_start)
        compose.waitForIdle()
        assertEquals(2, started)
        assertEquals(1, requests.size)
        compose.runOnUiThread { registry.dispatchResult(requests.single(), false) }
        compose.onNodeWithText(context.getString(R.string.pomodoro_notifications_denied)).performScrollTo().assertIsDisplayed()
        tap(R.string.pomodoro_start)
        compose.waitForIdle()
        assertEquals(1, requests.size)
        tap(R.string.pomodoro_notifications_settings)
        compose.waitUntil(5_000) { settings == 1 }
        // Settings is still outstanding; a new session start still runs immediately.
        tap(R.string.pomodoro_start)
        assertEquals(4, started)
        assertEquals(1, requests.size)
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    fun explicitRetryBeforeAnyStartRequestsAndGrantRemovesThePersistentNotice() {
        show()
        tap(R.string.pomodoro_notifications_settings)
        compose.waitUntil(5_000) { requests.size == 1 && asked }
        assertEquals(0, started)
        compose.runOnUiThread { registry.dispatchResult(requests.single(), true) }
        compose.onNodeWithText(context.getString(R.string.pomodoro_notifications_denied)).assertDoesNotExist()
        tap(R.string.pomodoro_start)
        compose.waitForIdle()
        assertEquals(1, requests.size)
    }

    @Test fun exactAndFullScreenSettingsNeverDisableStartAndNoticesRemainUntilGrant() {
        show(granted = true)
        tap(R.string.pomodoro_exact_alarm_settings)
        tap(R.string.pomodoro_fullscreen_settings)
        tap(R.string.pomodoro_start)
        assertEquals(1, started)
        assertEquals(1, exactSettings)
        assertEquals(1, fullSettings)
        compose.waitForIdle()
        assertTrue(requests.isEmpty())
        compose.onNodeWithText(context.getString(R.string.pomodoro_alarm_denied)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_denied)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { page = page.copy(alarmDenied = false, fullScreenDenied = false, notificationsDenied = false) }
        compose.onNodeWithText(context.getString(R.string.pomodoro_exact_alarm_settings)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_settings)).assertDoesNotExist()
    }

    @Test @SdkSuppress(maxSdkVersion = 32)
    fun android12StartsWithoutPostNotificationRequestAndRetryOpensSettings() {
        show()
        tap(R.string.pomodoro_start)
        tap(R.string.pomodoro_notifications_settings)
        compose.waitUntil(5_000) { settings == 1 }
        assertTrue(requests.isEmpty())
        assertEquals(1, started)
    }
}
