package com.github.e0ipso.tokkl.benchmark

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.waitForStable
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tap-to-state on the Timer page (docs/PRD.md, Speed): the app's TimerStart and TimerStop trace
 * sections, TapTrace in the app, run from a Start or Stop tap until the screen has drawn what the
 * tap did. The goal is 200 ms on a mid-range phone. CI compares each run with the last green run
 * on main.
 */
@OptIn(ExperimentalMetricApi::class)
class TimerTapBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun allowTimerNotifications() {
        // The first Start requests notification permission on API 33+. Its system dialog
        // covers the timer controls, so grant it before measuring Start and Stop.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(TARGET_PACKAGE, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun startAndStop() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric(START_SECTION, TraceSectionMetric.Mode.First),
            TraceSectionMetric(STOP_SECTION, TraceSectionMetric.Mode.First),
        ),
        compilationMode = CompilationMode.Full(),
        // The app keeps running between taps, as it does in use.
        startupMode = null,
        iterations = TAP_ITERATIONS,
        setupBlock = {
            if (!device.hasObject(By.pkg(TARGET_PACKAGE).depth(0))) startActivityAndWait()
            device.awaitTimerPage()
            if (device.hasObject(stopButton())) stopTimer()
            val start = checkNotNull(device.wait(Until.findObject(startButton().enabled(true)), UI_TIMEOUT_MS)) { "Start never became enabled" }
            check(!start.waitForStable(requireStableScreenshot = false).isTimeout) { "Start button never settled" }
        },
    ) {
        device.findObject(startButton()).click()
        checkNotNull(device.wait(Until.findObject(stopButton()), UI_TIMEOUT_MS)) { "Start did not show the running timer" }
        stopTimer()
    }

    /**
     * Taps Stop until the idle timer shows. The timer ignores a Stop tap until the phone's
     * database has the entry that Start started, which the screen does not show, and an ignored
     * tap opens no trace section.
     */
    private fun MacrobenchmarkScope.stopTimer() {
        try {
            repeat(STOP_TAPS) {
                device.findObject(stopButton())?.let { stop ->
                    // Queuing/sending Start changes the Unsent label and can wrap this row.
                    // UiAutomator injects a coordinate tap, so the node must stop moving first.
                    // This precedes the tap and its TimerStop trace, leaving that metric intact.
                    check(!stop.waitForStable(requireStableScreenshot = false).isTimeout) { "Stop button never settled" }
                    stop.click()
                }
                if (device.wait(Until.hasObject(startButton()), STOP_WAIT_MS)) return
            }
            error("$STOP_TAPS Stop taps did not show the idle timer")
        } catch (failure: Throwable) {
            // Capture before Macrobenchmark tears down the target app. A JUnit watcher would
            // otherwise capture the launcher instead of the screen that lost the controls.
            runCatching {
                val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                    ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
                directory.mkdirs()
                device.dumpWindowHierarchy(File(directory, "timer-stop-failure.xml"))
                device.takeScreenshot(File(directory, "timer-stop-failure.png"))
            }.onFailure { Log.w("TimerTapBenchmark", "Could not capture the failed timer screen", it) }
            throw failure
        }
    }

    private companion object {
        /** TapTrace.START and TapTrace.STOP in the app. */
        const val START_SECTION = "TimerStart"
        const val STOP_SECTION = "TimerStop"
        const val STOP_TAPS = 5
        const val STOP_WAIT_MS = 2_000L
    }
}
