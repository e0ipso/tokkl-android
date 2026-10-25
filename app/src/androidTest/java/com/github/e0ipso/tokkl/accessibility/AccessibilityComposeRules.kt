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
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator

/**
 * Audit tests use the same v2 factories as other tests, with the maintained ATF integration.
 * Compose's integration requires API 34. API 31 still runs the original fit/semantics assertions
 * and Espresso View checks; it must not be reported as a Compose ATF pass.
 * Timing tests keep their existing factories, so ATF work is outside their measured actions.
 */
inline fun <reified A : ComponentActivity> createAndroidComposeRule() =
    androidComposeRule<A>().also { it.enableTokklAccessibilityChecks() }

/**
 * [scaledSizes] is for content drawn at a density below the device's, as
 * `DeviceConfigurationOverride.WindowSize` draws a window larger than the phone. ATF measures
 * touch targets in the device's pixels, so there a 48dp target reads smaller than it is. Such a
 * rule leaves out only the touch-target check; every other check still runs.
 */
fun createComposeRule(scaledSizes: Boolean = false) = composeRule().also { it.enableTokklAccessibilityChecks(scaledSizes) }

fun createEmptyComposeRule() = emptyComposeRule().also { it.enableTokklAccessibilityChecks() }

fun ComposeTestRule.enableTokklAccessibilityChecks(scaledSizes: Boolean = false) {
    if (Build.VERSION.SDK_INT >= 34) {
        val validator = if (scaledSizes) {
            // A validator of its own, so the shared one that Espresso's View checks use keeps
            // the touch-target check.
            AccessibilityValidator().setRunChecksFromRootView(true)
                .setSuppressingResultMatcher(matchesCheck(TouchTargetSizeCheck::class.java))
        } else {
            AccessibilityChecks.enable().setRunChecksFromRootView(true)
        }
        enableAccessibilityChecks(validator)
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
