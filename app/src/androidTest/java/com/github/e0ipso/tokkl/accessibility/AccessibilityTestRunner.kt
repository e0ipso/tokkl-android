package com.github.e0ipso.tokkl.accessibility

import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.runner.AndroidJUnitRunner

/** Keep View checks on for every Espresso action, including hosted widget Views. */
class AccessibilityTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
        super.onStart()
    }
}
