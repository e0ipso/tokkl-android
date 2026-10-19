package com.github.e0ipso.tokkl

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Waits until a node with [text] exists. The first screen appears only after
 * the app has read the stored session off the main thread, which the compose
 * rule's idle check does not wait for.
 */
fun ComposeTestRule.awaitText(text: String, timeoutMillis: Long = START_TIMEOUT_MS) {
    waitUntil("\"$text\" shows", timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

/**
 * Waits until the Timer page shows, the first page of a signed-in session: the bottom bar marks
 * its Timer item as selected.
 */
fun ComposeTestRule.awaitTimerPage(timeoutMillis: Long = START_TIMEOUT_MS) {
    val timer = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.nav_timer)
    waitUntil("the Timer page shows", timeoutMillis) {
        onAllNodes(hasText(timer) and isSelected()).fetchSemanticsNodes().isNotEmpty()
    }
}

private const val START_TIMEOUT_MS = 5_000L
