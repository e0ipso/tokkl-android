package com.github.e0ipso.tokkl.accessibility

import android.os.Bundle
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.runner.AndroidJUnitRunner
import com.github.e0ipso.tokkl.StepAwareShardingFilter
import com.github.e0ipso.tokkl.SustainedRepeat

/**
 * Keep View checks on for every Espresso action, including hosted widget Views.
 *
 * An unfiltered run leaves out [SustainedRepeat]. Those classes repeat a scenario the suite
 * already runs. A `class` or `testFile` selection is left as given, and
 * `includeSustainedRepeat=true` puts the repeats back into an unfiltered run.
 */
class AccessibilityTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val selected = !arguments.getString("class").isNullOrBlank() ||
            !arguments.getString("testFile").isNullOrBlank()
        if (!selected && arguments.getString("includeSustainedRepeat") != "true") {
            val marker = SustainedRepeat::class.java.name
            val names = arguments.getString("notAnnotation")?.split(',').orEmpty().filter { it.isNotBlank() }
            if (marker !in names) arguments.putString("notAnnotation", (names + marker).joinToString(","))
        }
        shardStepsTogether(arguments)
        super.onCreate(arguments)
    }

    /**
     * Hands `numShards` and `shardIndex` to [StepAwareShardingFilter], so the steps of a restart
     * test run on the same device in order. The runner's own sharding would scatter them.
     */
    private fun shardStepsTogether(arguments: Bundle) {
        val count = arguments.getString("numShards") ?: return
        arguments.putString(StepAwareShardingFilter.SHARD_COUNT, count)
        arguments.putString(StepAwareShardingFilter.SHARD_INDEX, arguments.getString("shardIndex"))
        arguments.remove("numShards")
        arguments.remove("shardIndex")
        val filters = arguments.getString("filter")?.split(',').orEmpty().filter { it.isNotBlank() }
        arguments.putString("filter", (filters + StepAwareShardingFilter::class.java.name).joinToString(","))
    }

    override fun onStart() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
        super.onStart()
    }
}
