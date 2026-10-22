package com.github.e0ipso.tokkl.signin

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Checks, at the device's font scale, that every field of the sign-in form keeps its height and
 * its place when its error shows and when the error clears, for both sign-in methods. Each
 * field's supporting line reserves the room of the tallest of its hint and its errors, so an
 * error with more or fewer lines than its hint moves nothing below it.
 *
 * Places count from the server URL field's top, so a scroll or the keyboard moving the whole form
 * does not count. Each is the difference of two positions, which loses float precision, so they
 * match to within half a dp.
 */
internal fun ComposeTestRule.assertFieldErrorsLeaveTheFormInPlace() {
    for ((method, fields) in METHODS) {
        onNode(hasText(string(method)) and isSelectable()).performScrollTo().performClick()
        for ((label, _) in fields) field(label).performTextClearance()
        val before = layoutOf(fields)

        // Every field is empty, so each shows its error.
        onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
        waitUntil("the errors show", TIMEOUT_MS) { shows(R.string.sign_in_error_server_url) }
        val withErrors = layoutOf(fields)

        for ((label, value) in fields) field(label).performTextInput(value)
        waitUntil("the errors clear", TIMEOUT_MS) { !shows(R.string.sign_in_error_server_url) }
        val cleared = layoutOf(fields)

        for ((index, label) in fields.map { it.first }.withIndex()) {
            val name = "${string(label)} with ${string(method)}"
            assertEquals("$name changed height when its error showed", before[index].height.value, withErrors[index].height.value, SLACK_DP)
            assertEquals("$name moved when the errors showed", before[index].top.value, withErrors[index].top.value, SLACK_DP)
            assertEquals("$name changed height when its error cleared", before[index].height.value, cleared[index].height.value, SLACK_DP)
            assertEquals("$name moved when the errors cleared", before[index].top.value, cleared[index].top.value, SLACK_DP)
        }
    }
}

/**
 * Checks, at the device's font scale, that the server URL error takes no more lines than the hint
 * it replaces, as TKA-0158 budgeted. A URL with a user name is the one refusal left, since a
 * query or a fragment is dropped.
 */
internal fun ComposeTestRule.assertServerUrlErrorFitsTheHint() {
    val hintLines = lineCount(R.string.sign_in_server_url_hint)
    field(R.string.sign_in_server_url).performTextClearance()
    field(R.string.sign_in_server_url).performTextInput("https://ana@tokkl.example.com")
    onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
    waitUntil("the server URL error shows", TIMEOUT_MS) { shows(R.string.sign_in_error_server_url) }

    val errorLines = lineCount(R.string.sign_in_error_server_url)
    assertTrue("the error takes $errorLines lines, the hint $hintLines", errorLines <= hintLines)
}

/** How many lines the text [id] takes on screen. */
private fun ComposeTestRule.lineCount(@StringRes id: Int): Int {
    val node = onNode(hasText(string(id)), useUnmergedTree = true).fetchSemanticsNode()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.single().lineCount
}

/** A field's height, and its top below the server URL field's. */
private class FieldLayout(val height: Dp, val top: Dp)

private fun ComposeTestRule.layoutOf(fields: List<Pair<Int, String>>): List<FieldLayout> {
    val nodes = fields.map { (label, _) -> field(label).fetchSemanticsNode() }
    return runOnIdle {
        // The keyboard can scroll the form between semantics queries. Read every
        // unclipped position in one main-thread task, using the same frame's origin.
        val serverTop = nodes.first().positionInRoot.y
        nodes.map { node ->
            with(node.layoutInfo.density) {
                FieldLayout(height = node.size.height.toDp(), top = (node.positionInRoot.y - serverTop).toDp())
            }
        }
    }
}

private fun ComposeTestRule.field(@StringRes label: Int) = onNode(hasSetTextAction() and hasText(string(label)))

private fun ComposeTestRule.shows(@StringRes text: Int): Boolean =
    onAllNodes(hasText(string(text)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

private fun string(@StringRes id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

/** Each method's fields, top to bottom, with a value that clears its error. */
private val METHODS = listOf(
    R.string.sign_in_method_api_token to listOf(
        R.string.sign_in_server_url to "https://tokkl.example.com",
        R.string.sign_in_api_token to "not-a-real-token",
    ),
    R.string.sign_in_method_email_password to listOf(
        R.string.sign_in_server_url to "https://tokkl.example.com",
        R.string.sign_in_email to "ana@example.com",
        R.string.sign_in_password to "not-a-real-password",
    ),
)

private const val TIMEOUT_MS = 5_000L
private const val SLACK_DP = 0.5f
