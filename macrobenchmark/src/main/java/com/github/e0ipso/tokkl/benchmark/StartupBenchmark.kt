package com.github.e0ipso.tokkl.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * Cold start of the signed-in app to the Timer page (docs/PRD.md, Speed). The goal is 1.5 s on a
 * mid-range phone. The emulator is no such phone, so CI compares each run with the last green run
 * on main instead.
 */
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        // Compiled ahead of time, as the app runs once the phone has optimized it, which varies
        // less between runs than the JIT does.
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = START_ITERATIONS,
        setupBlock = {
            // Each start measured is of the signed-in app. Macrobenchmark kills the process
            // after this block.
            startActivityAndWait()
            device.awaitTimerPage()
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.awaitTimerPage()
    }
}
