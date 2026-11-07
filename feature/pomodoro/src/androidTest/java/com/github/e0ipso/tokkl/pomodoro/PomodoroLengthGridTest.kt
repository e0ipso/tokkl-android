package com.github.e0ipso.tokkl.pomodoro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createComposeRule
import com.github.e0ipso.tokkl.core.shared.R
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

/**
 * The Pomodoro length form's grid: two columns once the form itself is 360dp wide and one below,
 * whatever the window is, and a Start button that follows the cards' drafts.
 */
class PomodoroLengthGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var lengths by mutableStateOf(PomodoroLengths.from(PomodoroDefaults.DEFAULT))
    private val labels = listOf(
        context.getString(R.string.pomodoro_card_work),
        context.getString(R.string.pomodoro_card_short_rest),
        context.getString(R.string.pomodoro_card_long_rest),
        context.getString(R.string.pomodoro_card_long_rest_every),
    )

    @Test
    fun theFormUsesTwoColumnsFromThreeHundredSixtyDpOfItsOwnWidthAndOneBelow() {
        var hostWidth by mutableStateOf(359.dp)
        composeRule.setContent {
            TokklTheme {
                Box(Modifier.width(hostWidth)) {
                    PomodoroLengthForm(lengths, enabled = true, onChange = { lengths = it })
                }
            }
        }
        for (width in listOf(359.dp, 360.dp, 400.dp, 359.dp)) {
            hostWidth = width
            composeRule.waitForIdle()
            val (work, shortRest, longRest, every) = labels.map {
                composeRule.lengthCard(
                    it,
                ).getUnclippedBoundsInRoot()
            }
            val at = "at $width"
            if (width < 360.dp) {
                // One column, in order, each card as wide as the form.
                assertEquals(
                    "left edges $at",
                    listOf(work.left),
                    listOf(shortRest.left, longRest.left, every.left).distinct(),
                )
                assertTrue(
                    "one above the next $at",
                    work.bottom <= shortRest.top && shortRest.bottom <= longRest.top && longRest.bottom <= every.top,
                )
                // The host rounds to whole pixels, so the width can be a pixel short of the dp.
                assertTrue("card width $at", (width - work.width).value < 1f)
            } else {
                // Two columns: Work and Short rest above Long rest and Long rest every.
                assertEquals("first row $at", work.top, shortRest.top)
                assertEquals("second row $at", longRest.top, every.top)
                assertTrue(
                    "first column left of the second $at",
                    work.right <= shortRest.left && longRest.right <= every.left,
                )
                assertEquals("columns line up $at", work.left, longRest.left)
                assertTrue("second row below the first $at", longRest.top >= work.bottom)
                assertEquals("row one height $at", work.height, shortRest.height)
                assertEquals("row two height $at", longRest.height, every.height)
            }
        }
    }

    @Test
    fun startStaysDisabledWhileACardHoldsAnInvalidLength() {
        val account = AccountSettings("europe/madrid", DayOfWeek.MONDAY, null, defaultWorkspaceId = 1)
        var page by mutableStateOf(PomodoroPageState(loading = false, account = account))
        composeRule.setContent {
            TokklTheme {
                PomodoroContent(
                    page,
                    onLengths = { page = page.copy(lengths = it) },
                    onFields = {},
                    onDescription = {},
                    onDescriptionDone = {},
                    onStart = {},
                    onNext = {},
                    onEnd = {},
                )
            }
        }
        val start = composeRule.onNode(hasText(context.getString(R.string.pomodoro_start)) and hasClickAction())
        start.performScrollTo().assertIsEnabled()

        for ((index, invalid) in listOf("181", "", "0", "13").withIndex()) {
            val valid = page.lengths.asList()[index]
            type(labels[index], invalid)
            assertEquals("the draft reaches the lengths as typed", invalid, page.lengths.asList()[index])
            start.performScrollTo().assertIsNotEnabled()
            type(labels[index], valid)
            start.performScrollTo().assertIsEnabled()
        }
    }

    private fun type(label: String, value: String) {
        composeRule.lengthCard(label).performScrollTo()
        composeRule.lengthValue(label).performTextReplacement(value)
        composeRule.waitForIdle()
    }

    private fun PomodoroLengths.asList() = listOf(work, shortRest, longRest, every)
}
