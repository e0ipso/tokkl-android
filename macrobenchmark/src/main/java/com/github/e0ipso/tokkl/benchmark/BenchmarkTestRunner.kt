package com.github.e0ipso.tokkl.benchmark

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runs the benchmarks from the list in `tokklBenchmarkTestFile` when one is given.
 *
 * Every runner argument reaches both test APKs. scripts/instrumented-tests.sh puts the app's
 * tests of a `class=` list in `testFile` and the benchmarks in `tokklBenchmarkTestFile`, because
 * this APK cannot load the app's test classes and would fail the run on them.
 */
class BenchmarkTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        arguments.getString(BENCHMARK_TEST_FILE)?.takeIf { it.isNotBlank() }?.let { arguments.putString("testFile", it) }
        super.onCreate(arguments)
    }

    private companion object {
        const val BENCHMARK_TEST_FILE = "tokklBenchmarkTestFile"
    }
}
