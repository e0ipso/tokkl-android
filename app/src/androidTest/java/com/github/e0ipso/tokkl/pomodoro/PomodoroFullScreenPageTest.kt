package com.github.e0ipso.tokkl.pomodoro

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Exercises the actual page's resume observer and settings launch, without navigation changes. */
@SignedOut
class PomodoroFullScreenPageTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as TokklApplication
    private val device = UiDevice.getInstance(instrumentation)
    private val scope = MainScope()
    private var originalMode: String? = null

    @After fun restoreAccessAndCollectors() {
        try { originalMode?.let { DeviceShell.run("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT $it") } }
        finally { scope.cancel() }
    }

    @Test @SdkSuppress(minSdkVersion = 34)
    fun deniedNoticeOpensAppSettingsAndDisappearsOnResumeAfterGrant() {
        val output = DeviceShell.run("cmd appops get ${context.packageName} USE_FULL_SCREEN_INTENT").trim()
        originalMode = if (output.startsWith("No operations")) "default" else {
            Regex("USE_FULL_SCREEN_INTENT: (allow|deny|ignore|default|foreground)").find(output)?.groupValues?.get(1)
                ?: error("Cannot restore full-screen app-op: $output")
        }
        DeviceShell.run("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT deny")
        val model = PomodoroScreenModel(app.pomodoroSessions, app.pomodoroDefaults.defaults,
            app.accountSettings.state, app.changes.unsentCount, app.changes.held, scope)
        compose.activityRule.scenario.onActivity { WindowCompat.enableEdgeToEdge(it.window) }
        compose.setContent { TokklTheme { PomodoroScreen(model, app.cache) } }
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_denied)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_settings)).performScrollTo().performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10_000))
        DeviceShell.run("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT allow")
        device.pressBack()
        compose.waitUntil(10_000) { !model.state.value.fullScreenDenied }
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_denied)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_settings)).assertDoesNotExist()
    }

    @Test @SdkSuppress(maxSdkVersion = 33)
    fun olderAndroidShowsThePageWithoutAnUnsupportedFullScreenSettingsButton() {
        assertTrue(Build.VERSION.SDK_INT in 31..33)
        val model = PomodoroScreenModel(app.pomodoroSessions, app.pomodoroDefaults.defaults,
            app.accountSettings.state, app.changes.unsentCount, app.changes.held, scope)
        compose.setContent { TokklTheme { PomodoroScreen(model, app.cache) } }
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_denied)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.pomodoro_fullscreen_settings)).assertDoesNotExist()
    }
}
