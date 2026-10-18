package com.github.e0ipso.tokkl.settings

import android.app.Activity
import android.os.Process
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Each theme mode survives a restart of the app process. The steps run in name order, and the
 * test orchestrator (app/build.gradle.kts) starts a new process for each. Every step after the
 * first starts the app, checks the colours and the choice the step before left, and picks the
 * next mode. A step fails if it runs in the same process as the one before.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ThemeSettingsRestartTest {
    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Names the process that ran the step before. */
    private val previousStep = File(context.cacheDir, "ThemeSettingsRestartTest.process")

    @Test
    fun step1ChoosesLight() {
        ThemeSettingsStore(context).clear()
        previousStep.delete()
        launch { activity ->
            choose(ThemeMode.LIGHT)
            assertEquals(tokklTheme(dark = false), composeRule.shownTheme(activity))
        }
        endStep(ThemeMode.LIGHT)
    }

    @Test
    fun step2KeepsLightAndChoosesDark() {
        assertNewProcess()
        launch { activity ->
            assertEquals("on start", tokklTheme(dark = false), composeRule.shownTheme(activity))
            composeRule.openSettings()
            mode(ThemeMode.LIGHT).assertIsSelected()
            choose(ThemeMode.DARK)
        }
        endStep(ThemeMode.DARK)
    }

    @Test
    fun step3KeepsDarkAndChoosesTheSystem() {
        assertNewProcess()
        launch { activity ->
            assertEquals("on start", tokklTheme(dark = true), composeRule.shownTheme(activity))
            composeRule.openSettings()
            mode(ThemeMode.DARK).assertIsSelected()
            choose(ThemeMode.SYSTEM)
        }
        endStep(ThemeMode.SYSTEM)
    }

    @Test
    fun step4KeepsFollowingTheSystem() {
        try {
            assertNewProcess()
            launch { activity ->
                assertEquals("on start", tokklTheme(dark = systemIsDark(activity)), composeRule.shownTheme(activity))
                composeRule.openSettings()
                mode(ThemeMode.SYSTEM).assertIsSelected()
            }
        } finally {
            ThemeSettingsStore(context).clear()
            followTheSystemNightMode()
            previousStep.delete()
        }
    }

    /** Starts the app on the launch screen and runs [steps] with its activity. */
    private fun launch(steps: (Activity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.awaitText(string(R.string.launch_message))
            lateinit var activity: Activity
            scenario.onActivity { activity = it }
            steps(activity)
        }
    }

    /** Picks [mode], opening the settings screen first if needed. */
    private fun choose(mode: ThemeMode) {
        if (composeRule.onAllNodes(hasText(string(R.string.settings_theme))).fetchSemanticsNodes().isEmpty()) {
            composeRule.openSettings()
        }
        mode(mode).performClick().assertIsSelected()
    }

    private fun mode(mode: ThemeMode) = composeRule.onNode(hasText(string(THEME_MODE_LABELS.getValue(mode))) and isSelectable())

    /** Waits for [chosen] to reach the disk, then names this process for the next step. */
    private fun endStep(chosen: ThemeMode) {
        awaitModeOnDisk(chosen)
        previousStep.writeText(currentProcess())
    }

    private fun assertNewProcess() {
        assertTrue("the step before did not run", previousStep.exists())
        assertNotEquals("the step before ran in this same process", previousStep.readText(), currentProcess())
    }

    /** A PID can be reused, so the process start time goes with it. */
    private fun currentProcess(): String = "${Process.myPid()}@${Process.getStartElapsedRealtime()}"

    private fun tokklTheme(dark: Boolean): ShownTheme =
        expectedTheme(if (dark) TokklDarkColorScheme.background else TokklLightColorScheme.background, dark)
}
