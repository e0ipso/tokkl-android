package com.github.e0ipso.tokkl.accessibility

import android.os.Bundle
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.runner.AndroidJUnitRunner
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
        super.onCreate(arguments)
    }

    override fun onStart() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
        super.onStart()
    }
}
