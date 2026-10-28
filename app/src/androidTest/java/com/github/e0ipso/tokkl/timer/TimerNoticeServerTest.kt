package com.github.e0ipso.tokkl.timer

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.timerstate.TimerState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** The timer's notices reach the screen through the one path the timer's pages share. */
class TimerNoticeServerTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val app get() = fixture.app
    private val context get() = fixture.context

    @Before fun prepare() = fixture.prepare()
    @After fun cleanup() = fixture.cleanup()

    @Test
    fun aTooShortPomodoroIsSaidOnceAndNotAgainWhenItsStateArrivesAgain() {
        // Another client starts a session and ends it at once. The server discards the pomodoro,
        // which rounds to nothing at any time resolution, and says so in the state.
        val client = fixture.account.client
        val started = client.send(
            "POST", client.server.app("pomodoro"),
            buildJsonObject {
                put("workspace_id", fixture.account.workspaceId)
                put("description", "Too short")
            },
            TimerState.serializer(),
        ).orFail("POST /api/app/pomodoro")
        val ended = client.send(
            "POST", client.server.app("pomodoro", "end"), buildJsonObject { put("revision", started.revision) }, TimerState.serializer(),
        ).orFail("POST /api/app/pomodoro/end")
        val discarded = ended.discardedPomodoro
        assertNotNull("the server discarded the pomodoro", discarded)
        fixture.refresh()
        assertEquals(discarded, app.timerState.discardNotice.value)

        val text = context.getString(R.string.timer_pomodoro_too_short)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            compose.waitUntil("the notice shows", 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
            assertNull("the holder recorded it as shown", app.timerState.discardNotice.value)
            compose.waitUntil("the notice goes", 15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
        }
        // A new activity and view model, and the state at that revision read again.
        fixture.refresh()
        assertEquals("the server repeats it", discarded, app.timerState.state.value!!.state.discardedPomodoro)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            compose.waitForIdle()
            assertTrue("it is not said again", compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
        }
    }
}
