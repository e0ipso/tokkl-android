// Deliberately outside the existing CI's com.github.e0ipso.tokkl.benchmark package filter.
package com.github.e0ipso.tokkl.catalogprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import com.github.e0ipso.tokkl.benchmark.TARGET_PACKAGE
import org.junit.Rule
import org.junit.Test

/** Independent of Compose's test clock. Apply these exact overlay bytes to both revisions. */
class CatalogFrameBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun tagsFirstScroll() = profile("tags", "first")
    @Test fun tagsSteadyState() = profile("tags", "steady")
    @Test fun trashFirstScroll() = profile("trash", "first")
    @Test fun trashSteadyState() = profile("trash", "steady")

    private fun profile(workload: String, phase: String) {
        val probe = CatalogProbe(workload, phase)
        try {
            rule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(FrameTimingMetric()),
                compilationMode = CompilationMode.Full(),
                startupMode = null,
                iterations = 10,
                setupBlock = { probe.prepare(this, requireNotNull(iteration)) },
            ) {
                probe.measure(requireNotNull(iteration))
            }
            probe.requireComplete()
        } finally {
            probe.close()
        }
    }
}
