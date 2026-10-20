package com.github.e0ipso.tokkl.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
            checkNotNull(device.wait(Until.findObject(startButton().enabled(true)), UI_TIMEOUT_MS)) { "Start never became enabled" }
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
        repeat(STOP_TAPS) {
            device.findObject(stopButton())?.click()
            if (device.wait(Until.hasObject(startButton()), STOP_WAIT_MS)) return
        }
        error("$STOP_TAPS Stop taps did not show the idle timer")
    }

    private companion object {
        /** TapTrace.START and TapTrace.STOP in the app. */
        const val START_SECTION = "TimerStart"
        const val STOP_SECTION = "TimerStop"
        const val STOP_TAPS = 5
        const val STOP_WAIT_MS = 2_000L
    }
}
