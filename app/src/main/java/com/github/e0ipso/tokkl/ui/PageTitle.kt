package com.github.e0ipso.tokkl.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * A screen's title in the page-title role, 28sp Anybody 900 in capitals, as Tokkl's web sets
 * them. The capitals are drawn, not read: TalkBack gets [text] as given, in sentence case, as a
 * heading. Keep the string resource in sentence case.
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = TokklTheme.pageTitleTextStyle,
        // This modifier sits outside Text's own semantics, so its text replaces the capitals.
        modifier = modifier.semantics {
            this.text = AnnotatedString(text)
            heading()
        },
    )
}
