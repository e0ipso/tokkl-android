package com.github.e0ipso.tokkl.pomodoro

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.Destination
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedInPanes
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.timer.TimerAcceptanceFixture
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerState
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
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

/**
 * The Pomodoro page against the real Tokkl server, which owns the session: Start, Next phase and
 * End go to it and the page shows what it answers, edits of the open pomodoro go through the
 * queue, and a tap drawn before another device moved the timer on is refused.
 */
class PomodoroPageServerTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val fixture = TimerAcceptanceFixture()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = fixture.app
    private lateinit var model: PomodoroScreenModel

    @Before
    fun prepareAnAccountWithItsOwnDefaults() {
        fixture.prepare()
        val client = fixture.account.client
        client.send("POST", client.server.api("me", "preferences"), buildJsonObject {
            put("pomodoro_focus_interval_in_minutes", 40)
            put("pomodoro_break_interval_in_minutes", 8)
            put("pomodoro_long_break_interval_in_minutes", 20)
            put("pomodoro_long_break_every", 3)
        }, JsonObject.serializer()).orFail("set Pomodoro defaults")
        fixture.refresh()
    }

    @After
    fun cleanup() {
        try { AirplaneMode.off() } finally { fixture.cleanup() }
    }

    @Test
    fun theFormSendsItsLengthsAndFieldsAndNextPhaseAndEndFollowTheServer() {
        show()
        field(R.string.pomodoro_work_minutes).assert(hasText("40"))
        field(R.string.pomodoro_short_rest_minutes).assert(hasText("8"))
        field(R.string.pomodoro_long_rest_minutes).assert(hasText("20"))
        field(R.string.pomodoro_long_rest_every).assert(hasText("3"))
        type(R.string.pomodoro_work_minutes, "30")
        type(R.string.pomodoro_short_rest_minutes, "5")
        type(R.string.pomodoro_long_rest_minutes, "12")
        type(R.string.pomodoro_long_rest_every, "2")
        type(R.string.entry_description, "First work")
        tap(R.string.pomodoro_start)
        await { model.state.value.pomodoro != null && !model.state.value.busy }

        val started = serverTimer()
        val session = started.pomodoro!!
        assertEquals(listOf(30, 5, 12, 2), listOf(session.workMinutes, session.shortRestMinutes, session.longRestMinutes, session.longRestEvery))
        assertEquals("First work", started.runningEntry!!.description)
        assertEquals("the page holds the server's state", started.revision, model.state.value.held!!.state.revision)
        compose.onNode(hasText(context.getString(R.string.pomodoro_work)) and isHeading()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pomodoro_number, 1, 2)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pomodoro_up_next, context.getString(R.string.pomodoro_short_rest)))
            .performScrollTo().assertIsDisplayed()

        tap(R.string.pomodoro_start_short_rest)
        await { model.state.value.pomodoro?.phase?.kind == PhaseKind.SHORT_REST && !model.state.value.busy }
        assertEquals(PhaseKind.SHORT_REST, serverTimer().pomodoro!!.phase.kind)
        compose.onNode(hasText(context.getString(R.string.pomodoro_short_rest)) and isHeading()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pomodoro_number, 1, 2)).performScrollTo().assertIsDisplayed()

        tap(R.string.pomodoro_end)
        await { model.state.value.pomodoro == null && !model.state.value.busy }
        assertNull(serverTimer().pomodoro)
        // The form is back, on the account's defaults again.
        field(R.string.pomodoro_work_minutes).performScrollTo().assert(hasText("40"))
    }

    @Test
    fun aTapDrawnBeforeAnotherDeviceMovedTheTimerOnIsRefusedAndTheServersStateShows() {
        show()
        tap(R.string.pomodoro_start)
        await { model.state.value.pomodoro != null && !model.state.value.busy }
        val drawn = model.state.value.shown!!
        // Another device confirms the phase first.
        val client = fixture.account.client
        client.send("POST", client.server.app("pomodoro", "next"), buildJsonObject { put("revision", drawn.revision) }, TimerState.serializer())
            .orFail("POST /api/app/pomodoro/next")

        compose.runOnIdle { model.next(drawn) }
        await { model.state.value.problem != null && !model.state.value.busy }
        assertEquals(PomodoroProblem(PomodoroAction.NEXT, TimerActionResult.Refused), model.state.value.problem)
        compose.onNodeWithText(context.getString(TimerActionResult.Refused.message)).performScrollTo().assertIsDisplayed()
        // The refusal carried the server's state, and the old tap advanced nothing a second time.
        assertEquals(PhaseKind.SHORT_REST, model.state.value.pomodoro!!.phase.kind)
        val server = serverTimer()
        assertEquals(PhaseKind.SHORT_REST, server.pomodoro!!.phase.kind)
        assertEquals(drawn.revision + 1, server.revision)
    }

    @Test
    fun aDescriptionTypedJustBeforeNextPhaseReachesTheServerAndCarriesToTheNextPhase() {
        show()
        type(R.string.entry_description, "Before")
        tap(R.string.pomodoro_start)
        await { model.state.value.pomodoro != null && !model.state.value.busy }

        type(R.string.entry_description, "After the edit")
        tap(R.string.pomodoro_start_short_rest)
        await { model.state.value.pomodoro?.phase?.kind == PhaseKind.SHORT_REST && !model.state.value.busy }
        assertNull("the user's own edit is not another device's change", model.state.value.problem)
        val server = serverTimer()
        assertEquals(PhaseKind.SHORT_REST, server.pomodoro!!.phase.kind)
        assertEquals("After the edit", server.runningEntry!!.description)
        compose.onNode(hasText(context.getString(R.string.entry_description)) and hasSetTextAction())
            .performScrollTo().assert(hasText("After the edit"))
    }

    @Test
    fun billableChangedOnThePageUpdatesTheServersRunningEntry() {
        show()
        tap(R.string.pomodoro_start)
        await { model.state.value.pomodoro != null && !model.state.value.busy }
        assertTrue(serverTimer().runningEntry!!.billable != true)
        compose.onNode(hasText(context.getString(R.string.entry_billable)) and isToggleable()).performScrollTo().performClick()
        await { model.state.value.fields.billable }
        runBlocking { app.sender.sendAndWait() }
        assertEquals(true, serverTimer().runningEntry!!.billable)
        assertNotNull(serverTimer().pomodoro)
    }

    @Test
    fun withoutAConnectionStartKeepsTheFormAndSaysSo() {
        show()
        type(R.string.entry_description, "Offline start")
        AirplaneMode.set(true, fixture.account.client)
        tap(R.string.pomodoro_start)
        await { model.state.value.problem != null && !model.state.value.busy }
        assertEquals(PomodoroProblem(PomodoroAction.START, TimerActionResult.Offline), model.state.value.problem)
        compose.onNodeWithText(context.getString(TimerActionResult.Offline.message)).performScrollTo().assertIsDisplayed()
        assertNull(model.state.value.pomodoro)
        field(R.string.entry_description).performScrollTo().assert(hasText("Offline start"))
        button(R.string.pomodoro_start).performScrollTo().assertIsEnabled()
        AirplaneMode.set(false, fixture.account.client)
        assertNull("the failed start reached no server", serverTimer().pomodoro)
    }

    private fun serverTimer(): TimerState {
        val client = fixture.account.client
        return client.get(client.server.app("timer"), TimerState.serializer()).orFail("GET /api/app/timer")
    }

    private fun show() {
        compose.activityRule.scenario.onActivity { activity ->
            WindowCompat.enableEdgeToEdge(activity.window)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            model = ViewModelProvider(activity)[PomodoroViewModel::class.java].model
        }
        compose.setContent {
            TokklTheme {
                SignedInPanes(Destination.POMODORO, entryBeside = false, entryKey = 0) { destination ->
                    check(destination == Destination.POMODORO)
                    PomodoroScreen(model, app.cache)
                }
            }
        }
        await { !model.state.value.loading }
    }

    private fun await(condition: () -> Boolean) = compose.waitUntil(15_000, condition)
    private fun field(label: Int) = compose.onNode(hasText(context.getString(label)) and hasSetTextAction())
    private fun button(label: Int) = compose.onNode(hasText(context.getString(label)) and hasClickAction())
    private fun type(label: Int, value: String) { field(label).performScrollTo().performTextReplacement(value) }

    /**
     * Taps the button once it has kept still, fully visible, for [STILL_MS]. Typing opens the
     * keyboard, and while it slides in the page shrinks above it frame by frame, so a tap aimed at
     * where the button was lands beside it. If the keyboard covers the button after the scroll,
     * scroll again: a clipped button can keep still too, but a tap on it reaches the keyboard.
     */
    private fun tap(label: Int) {
        val node = button(label).performScrollTo()
        var bounds: Rect? = null
        var since = SystemClock.uptimeMillis()
        compose.waitUntil(10_000) {
            val semantics = node.fetchSemanticsNode()
            val now = semantics.boundsInRoot
            val fullyVisible = now.width > 0 && now.height > 0 &&
                now.width >= semantics.size.width && now.height >= semantics.size.height
            if (!fullyVisible) {
                node.performScrollTo()
                bounds = null
                since = SystemClock.uptimeMillis()
            } else if (now != bounds) {
                bounds = now
                since = SystemClock.uptimeMillis()
            }
            fullyVisible && SystemClock.uptimeMillis() - since >= STILL_MS
        }
        node.performClick()
    }

    private companion object {
        const val STILL_MS = 500L
    }
}
