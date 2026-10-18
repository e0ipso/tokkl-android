package com.github.e0ipso.tokkl

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText

/**
 * Waits until a node with [text] exists. The first screen appears only after
 * the app has read the stored session off the main thread, which the compose
 * rule's idle check does not wait for.
 */
fun ComposeTestRule.awaitText(text: String, timeoutMillis: Long = START_TIMEOUT_MS) {
    waitUntil("\"$text\" shows", timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

private const val START_TIMEOUT_MS = 5_000L
