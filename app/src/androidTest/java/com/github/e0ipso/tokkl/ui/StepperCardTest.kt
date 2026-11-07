package com.github.e0ipso.tokkl.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasRequestFocusAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.core.shared.R
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createComposeRule
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The stepper card on its own, with Pomodoro's work and long-rest-every ranges: typing keeps any
 * draft, the buttons and TalkBack's actions step by one and stop at the range's ends, a draft
 * out of range or with no number names its limit, TalkBack reads the card as one node that still
 * takes text, and a disabled card takes nothing.
 */
class StepperCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }

    private var draft by mutableStateOf("")
    private var enabled by mutableStateOf(true)
    private var field by mutableStateOf(WORK)

    /** The class and flags of each input method the card started, from its EditorInfo. */
    private val inputTypes = CopyOnWriteArrayList<Int>()
    private lateinit var colors: ColorScheme

    @Test
    fun aTapOpensNumberTypingThatKeepsEveryDraftAsTyped() {
        show("25")
        value().performTouchInput { click() }
        card().assertIsFocused()
        assertNumberKeyboardOpened()

        // Nothing clamps or rewrites a draft while it is typed, not even an empty or absurd one.
        for (typed in listOf("", "0", "240", "99999999999", "7")) {
            value().performTextClearance()
            assertDraft("")
            if (typed.isNotEmpty()) value().performTextInput(typed)
            assertDraft(typed)
        }
    }

    @Test
    fun buttonsStepByOneAndStopAtBothEndsOfTheRange() {
        show("25")
        press(STEPPER_INCREASE_TAG)
        assertDraft("26")
        press(STEPPER_DECREASE_TAG)
        assertDraft("25")

        for (each in listOf(WORK, EVERY)) {
            field = each
            val first = each.range.first
            val last = each.range.last
            draft = "${last - 1}"
            press(STEPPER_INCREASE_TAG)
            assertDraft("$last")
            press(STEPPER_INCREASE_TAG)
            assertDraft("$last")
            draft = "${first + 1}"
            press(STEPPER_DECREASE_TAG)
            assertDraft("$first")
            press(STEPPER_DECREASE_TAG)
            assertDraft("$first")
        }
    }

    @Test
    fun aStepFromAnEmptyOrOversizedDraftLandsInTheRange() {
        show("")
        val steps = listOf(
            Triple("", STEPPER_INCREASE_TAG, "1"),
            Triple("", STEPPER_DECREASE_TAG, "1"),
            Triple("99999999999", STEPPER_INCREASE_TAG, "180"),
            Triple("99999999999", STEPPER_DECREASE_TAG, "180"),
            // Too long for a Long.
            Triple("99999999999999999999999", STEPPER_DECREASE_TAG, "180"),
            Triple("-99999999999999999999999", STEPPER_INCREASE_TAG, "1"),
            Triple("0", STEPPER_INCREASE_TAG, "1"),
            Triple("-5", STEPPER_DECREASE_TAG, "1"),
            Triple("2x", STEPPER_INCREASE_TAG, "1"),
        )
        for ((start, button, expected) in steps) {
            draft = start
            press(button)
            assertDraft(expected)
        }
    }

    @Test
    fun talkBackActionsStepAndClampLikeTheButtons() {
        show("25")
        act("Increase")
        assertDraft("26")
        act("Decrease")
        assertDraft("25")

        val steps = listOf(
            Triple("180", "Increase", "180"),
            Triple("1", "Decrease", "1"),
            Triple("99999999999", "Decrease", "180"),
            Triple("", "Increase", "1"),
        )
        for ((start, action, expected) in steps) {
            draft = start
            act(action)
            assertDraft(expected)
        }
    }

    @Test
    fun talkBackReadsOneNodeThatStillTakesText() {
        show("25")
        composeRule.onAllNodes(hasContentDescription("Work, 25 minutes, 1 to 180")).assertCountEquals(1)
        card().assertContentDescriptionEquals("Work, 25 minutes, 1 to 180")
        // The number and both buttons are not stops of their own.
        card().onChildren().assertCountEquals(0)
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        card().assert(hasClickAction()).assert(hasRequestFocusAction()).assert(hasSetTextAction())

        // TalkBack's double tap opens number typing on the field.
        card().performSemanticsAction(SemanticsActions.OnClick)
        card().assertIsFocused()
        assertNumberKeyboardOpened()
        // A service that sets the text, such as Voice Access, changes the draft as typing does.
        card().performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("42")) }
        assertDraft("42")
        card().assertContentDescriptionEquals("Work, 42 minutes, 1 to 180")

        field = EVERY
        draft = "1"
        card().assertContentDescriptionEquals("Long rest every, 1 work phase, 1 to 12")
        composeRule.onNodeWithText("work phase · 1–12", useUnmergedTree = true).assertIsDisplayed()
        draft = "4"
        card().assertContentDescriptionEquals("Long rest every, 4 work phases, 1 to 12")
        composeRule.onNodeWithText("work phases · 1–12", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aDraftOutOfRangeOrWithNoNumberNamesItsLimit() {
        show("25")
        composeRule.onNodeWithText("min · 1–180", useUnmergedTree = true).assertIsDisplayed()
        assertFrame(colors.outline)

        val drafts = listOf(
            Triple("", "enter 1–180", "Work, empty, enter 1 to 180"),
            Triple("2x", "enter 1–180", "Work, not a whole number, enter 1 to 180"),
            Triple("240", "max 180", "Work, 240 minutes, over the maximum of 180"),
            Triple("99999999999999999999999", "max 180", "Work, 99999999999999999999999 minutes, over the maximum of 180"),
            Triple("0", "min 1", "Work, 0 minutes, under the minimum of 1"),
        )
        for ((typed, line, spoken) in drafts) {
            draft = typed
            composeRule.onNodeWithText(line, useUnmergedTree = true).assertIsDisplayed()
            card().assertContentDescriptionEquals(spoken)
            // The draft stays as typed, in the danger colour's frame.
            value().assertTextEquals(typed)
            assertFrame(colors.error)
        }

        field = EVERY
        draft = "13"
        composeRule.onNodeWithText("max 12", useUnmergedTree = true).assertIsDisplayed()
        card().assertContentDescriptionEquals("Long rest every, 13 work phases, over the maximum of 12")
    }

    @Test
    fun aDisabledCardRefusesTypingButtonsAndActions() {
        enabled = false
        show("25")
        card().assertIsNotEnabled()
            .assertContentDescriptionEquals("Work, 25 minutes, 1 to 180")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        value().assertIsNotEnabled().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))

        value().performTouchInput { click() }
        press(STEPPER_INCREASE_TAG)
        press(STEPPER_DECREASE_TAG)
        assertDraft("25")
        assertTrue("a disabled card started typing: $inputTypes", inputTypes.isEmpty())

        // Enabled again, the same card takes the actions back.
        enabled = true
        card().assertIsEnabled()
        act("Increase")
        assertDraft("26")
    }

    private fun show(value: String) {
        draft = value
        composeRule.setContent {
            TokklTheme {
                colors = MaterialTheme.colorScheme
                // Reads the EditorInfo each input method would get, then starts it as usual.
                InterceptPlatformTextInput({ request, next ->
                    val info = EditorInfo()
                    request.createInputConnection(info)
                    inputTypes += info.inputType
                    next.startInputMethod(request)
                }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(TokklTheme.spacing.space4)) {
                        StepperCard(
                            label = stringResource(field.label),
                            value = draft,
                            range = field.range,
                            unit = field.unit,
                            onChange = { draft = it },
                            enabled = enabled,
                            modifier = Modifier.width(CARD_WIDTH).testTag(CARD),
                        )
                    }
                }
            }
        }
    }

    private fun card(): SemanticsNodeInteraction = composeRule.onNodeWithTag(CARD)

    private fun value(): SemanticsNodeInteraction = composeRule.onNodeWithTag(STEPPER_VALUE_TAG, useUnmergedTree = true)

    /** A real tap on a button, which has no semantics of its own to click. */
    private fun press(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
    }

    /** The card's TalkBack action named [label]. */
    @OptIn(ExperimentalTestApi::class)
    private fun act(label: String) {
        card().performCustomAccessibilityActionWithLabel(label)
    }

    private fun assertDraft(expected: String) {
        composeRule.waitForIdle()
        assertEquals("the caller's draft", expected, draft)
        value().assertTextEquals(expected)
    }

    private fun assertNumberKeyboardOpened() {
        composeRule.waitUntil(INPUT_TIMEOUT_MS) { inputTypes.isNotEmpty() }
        assertEquals("input class", InputType.TYPE_CLASS_NUMBER, inputTypes.last() and InputType.TYPE_MASK_CLASS)
    }

    /** The card's frame, sampled 1dp in from the middle of its left edge. */
    private fun assertFrame(expected: Color) {
        composeRule.waitForIdle()
        val pixels = card().captureToImage().toPixelMap()
        val density = composeRule.density.density
        val actual = pixels[(1 * density).roundToInt(), pixels.height / 2]
        val near = listOf(actual.red to expected.red, actual.green to expected.green, actual.blue to expected.blue)
            .all { (a, b) -> abs(a - b) * 255 <= 2 }
        assertTrue("frame is $actual, expected $expected", near)
    }

    private data class Field(@StringRes val label: Int, val range: IntRange, val unit: StepperUnit)

    private companion object {
        val WORK = Field(R.string.pomodoro_card_work, PomodoroDefaults.WORK_MINUTES, StepperUnit.MINUTES)
        val EVERY = Field(R.string.pomodoro_card_long_rest_every, PomodoroDefaults.LONG_REST_EVERY, StepperUnit.WORK_PHASES)

        /** A card in the narrower column of the two-column Pomodoro grid at 360dp. */
        val CARD_WIDTH = 176.dp
        const val CARD = "card"
        const val INPUT_TIMEOUT_MS = 5_000L
    }
}
