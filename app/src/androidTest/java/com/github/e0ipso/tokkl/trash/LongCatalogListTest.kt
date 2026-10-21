package com.github.e0ipso.tokkl.trash

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.tags.Tag
import com.github.e0ipso.tokkl.tags.TagsActions
import com.github.e0ipso.tokkl.tags.TagsContent
import com.github.e0ipso.tokkl.tags.TagsData
import com.github.e0ipso.tokkl.tags.TagsServer
import com.github.e0ipso.tokkl.tags.TagsState
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Repeatable on-device opening/fling measurements, excluding network and fixture creation. */
class LongCatalogListTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)

    @Test
    fun oneThousandTrashEntries() = withAccount { account ->
        val start = Instant.parse("2026-10-19T08:00:00Z")
        repeat(1000) { index ->
            account.createEntry("Measured trash $index", start, start.plusSeconds(60)).also(account::deleteEntry)
        }
        val entries = TrashServer(account.client).list().orFail("list measurement trash")
        assertEquals(1000, entries.size)
        measure("trash", "Measured trash", entries.size, entries.last().description!!) {
            TrashContent(TrashState(entries = entries), false, null, TrashActions())
        }
    }

    @Test
    fun fiveHundredTags() = withAccount { account ->
        val server = TagsServer(account.client, account.workspaceId)
        repeat(500) { index -> server.create("Measured tag %03d".format(index), emptySet()).orFail("create measurement tag") }
        val tags: List<Tag> = server.list().orFail("list measurement tags")
        assertEquals(500, tags.size)
        measure("tags", "Measured tag", tags.size, tags.last().name) {
            TagsContent(TagsState(data = TagsData(tags)), false, emptyList(), TagsActions())
        }
    }

    private fun withAccount(block: (TestAccounts.Account) -> Unit) {
        val account = TestAccounts.create("Europe/Madrid")
        try {
            block(account)
        } finally {
            TestAccounts.delete(account)
            report("fixture account ${account.id} deleted with its workspace and all rows")
        }
    }

    private fun measure(page: String, prefix: String, totalRows: Int, lastName: String, content: @Composable () -> Unit) {
        val generation = mutableIntStateOf(-1)
        compose.activityRule.scenario.onActivity { WindowCompat.enableEdgeToEdge(it.window) }
        compose.setContent {
            TokklTheme(darkTheme = false) {
                if (generation.intValue >= 0) key(generation.intValue) { content() }
            }
        }
        val thread = HandlerThread("catalog-frame-metrics").apply { start() }
        val frames = CopyOnWriteArrayList<Double>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
        }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        try {
            repeat(3) { iteration ->
                frames.clear()
                val openStart = SystemClock.elapsedRealtimeNanos()
                compose.runOnUiThread { generation.intValue = iteration }
                compose.waitForIdle()
                val openMs = (SystemClock.elapsedRealtimeNanos() - openStart) / 1_000_000.0
                val composedRows = compose.onAllNodes(SemanticsMatcher("row text") {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.startsWith(prefix) } == true
                }, useUnmergedTree = true).fetchSemanticsNodes().size
                report("$page iteration=$iteration rows=$totalRows openMs=$openMs composedRows=$composedRows openFrames=${summary(frames.toList())}")
                assertTrue("only nearby rows enter the semantics tree, not all $totalRows", composedRows in 1 until totalRows / 10)
                frames.clear()
                val scrollStart = SystemClock.elapsedRealtimeNanos()
                repeat(5) { compose.onNode(hasScrollAction()).performTouchInput { swipeUp(durationMillis = 150) } }
                compose.waitForIdle()
                val scrollMs = (SystemClock.elapsedRealtimeNanos() - scrollStart) / 1_000_000.0
                report("$page iteration=$iteration fiveFlingsMs=$scrollMs flingFrames=${summary(frames.toList())}")
                assertTrue("the page emitted frame timings", frames.isNotEmpty())
            }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(lastName, substring = true))
            compose.onNode(hasText(lastName, substring = true)).assertIsDisplayed()
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
            thread.join()
        }
    }

    private fun summary(frames: List<Double>): String {
        val sorted = frames.sorted()
        if (sorted.isEmpty()) return "none"
        return "count=${sorted.size},p50Ms=${sorted[sorted.size / 2]},p95Ms=${sorted[((sorted.size - 1) * 0.95).toInt()]},maxMs=${sorted.last()}"
    }

    private fun report(message: String) {
        android.util.Log.i("LongCatalogList", message)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "LongCatalogList: $message\n") })
    }
}
