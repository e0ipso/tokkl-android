package com.github.e0ipso.tokkl

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

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

/**
 * The bottom bar's item named [label], found by its tab role. Text and a click action alone also
 * match every cached history row whose description is that text, since each row is clickable.
 */
fun bottomBarItem(@StringRes label: Int): SemanticsMatcher {
    val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(label)
    return hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
}

/** Waits until the bottom bar shows its item named [label], then taps it. */
fun ComposeTestRule.openBarPage(@StringRes label: Int, timeoutMillis: Long = START_TIMEOUT_MS) {
    val item = bottomBarItem(label)
    waitUntil("the bar item shows", timeoutMillis) { onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
    onNode(item).performClick()
}

/**
 * Runs [block] on a thread of its own and fails when it has not returned after [timeoutMillis].
 * ActivityScenario's close() and onActivity() start with Instrumentation.waitForIdleSync(), which
 * has no timeout, so a main thread that never goes idle would hang the whole run instead of
 * failing one test. The thread of a block that timed out stays blocked until the test process ends.
 */
fun <T> finishWithin(what: String, timeoutMillis: Long = SCENARIO_TIMEOUT_MS, block: () -> T): T {
    val task = FutureTask(block)
    thread(isDaemon = true, name = "finish-within") { task.run() }
    return try {
        task.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        throw AssertionError("$what took more than $timeoutMillis ms; the app's main thread never went idle", e)
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
}

private const val START_TIMEOUT_MS = 5_000L

/** ActivityScenario's own limit for an activity to reach a lifecycle state. */
private const val SCENARIO_TIMEOUT_MS = 45_000L
