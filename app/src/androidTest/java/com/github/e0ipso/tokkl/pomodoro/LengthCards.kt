package com.github.e0ipso.tokkl.pomodoro

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.github.e0ipso.tokkl.ui.STEPPER_VALUE_TAG

// The Pomodoro length form's stepper cards, which the tests find by the short label that starts
// the card's spoken description, such as "Work, 25 minutes, 1 to 180". "Long rest" and
// "Long rest every" are told apart by the comma.

/** The card for [label] as TalkBack sees it, one node that takes text. */
internal fun lengthCardMatcher(label: String): SemanticsMatcher =
    SemanticsMatcher("length card \"$label\"") { node ->
        SemanticsActions.SetText in node.config &&
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.startsWith("$label, ") == true
    }

internal fun SemanticsNodeInteractionsProvider.lengthCard(label: String): SemanticsNodeInteraction =
    onNode(lengthCardMatcher(label))

/** The number's text field inside the card for [label], which only the unmerged tree has. */
internal fun SemanticsNodeInteractionsProvider.lengthValue(label: String): SemanticsNodeInteraction =
    onNode(hasTestTag(STEPPER_VALUE_TAG) and hasAnyAncestor(lengthCardMatcher(label)), useUnmergedTree = true)

/** The visible label text inside the card for [label]. */
internal fun SemanticsNodeInteractionsProvider.lengthLabel(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and hasAnyAncestor(lengthCardMatcher(label)), useUnmergedTree = true)
