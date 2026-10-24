package com.github.e0ipso.tokkl.accessibility

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule as androidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule as composeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule as emptyComposeRule
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.espresso.accessibility.AccessibilityChecks

/**
 * Audit tests use the same v2 factories as other tests, with the maintained ATF integration.
 * Compose's integration requires API 34. API 31 still runs the original fit/semantics assertions
 * and Espresso View checks; it must not be reported as a Compose ATF pass.
 * Timing tests keep their existing factories, so ATF work is outside their measured actions.
 */
inline fun <reified A : ComponentActivity> createAndroidComposeRule() =
    androidComposeRule<A>().also { it.enableTokklAccessibilityChecks() }

fun createComposeRule() = composeRule().also { it.enableTokklAccessibilityChecks() }

fun createEmptyComposeRule() = emptyComposeRule().also { it.enableTokklAccessibilityChecks() }

fun ComposeTestRule.enableTokklAccessibilityChecks() {
    if (Build.VERSION.SDK_INT >= 34) {
        enableAccessibilityChecks(AccessibilityChecks.enable().setRunChecksFromRootView(true))
    }
}

/** Explicitly check static and final states too; an assertion alone does not run ATF. */
fun ComposeTestRule.checkAccessibility() {
    if (Build.VERSION.SDK_INT >= 34) {
        val roots = onAllNodes(isRoot())
        check(roots.fetchSemanticsNodes().isNotEmpty()) { "the accessibility audit has no UI roots" }
        for (index in roots.fetchSemanticsNodes().indices) {
            roots[index].tryPerformAccessibilityChecks()
        }
    }
}
